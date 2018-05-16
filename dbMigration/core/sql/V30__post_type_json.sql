DROP VIEW post;

ALTER TABLE "rawpost" drop COLUMN tpe;

ALTER TABLE rawpost
    ALTER COLUMN content TYPE JSONB USING json_build_object('Markdown', json_build_object('content', content));



CREATE OR REPLACE VIEW post AS
    SELECT id,content,author,created,modified,joindate,joinlevel
        FROM rawpost WHERE isdeleted = false;


drop function graph_page;
drop function induced_subgraph;

-- induced subgraph: postids -> ajacency list
create or replace function induced_subgraph(postids varchar(36)[]) returns table(postid varchar(36), content jsonb, author varchar(36), created timestamp without time zone, modified timestamp without time zone, joindate timestamp without time zone, joinlevel accesslevel, targetids varchar(36)[], labels text[]) as $$

    select
    post.id, post.content, post.author, post.created, post.modified, post.joindate, post.joinlevel, -- all post columns
    array_remove(array_agg(conn.targetid), NULL), array_remove(array_agg(conn.label), NULL) -- removing NULL is important (e.g. decoding NULL as a string fails)
    from post
    left outer join connection conn on conn.sourceid = post.id AND conn.targetid = ANY(postids)
    where post.id = ANY(postids)
    group by (post.id, post.content, post.author, post.created, post.modified, post.joindate, post.joinlevel) -- needed for multiple outgoing connections

$$ language sql;


-- page(parents, children) -> graph as adjacency list
create or replace function graph_page(parents varchar(36)[], children varchar(36)[], userid varchar(36)) returns table(postid varchar(36), content jsonb, author varchar(36), created timestamp without time zone, modified timestamp without time zone, joindate timestamp without time zone, joinlevel accesslevel, targetids varchar(36)[], labels text[]) as $$
    select induced_subgraph(readable_posts(userid, graph_page_posts(parents, children), children));
$$ language sql;
