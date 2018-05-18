alter table "user"
    add column channelpostid varchar(36),
    add constraint fk_channelpostid
    foreign key (channelpostid)
    references rawpost (id);

with ins as (insert into rawpost (id, content, author, joinlevel) 
        (select 'c' || md5(random()::text)::varchar(24), '{"Channels":{}}', "user".id, 'read' from "user" where channelpostid is null) returning author as userid, id as channelpostid) update "user" set channelpostid = ins.channelpostid from ins where "user".id = ins.userid;

insert into membership (userid, postid, level) select "user".id, "user".channelpostid, 'read' from "user" on conflict do nothing;

alter table "user"
    alter channelpostid set not null;


-- page(parents, children) -> graph as adjacency list
create or replace function graph_page(parents varchar(36)[], children varchar(36)[], userid varchar(36)) returns table(postid varchar(36), content jsonb, author varchar(36), created timestamp without time zone, modified timestamp without time zone, joindate timestamp without time zone, joinlevel accesslevel, targetids varchar(36)[], labels text[]) as $$

 with cpid as (select channelpostid from "user" where id = userid)   
    select induced_subgraph(
        array[cpid.channelpostid] ||
        array(select sourceid from rawconnection,cpid where targetid = cpid.channelpostid and label = 2) ||
        readable_posts(userid, graph_page_posts(parents, children), children)
    )
    from cpid;

$$ language sql STABLE;

