-- page(parents, children) -> graph as adjacency list
create or replace function graph_page_with_orphans(parents varchar(36)[], children varchar(36)[], userid varchar(36)) returns table(postid varchar(36), content jsonb, author varchar(36), created timestamp without time zone, modified timestamp without time zone, joindate timestamp without time zone, joinlevel accesslevel, targetids varchar(36)[], labels text[]) as $$

 with cpid as (select channelpostid from "user" where id = userid)
    select induced_subgraph(
        array[cpid.channelpostid] ||
        array(select sourceid from rawconnection,cpid where targetid = cpid.channelpostid and label = 2) ||
        readable_posts(userid, 
            array(select id from post where id not in (select sourceid from rawconnection where label = 2)) ||
            graph_page_posts(parents, children), children
        )
    )
    from cpid;

$$ language sql STABLE;
