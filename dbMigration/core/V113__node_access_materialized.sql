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

-- table to store invalidated nodes via changes
create table node_can_access_invalid(
  node_id uuid not null references node on delete cascade
);
create unique index on node_can_access_invalid (node_id);

-- materialized table to cache granted access for user on node (valid = false means it needs to be recalculated)
create table node_can_access_mat(
  node_id uuid not null references node on delete cascade,
  user_id uuid not null references node on delete cascade
);
create unique index on node_can_access_mat (node_id, user_id);
create index on node_can_access_mat (user_id);

-- materialized table to cache public nodes including inheritance (valid = false means it needs to be recalculated)
create table node_can_access_public_mat(
  node_id uuid not null references node on delete cascade
);
create index on node_can_access_public_mat (node_id);

-- trigger for node update
create function node_update() returns trigger
  security definer
  language plpgsql
as $$
  begin
    IF (new.accesslevel <> old.accesslevel) THEN
        insert into node_can_access_invalid select unnest(node_can_access_deep_children(new.id)) on conflict do nothing;
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
        insert into node_can_access_invalid select unnest(node_can_access_deep_children(new.targetid)) on conflict do nothing;
    ELSIF(new.data->>'type' = 'Member') THEN
        --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
        insert into node_can_access_invalid select unnest(node_can_access_deep_children(new.sourceid)) on conflict do nothing;
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
            insert into node_can_access_invalid select unnest(node_can_access_deep_children(new.targetid)) on conflict do nothing;
        ELSIF(new.data->>'type' = 'Member') THEN
            --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
            insert into node_can_access_invalid select unnest(node_can_access_deep_children(new.sourceid)) on conflict do nothing;
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
        insert into node_can_access_invalid select unnest(node_can_access_deep_children(new.targetid)) on conflict do nothing;
    ELSIF(new.data->>'type' = 'Member') THEN
        --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
        insert into node_can_access_invalid select unnest(node_can_access_deep_children(new.sourceid)) on conflict do nothing;
    end IF;
    return new;
  end;
$$;
create trigger edge_delete_trigger before delete on edge for each row execute procedure edge_delete();

-- deep access children of node
create function node_can_access_deep_children(node_id uuid)
  returns table(id uuid)
  security definer
  language sql
as $$
    with recursive
        content(id) AS (
            select id from node where id = node_id
            union -- discards duplicates, therefore handles cycles and diamond cases
            select accessedge.target_nodeid FROM content INNER JOIN accessedge ON accessedge.source_nodeid = content.id
        )
        -- all transitive children
        select id from content;
$$;

CREATE TYPE node_can_access_public_result AS ENUM ('unknown', 'private', 'public');

create function node_can_access_public_agg_fun(accum node_can_access_public_result, curr node_can_access_public_result) returns node_can_access_public_result AS $$
    select (
        CASE
        WHEN accum = 'public' THEN 'public'
        WHEN curr = 'public' THEN 'public'
        WHEN accum = 'unknown' THEN 'unknown'
        ELSE curr
        END
    );
$$ LANGUAGE sql strict;
create aggregate node_can_access_public_agg(node_can_access_public_result)
(
    INITCOND = 'private',
    STYPE = node_can_access_public_result,
    SFUNC = node_can_access_public_agg_fun
);

-- recursively check whether a node is public. will use cache if valid and otherwise with side-effect of filling the cache.
-- returns true if public and false if not
create function node_can_access_public_recursive(nodeid uuid, visited uuid[]) returns node_can_access_public_result as $$
declare
    node_access_level accesslevel;
    public_result node_can_access_public_result;
    is_public boolean;
    is_invalid boolean;
begin
    IF ( nodeid = any(visited) ) THEN return 'unknown'; end if; -- prevent inheritance cycles

    is_public := false;

    select exists(select * from node_can_access_invalid where node_id = nodeid) into is_invalid;
    IF ( is_invalid = false ) THEN
        is_public := (select exists(select * from node_can_access_public_mat where node_id = nodeid));
        if (is_public = true) then
            return 'public';
        else
            return 'private';
        end if;
    end if;

    -- read node access level to decide for recurison
    select accessLevel into node_access_level from node where id = nodeid limit 1;

    -- if node access level is public, return true
    IF (node_access_level = 'readwrite') THEN
        is_public := true;
    ELSIF (node_access_level IS NULL) THEN-- null means inherit for the node
        -- recursively inherit permissions from parents. minimum one parent needs to allowed access.
        public_result := (select node_can_access_public_agg(node_can_access_reursive(accessedge.source_nodeid, visited || nodeid)) from accessedge where accessedge.target_nodeid = nodeid);
        if (access_result = 'unknown' and cardinality(visited) > 0) then return 'unknown'; end if;
        if (public_result = 'public') then is_public := true; end if;
    END IF;

    delete from node_can_access_public_mat where node_id = nodeid;
    delete from node_can_access_invalid_node where node_id = nodeid;
    if (is_public = true) then
        insert into node_can_access_public_mat VALUES(node_id);
        return 'public';
    else
        return 'private';
    end if;
end;
$$ language plpgsql strict;

CREATE TYPE node_can_access_result AS (
    known boolean,
    user_ids uuid[]
);

create function node_can_access_agg_fun(accum node_can_access_result, curr node_can_access_result) returns node_can_access_result AS $$
    select false, accum.user_ids || curr.user_ids where not accum.known or not curr.known
    UNION ALL
    select true, accum.user_ids || curr.user_ids where accum.known and curr.known
$$ LANGUAGE sql strict;
create aggregate node_can_access_agg(node_can_access_result)
(
    INITCOND = '(true,{})',
    STYPE = node_can_access_result,
    SFUNC = node_can_access_agg_fun
);

-- recursively check whether a node is accessible. will use cache if valid and otherwise with side-effect of filling the cache.
-- returns the user-ids that are allowed to access this node
create function node_can_access_recursive(nodeid uuid, visited uuid[]) returns node_can_access_result as $$
declare
    node_access_level accesslevel;
    user_ids uuid[];
    is_known boolean;
    result node_can_access_result;
    is_invalid boolean;
begin
    IF ( nodeid = any(visited) ) THEN return row(false, array[]::uuid[]); end if; -- prevent inheritance cycles

    is_known := true;

    select exists(select * from node_can_access_invalid where node_id = nodeid) into is_invalid;
    IF ( is_invalid = false ) THEN
        user_ids := (select array_agg(user_id) from node_can_access_mat where node_id = nodeid);
        return row(true, user_ids);
    end if;

    -- collect all user_ids that have granted access for this node
    select array_agg(member.target_userid) into user_ids from member where data->>'level' = 'readwrite' and member.source_nodeid = nodeid;

    -- read node access level to decide for recurison
    select accessLevel into node_access_level from node where id = nodeid limit 1;

    -- if node access level is inherited or public, check above, else not grant access.
    IF (node_access_level IS NULL or node_access_level = 'readwrite') THEN -- null means inherit for the node, readwrite/public inherits as well
        -- recursively inherit permissions from parents. minimum one parent needs to allowed access.
        result := (select node_can_access_agg(node_can_access_reursive(accessedge.source_nodeid, visited || nodeid)) from accessedge where accessedge.target_nodeid = nodeid);
        if (not result.known and cardinality(visited) > 0) then is_known := false end if;
        user_ids := user_ids || node_can_access_result.user_ids;
    END IF;

    delete from node_can_access_mat where node_id = nodeid;
    delete from node_can_access_invalid_node where node_id = nodeid;
    insert into node_can_access_mat select node_id, user_id from unnest(user_ids) as user_id;
    return row(is_known, user_ids);
end;
$$ language plpgsql strict;

create function node_can_access(nodeid uuid, userid uuid) returns boolean as $$
begin
    IF NOT EXISTS (select 1 from node where id = nodeid) then return true; end if; -- everybody has full access to non-existant nodes
    node_can_access_recursive(nodeid, array[]::uuid[]) = 'allowed';
end;
$$ language plpgsql strict;

create function node_can_access_public(nodeid uuid) returns boolean as $$
begin
    return node_can_access_public_recursive(nodeid, array[]::uuid[]) = 'public';
end;
$$ language plpgsql strict;
