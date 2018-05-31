alter table "user"
    add column userpostid varchar(36),
    add constraint fk_userpostid
    foreign key (userpostid)
    references rawpost (id);


with ins as (insert into rawpost (id, content, author, joinlevel) 
        (select 'c' || md5(random()::text)::varchar(24), '{"Type":"User"}', "user".id, 'read' from "user" where userpostid is null) returning author as userid, id as userpostid) update "user" set userpostid = ins.userpostid from ins where "user".id = ins.userid;

alter table "user"
    alter userpostid set not null;
