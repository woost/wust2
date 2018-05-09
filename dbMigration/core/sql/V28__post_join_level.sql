DROP VIEW post;

-- old behavior
update rawpost set locked = '294276-01-01 00:00:00.000' where locked is null;
ALTER TABLE "rawpost" alter COLUMN locked set not null;

ALTER TABLE "rawpost" RENAME COLUMN locked TO joindate;
ALTER TABLE "rawpost" alter COLUMN joindate set default '0001-01-01 00:00:00.000'; -- https://www.postgresql.org/docs/9.1/static/datatype-datetime.html

CREATE TYPE accesslevel AS ENUM ('read', 'readwrite');
ALTER TABLE "rawpost" ADD COLUMN joinlevel accesslevel NOT NULL DEFAULT 'readwrite';

ALTER TABLE "membership" ADD COLUMN level accesslevel NOT NULL DEFAULT 'readwrite';

CREATE OR REPLACE VIEW post AS
    SELECT id,content,author,created,modified,joindate,joinlevel
        FROM rawpost WHERE isdeleted = false;


drop function graph_page;
drop function induced_subgraph;

-- induced subgraph: postids -> ajacency list
create or replace function induced_subgraph(postids varchar(36)[]) returns table(postid varchar(36), content text, author varchar(36), created timestamp without time zone, modified timestamp without time zone, joindate timestamp without time zone, joinlevel accesslevel, targetids varchar(36)[], labels text[]) as $$

    select
    post.id, post.content, post.author, post.created, post.modified, post.joindate, post.joinlevel, -- all post columns
    array_remove(array_agg(conn.targetid), NULL), array_remove(array_agg(conn.label), NULL) -- removing NULL is important (e.g. decoding NULL as a string fails)
    from post
    left outer join connection conn on conn.sourceid = post.id AND conn.targetid = ANY(postids)
    where post.id = ANY(postids)
    group by (post.id, post.content, post.author, post.created, post.modified, post.joindate, post.joinlevel) -- needed for multiple outgoing connections

$$ language sql;

CREATE FUNCTION array_intersect(anyarray, anyarray)
  RETURNS anyarray
  language sql
as $FUNCTION$
    SELECT ARRAY(
        SELECT UNNEST($1)
        INTERSECT
        SELECT UNNEST($2)
    );
$FUNCTION$;


-- expects table visited
create or replace function readable_posts(requester_userid varchar(36), postids varchar(36)[], stopchildren varchar(36)[] default array[]::varchar(36)[]) returns varchar(36)[] as $$
declare
    transitive_parents varchar(36)[];
    readable_parents varchar(36)[];
    readable_transitive_parents varchar(36)[];
begin
    -- transitive_parents = traverse all postids up (over parent connection), collect all postids
    -- readable_parents = join transitive_parents with memebership and retain the ones with membership
    -- readable_transitive_parents = traverse readable_parents down and stop at lower page bound (stopchildren)
    -- return: intersect readable_transitive_parents with postids

    -- we have to privde the temporary visited table for the traversal functions,
    -- since it is not possible to create function local temporary tables.
    -- Creating is transaction local, which leads to "table 'visited' already exists" errors,
    -- when created inside functions.
--    create temporary table visited (id varchar(36) NOT NULL) on commit drop;
--    create unique index on visited (id);
    truncate table visited; -- truncating is needed, because else, the next traversal stops at the already visited vertices, even if they have further unvisited incoming edges

    transitive_parents := traverse_parents(postids);
    -- RAISE NOTICE 'transitive_parents: %', transitive_parents;

    readable_parents := array(select postid from membership where membership.userid = requester_userid and postid = any (transitive_parents));
    -- RAISE NOTICE 'readable_parents: %', readable_parents;

    truncate table visited; -- truncating is needed, because else, the next traversal stops at the already visited vertices, even if they have further unvisited incoming edges
    readable_transitive_parents := traverse_children(readable_parents, stopchildren); -- traversal stops at lower page bound
    -- RAISE NOTICE 'readable_transitive_parents: %', readable_transitive_parents;

    return array_intersect(readable_transitive_parents, postids);
end;
$$ language plpgsql;

-- page(parents, children) -> graph as adjacency list
create or replace function graph_page(parents varchar(36)[], children varchar(36)[], userid varchar(36)) returns table(postid varchar(36), content text, author varchar(36), created timestamp without time zone, modified timestamp without time zone, joindate timestamp without time zone, joinlevel accesslevel, targetids varchar(36)[], labels text[]) as $$
    select induced_subgraph(readable_posts(userid, graph_page_posts(parents, children), children));
$$ language sql;
