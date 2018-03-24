-- deleting duplicates before adding unique index:
delete from membership a using membership b where a.ctid < b.ctid and a.userid = b.userid and a.postid = b.postid;

alter table membership add PRIMARY KEY (userid,postid);

