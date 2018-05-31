drop view post;
alter table rawpost rename to post;

alter table post rename isdeleted to deleted;
alter table post
    alter column deleted drop default,
    alter column deleted type timestamp without time zone USING
      CASE WHEN deleted=true
        THEN timestamp '0001-01-01 00:00:00.000'
        ELSE timestamp '294276-01-01 00:00:00.000'
      END,
    alter column deleted set default timestamp '294276-01-01 00:00:00.000';

drop function induced_subgraph;
create or replace function induced_subgraph(postids varchar(36)[]) returns table(postid varchar(36), content jsonb, author varchar(36), created timestamp without time zone, modified timestamp without time zone, joindate timestamp without time zone, joinlevel accesslevel, deleted timestamp without time zone, targetids varchar(36)[], connectionContents text[]) as $$

    select
    post.id, post.content, post.author, post.created, post.modified, post.joindate, post.joinlevel, post.deleted, -- all post columns
    array_remove(array_agg(conn.targetid), NULL), array_remove(array_agg(conn.content::text), NULL) -- removing NULL is important (e.g. decoding NULL as a string fails)
    from post
    left outer join connection conn on conn.sourceid = post.id AND conn.targetid = ANY(postids)
    where post.id = ANY(postids)
    group by (post.id, post.content, post.author, post.created, post.modified, post.joindate, post.joinlevel, post.deleted) -- needed for multiple outgoing connections

$$ language sql STABLE;

-- page(parents, children) -> graph as adjacency list
drop function graph_page;
create or replace function graph_page(parents varchar(36)[], children varchar(36)[], userid varchar(36)) returns table(postid varchar(36), content jsonb, author varchar(36), created timestamp without time zone, modified timestamp without time zone, joindate timestamp without time zone, joinlevel accesslevel, deleted timestamp without time zone, targetids varchar(36)[], connectioncontents text[]) as $$

 with cpid as (select channelpostid from "user" where id = userid)
    select induced_subgraph(
        array[cpid.channelpostid] ||
        array(select sourceid from connection,cpid where targetid = cpid.channelpostid and content->>'type' = 'Parent') ||
        readable_posts(userid, graph_page_posts(parents, children), children)
    )
    from cpid;

$$ language sql;

-- page(parents, children) -> graph as adjacency list
drop function graph_page_with_orphans;
create or replace function graph_page_with_orphans(parents varchar(36)[], children varchar(36)[], userid varchar(36)) returns table(postid varchar(36), content jsonb, author varchar(36), created timestamp without time zone, modified timestamp without time zone, joindate timestamp without time zone, joinlevel accesslevel, deleted timestamp without time zone, targetids varchar(36)[], connectioncontents text[]) as $$

 with cpid as (select channelpostid from "user" where id = userid)
    select induced_subgraph(
        array[cpid.channelpostid] ||
        array(select sourceid from connection,cpid where targetid = cpid.channelpostid and content->>'type' = 'Parent') ||
        readable_posts(userid, 
            array(select id from post where id not in (select sourceid from connection where content->>'type' = 'Parent')) ||
            graph_page_posts(parents, children), children
        )
    )
    from cpid;

$$ language sql;
