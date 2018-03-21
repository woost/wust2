create or replace function public.array_merge(arr1 anyarray, arr2 anyarray)
    returns anyarray language sql immutable
as $$
    -- select array_agg(distinct elem order by elem)
    select array_agg(distinct elem)
    from (
        select unnest(arr1) elem 
        union
        select unnest(arr2)
    ) s
$$;

drop aggregate if exists array_merge_agg(anyarray);
create aggregate array_merge_agg(anyarray) (
    sfunc = array_merge,
    stype = anyarray
);

drop function if exists notified_users(start varchar(36)[]);
create or replace function notified_users(start varchar(36)[]) returns table(userid varchar(36), postids varchar(36)[]) as $func$
declare
    queue varchar(36)[] := start;
    nextqueue varchar(36)[] := '{}';
    parentlabel int := 2; --(select id from label where name = 'parent');
    -- items record;
    begin
        create temporary table visited (id varchar(36) NOT NULL, reason varchar(36)) on commit drop;
        create unique index on visited (id,reason);

        create temporary table reasons (id varchar(36) NOT NULL, reasons varchar(36)[] NOT NULL) on commit drop;
        create unique index on reasons (id);
        insert into reasons (select unnest(start), array[unnest(start)]);

        -- RAISE NOTICE 'init visited:';
        -- FOR items IN select id, reason from visited LOOP
        --     RAISE NOTICE 'v: %, r: %', items.id, items.reason;
        -- END LOOP;

        -- RAISE NOTICE 'init reasons:';
        -- FOR items IN select id, reasons from reasons LOOP
        --     RAISE NOTICE 'id: %, r: %', items.id, items.reasons;
        -- END LOOP;


        WHILE array_length(queue,1) > 0 LOOP
            insert into visited (select q, unnest(reasons) from unnest(queue) q join reasons on q = reasons.id) on conflict do nothing;
            -- RAISE NOTICE 'queue: %', queue;
            -- RAISE NOTICE 'visited: %', array(select * from visited);
            -- RAISE NOTICE 'reasons: % %', array(select id from reasons), array(select reasons from reasons);

            IF (select count(*) from visited) > 10000 THEN -- node limit reached, stop traversal
                queue := ARRAY[];
            ELSE

                insert into reasons (
                    select targetid, array_merge_agg(reasons) -- custom agg function
                    from (select unnest(queue) as id) as q
                    join rawconnection on sourceid = q.id and rawconnection.label = parentlabel
                    join reasons on reasons.id = q.id
                    group by targetid
                ) on conflict (id) do update set reasons = array_merge(reasons.reasons, excluded.reasons);

                nextqueue := array(
                    select distinct targetid
                    from (select unnest(queue) as id) as q
                    join rawconnection on sourceid = q.id and rawconnection.label = parentlabel
                    join reasons on reasons.id = q.id
                    left outer join visited on targetid = visited.id 
                    where visited.id is NULL or NOT (visited.reason = any(reasons.reasons))
                    limit 10000 -- also apply limit on node degree
                );

                -- RAISE NOTICE 'tmp queue: %', nextqueue;

                -- RAISE NOTICE 'tmp reasons:';
                -- FOR items IN select id, reasons from reasons LOOP
                --     RAISE NOTICE 'id: %, r: %', items.id, items.reasons;
                -- END LOOP;

                -- TODO: why do we have to do this twice? Else there might be problems with reason propagation through cycles.
                insert into reasons (
                    select targetid, array_merge_agg(reasons) -- custom agg function
                    from (select unnest(nextqueue) as id) as q
                    join rawconnection on sourceid = q.id and rawconnection.label = parentlabel
                    join reasons on reasons.id = q.id
                    group by targetid
                ) on conflict (id) do update set reasons = array_merge(reasons.reasons, excluded.reasons);

                nextqueue := array(
                    select distinct targetid
                    from (select unnest(nextqueue) as id) as q
                    join rawconnection on sourceid = q.id and rawconnection.label = parentlabel
                    join reasons on reasons.id = q.id
                    left outer join visited on targetid = visited.id 
                    where visited.id is NULL or NOT (visited.reason = any(reasons.reasons))
                    limit 10000 -- also apply limit on node degree
                );

                -- RAISE NOTICE 'nextqueue: %', nextqueue;

                --         raise notice 'iiiii: %', array(select targetid
                    --         from (select unnest(queue) as id) as q
                    --         join rawconnection on sourceid = q.id and rawconnection.label = parentlabel
                    --         join reasons on reasons.id = q.id);
                --         raise notice 'uuuuu: %', array(select reasons.reasons
                    --         from (select unnest(queue) as id) as q
                    --         join rawconnection on sourceid = q.id and rawconnection.label = parentlabel
                    --         join reasons on reasons.id = q.id);



                queue := nextqueue;
                -- RAISE NOTICE 'visited:';
                -- FOR items IN select id, reason from visited LOOP
                --     RAISE NOTICE 'v: %, r: %', items.id, items.reason;
                -- END LOOP;

                -- RAISE NOTICE 'reasons:';
                -- FOR items IN select id, reasons from reasons LOOP
                --     RAISE NOTICE 'id: %, r: %', items.id, items.reasons;
                -- END LOOP;

                -- RAISE NOTICE '';
            END IF;
    END LOOP;

    return query select membership.userid, array_merge_agg(reasons)
    from reasons
    join membership on reasons.id = membership.postid
    group by membership.userid;
end;
$func$ language plpgsql;

-- example data:
-- truncate rawpost cascade;
-- truncate "user" cascade;

-- COPY "user" (id, name, revision, isimplicit) FROM stdin;
-- cjevou708000116324rjb3glx	anon-cjevou708000116324rjb3glx	0	t
-- 2	2	0	f
-- 1	1	0	f
-- 3	3	0	f
-- 4	4	0	f
-- 5	5	0	f
-- \.

-- COPY rawpost (id, content, isdeleted, author, created, modified) FROM stdin;
-- 1	1	f	1	2018-03-22 14:24:08.168274	2018-03-22 14:24:08.168274
-- 2	2	f	1	2018-03-22 14:24:24.675758	2018-03-22 14:24:24.675758
-- 3	3	f	1	2018-03-22 14:24:32.083777	2018-03-22 14:24:32.083777
-- 4	4	f	1	2018-03-22 14:24:40.665532	2018-03-22 14:24:40.665532
-- 5	5	f	1	2018-03-22 14:24:47.079404	2018-03-22 14:24:47.079404
-- 6	6	f	1	2018-03-22 14:24:55.732919	2018-03-22 14:24:55.732919
-- 7	7	f	1	2018-03-22 14:25:01.289936	2018-03-22 14:25:01.289936
-- 8	8	f	1	2018-03-22 14:25:40.85306	2018-03-22 14:25:40.85306
-- 9	9	f	1	2018-03-22 14:41:11.904888	2018-03-22 14:41:11.904888
-- \.

-- COPY membership (userid, postid) FROM stdin;
-- 2	1
-- 1	3
-- 3	6
-- 4	7
-- 5	2
-- 5	3
-- \.

-- COPY rawconnection (sourceid, targetid, label) FROM stdin;
-- 2	1	2
-- 3	1	2
-- 4	2	2
-- 5	2	2
-- 5	3	2
-- 7	4	2
-- 8	5	2
-- 8	6	2
-- 2	7	2
-- 9	3	2
-- \.


-- for a chain of 1000 nodes
-- scala> for(i <- 50 to 1050) { db.post.createPublic(wust.db.Data.Post(wust.ids.PostId(i.toString), i.toString, wust.ids.UserId("1"))) }
-- scala> for(i <- 50 to 1049) { db.connection.apply(wust.db.Data.Connection(PostId(s"$i"), Label.parent, PostId((i+1).toString))) }
-- scala> db.connection.apply(wust.db.Data.Connection(PostId("1"), Label.parent, PostId("50")))

-- select * from notified_users(ARRAY['7', '2']);
-- select * from notified_users(ARRAY['2','8']);

