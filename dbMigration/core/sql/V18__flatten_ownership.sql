alter table membership alter column groupid drop not null;
alter table membership add column postid character varying(36) null; 

insert into membership (postid, userid)
    select o.postid, m.userid
    from ownership as o
    left outer join usergroup as g on o.groupid = g.id
    left outer join membership as m on g.id = m.groupid;

delete from membership where postid is null;
alter table membership drop column groupid;
alter table membership alter column postid set not null;

drop table groupinvite;
drop table ownership;
drop table usergroup;
