
# Try out CTE for access rights

```sql
create or replace function allowed_users_for_node_recursive(nodeid uuid) returns table(user_id uuid) as $$
    with recursive
        transitive_access_parents(id) AS (
            select id from node where id = nodeid
            union
            select accessedge.target_nodeid
                from transitive_access_parents
                inner join node_can_access_invalid
                on transitive_access_parents.id = node_can_access_invalid.node_id
                inner join node
                on node.id = transitive_access_parents.id and (node.accesslevel is NULL or node.accesslevel = 'readwrite')
                inner join accessedge
                on accessedge.source_nodeid = transitive_access_parents.id
        )
        select target_userid
        from transitive_access_parents
        inner join node_can_access_invalid
        on transitive_access_parents.id = node_can_access_invalid.node_id
        inner join member
        on transitive_access_parents.id = member.source_nodeid and member.data->>'level' = 'readwrite'
        union
        select user_id
        from transitive_access_parents
        inner join node_can_access_mat
        on transitive_access_parents.id = node_can_access_mat.node_id and not exists(select 1 from node_can_access_invalid where node_can_access_invalid.node_id = transitive_access_parents.id)
$$ language sql strict;

drop function allowed_users_for_node_refresh;
create view allowed_users_for_node_refresh(nodeid uuid) returns table(user_id uuid) as $$
    with allowed_users(user_id) AS (
        select allowed_users_for_node_recursive(nodeid) as user_id
    ), delete_invalid AS (
        delete from node_can_access_invalid where node_id = nodeid
    ), delete_outdated AS (
        delete from node_can_access_mat
        where node_id = nodeid
        and not exists(select 1 from allowed_users where node_can_access_mat.user_id = allowed_users.user_id)
    )
    insert into node_can_access_mat select nodeid, user_id from allowed_users on conflict do nothing returning user_id;
$$ language sql strict;

create or replace function allowed_users_for_node(nodeid uuid) returns table(user_id uuid) as $$
    select user_id
    from node_can_access_mat
    where node_id = nodeid and not exists(select 1 from node_can_access_invalid where node_can_access_invalid.node_id = nodeid)
    UNION
    select user_id
    from allowed_users_for_node_refresh(nodeid)
    where exists(select 1 from node_can_access_invalid where node_can_access_invalid.node_id = nodeid)
$$ language sql strict;

create or replace function node_can_access(nodeid uuid, userid uuid) returns boolean as $$
begin
    return
    exists(
        select 1 from node_can_access_mat
        where node_id = nodeid and user_id = userid
        and not exists(select 1 from node_can_access_invalid where node_id = nodeid)
    )
    or not exists (
        select 1 from node
        where id = nodeid
    )
    or exists(
        select 1 from allowed_users_for_node(nodeid) where user_id = userid
    );
end;
$$ language plpgsql strict;
```
