-- CAN ACCESS CACHING
-- need to react to every way the access to a node for a user could have changed.
-- then need to update node_can_access_mat and node_can_access_public_mat table.
-- How can access change:
-- 0) add/delete node/user already handled by foreign key with cascade. edges cascaed as well when deleting nodes, so changes in children/targets will be handled in edge triggers.
-- 1) update node if permission has changed, because self and deep children might have different access now. if restricted then permission are stricter now, need to invalidate all child node access and recalculate. if inherited or public permission, access could be expanded, just need to recalculate (inherited and public behave the same, only public nodes can be accessed via url).
-- 2) insert child/labelledproperty, then deep children/targets might have different access now if permission is public or inherit. no need to invalidate, can only have more access
-- 3) delete child/labelledproperty. if inherited or public permission, access could be stricter now. Need to invalidate deep children/targets and recalculate.
-- 4) insert member. need to recalute that specific user in deep children/targets.
-- 5) delete member. need to invalidate and recalute that specific user in deep children/targets.

create table node_can_access_invalid(
  node_id uuid references node not null on delete cascade,
);
create unique index on node_can_access_mat (node_id);

-- materialized table to cache granted access for user on node (valid = false means it needs to be recalculated)
create table node_can_access_mat(
  node_id uuid references node not null on delete cascade,
  user_id uuid references node not null on delete cascade,
);
create unique index on node_can_access_mat (node_id, user_id);
create index on node_can_access_mat (user_id);

-- materialized table to cache public nodes including inheritance (valid = false means it needs to be recalculated)
create table node_can_access_public_mat(
  node_id uuid references node not null on delete cascade,
);
create index on node_can_access_public_mat (node_id);
create index on node_can_access_public_mat (valid);

-- trigger for node update
create function node_update() returns trigger
  security definer
  language plpgsql
as $$
  begin
    IF (new.accesslevel <> old.accesslevel) THEN
        --TODO: invalidate children
        insert into node_can_access_invalid values(new.id);
    end if;
    return new;
  end;
$$;
create trigger node_update_trigger before update on node for each row execute procedure node_update();

-- trigger edge insert
create function edge_insert() returns trigger
  security definer
  language plpgsql
as $$
  begin
    IF (new.data->>'type' = 'Child' or new.data->>'type' = 'LabeledProperty') THEN
        insert into node_can_access_invalid values(new.targetid);
    ELSIF(new.data->>'type' = 'Member') THEN
        --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
        insert into node_can_access_invalid values(new.sourceid);
    end IF;
    return new;
  end;
$$;
create trigger edge_insert_trigger before insert on edge for each row execute procedure edge_insert();

-- trigger edge update
create function edge_update() returns trigger
  security definer
  language plpgsql
as $$
  begin
    IF (new.sourceid <> old.sourceid or new.targetid <> old.targetid or new.data->>'type' <> old.data->>'type') THEN
        IF (new.data->>'type' = 'Child' or new.data->>'type' = 'LabeledProperty') THEN
            insert into node_can_access_invalid values(new.targetid);
        ELSIF(new.data->>'type' = 'Member') THEN
            --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
            insert into node_can_access_invalid values(new.sourceid);
        end IF;
    end IF;
    return new;
  end;
$$;
create trigger edge_update_trigger before insert on edge for each row execute procedure edge_update();

-- trigger for edge delete
create function edge_delete() returns trigger
  security definer
  language plpgsql
as $$
  begin
    IF (new.data->>'type' = 'Child' or new.data->>'type' = 'LabeledProperty') THEN
        --TODO: invalidate children...
        insert into node_can_access_invalid values(new.targetid);
    ELSIF(new.data->>'type' = 'Member') THEN
        --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
        insert into node_can_access_invalid values(new.sourceid);
    end IF;
    return new;
  end;
$$;

create trigger edge_delete_trigger before delete on edge for each row execute procedure edge_delete();

-- recalculate invalid nodes can_access_node_mat
create function refresh_node_can_access(id uuid)
  returns lazy.account_balances_mat
  security definer
  language sql
as $$
  with t as (
    select
      coalesce(
        sum(amount) filter (where post_time <= current_timestamp),
        0
      ) as balance,
      coalesce(
        min(post_time) filter (where current_timestamp < post_time),
        'Infinity'
      ) as expiration_time
    from transactions
    where name=_name
  )
  update lazy.account_balances_mat
  set balance = t.balance, expiration_time = t.expiration_time
  from t
  where name = id
  returning node_can_access_mat.*;
$$;

-- recursively check whether a node is accessible. with side-effect of filling the cache.
-- non-existing user: we assume that a user exist in any cases and therefore we do not handle this explicitly
create function node_can_access_reursive(userid uuid, nodeid uuid, visited uuid[], get_via_url_mode boolean) returns can_access_result as $$
declare
    member_access_level accesslevel;
    node_access_level accesslevel;
    result boolean;
    access_result can_access_result;
begin
    select exists(select can_access from node_can_access_mat where node_id = nodeid and user_id = userid and valid limit 1) into result;
    IF ( result = true ) THEN return 'allowed'; end if;
    IF ( result = false ) THEN return 'forbidden'; end if;
    IF ( nodeid = any(visited) ) THEN return 'unknown'; end if; -- prevent inheritance cycles

    -- is there a membership?
    select data->>'level' into member_access_level from member where member.target_userid = userid and member.source_nodeid = nodeid limit 1;
    IF (member_access_level IS NOT NULL) THEN -- if member edge exists
        -- either the use is granted readwrite, then he can access. otherwise not.
        result := member_access_level = 'readwrite';
        IF ( result = true ) THEN
            insert into node_can_access_mat VALUES (nodeid, result) on conflict (node_id, user_id) do update set valid = true;
            return 'allowed';
        ELSE
            delete from node_can_access_mat where node_id = nodeid and user_id = userid;
            return 'forbidden';
        end if;
    END IF;

    -- if no member edge exists, read access level directly from node
    select accessLevel into node_access_level from node where id = nodeid limit 1;

    -- get_via_url_mode allows access to public nodes, you can become a member of these nodes.
    -- IF (get_via_url_mode and node_access_level = 'readwrite') THEN
        -- insert into node_can_access_mat VALUES (nodeid, result) on conflict (node_id, user_id) do update set valid = true;
        -- return 'allowed';
    -- END IF;

    -- if node access level is inherited or public, check above, else not grant access.
    IF (node_access_level IS NULL or node_access_level = 'readwrite') THEN -- null means inherit for the node
        -- recursively inherit permissions from parents. minimum one parent needs to allowed access.
        select can_access_agg(node_can_access_reursive(userid, accessedge.source_nodeid, visited || nodeid, get_via_url_mode)) into access_result from accessedge where accessedge.target_nodeid = nodeid;
        -- return unknown if we cannot get a recursive answer, because of a cycle
        -- if this is because of visited, then we cannot know whether this node is accessible
        -- but if visited is empty, we know for sure that this node is inaccessible.
        if access_result = 'unknown' and cardinality(visited) > 0 then return 'unknown'; end if;
        result := access_result = 'allowed';
        insert into node_can_access_mat VALUES (nodeid, result) on conflict (node_id, user_id) do update set valid = true;
        IF ( result = true ) THEN
            insert into node_can_access_mat VALUES (nodeid, result) on conflict (node_id, user_id) do update set valid = true;
            return 'allowed';
        ELSE
            delete from node_can_access_mat where node_id = nodeid and user_id = userid;
            return 'forbidden';
        end if;
    END IF;

    delete from node_can_access_mat where node_id = nodeid and user_id = userid;
    return 'forbidden';
end;
$$ language plpgsql strict;

-- recursively check whether a node is accessible. with side-effect of filling the cache.
-- non-existing user: we assume that a user exist in any cases and therefore we do not handle this explicitly
create function node_can_access_reursive(userid uuid, nodeid uuid, visited uuid[], get_via_url_mode boolean) returns can_access_result as $$
declare
    member_access_level accesslevel;
    node_access_level accesslevel;
    result boolean;
    access_result can_access_result;
begin
    select exists(select can_access from node_can_access_mat where node_id = nodeid and user_id = userid and valid limit 1) into result;
    IF ( result = true ) THEN return 'allowed'; end if;
    IF ( result = false ) THEN return 'forbidden'; end if;
    IF ( nodeid = any(visited) ) THEN return 'unknown'; end if; -- prevent inheritance cycles

    -- is there a membership?
    select data->>'level' into member_access_level from member where member.target_userid = userid and member.source_nodeid = nodeid limit 1;
    IF (member_access_level IS NOT NULL) THEN -- if member edge exists
        -- either the use is granted readwrite, then he can access. otherwise not.
        result := member_access_level = 'readwrite';
        IF ( result = true ) THEN
            insert into node_can_access_mat VALUES (nodeid, result);
            return 'allowed';
        ELSE
            delete from node_can_access_mat where node_id = nodeid and user_id = userid;
            return 'forbidden';
        end if;
    END IF;

    -- if no member edge exists, read access level directly from node
    select accessLevel into node_access_level from node where id = nodeid limit 1;

    -- get_via_url_mode allows access to public nodes, you can become a member of these nodes.
    IF (get_via_url_mode and node_access_level = 'readwrite') THEN return 'allowed'; END IF;

    -- if node access level is inherited or public, check above, else not grant access.
    IF (node_access_level IS NULL or node_access_level = 'readwrite') THEN -- null means inherit for the node
        -- recursively inherit permissions from parents. minimum one parent needs to allowed access.
        select can_access_agg(node_can_access_reursive(userid, accessedge.source_nodeid, visited || nodeid, get_via_url_mode)) into access_result from accessedge where accessedge.target_nodeid = nodeid;
        -- return unknown if we cannot get a recursive answer, because of a cycle
        -- if this is because of visited, then we cannot know whether this node is accessible
        -- but if visited is empty, we know for sure that this node is inaccessible.
        if access_result = 'unknown' and cardinality(visited) > 0 then return 'unknown'; end if;
        result := access_result = 'allowed';
        insert into node_can_access_mat VALUES (nodeid, userid, result);
        IF ( result = true ) THEN return 'allowed'; end if;
        return 'forbidden';
    END IF;

    delete from node_can_access_mat where node_id = nodeid and user_id = userid;
    return 'forbidden';
end;
$$ language plpgsql strict;
