BEGIN;
SELECT plan(1);

create temporary table visited (id varchar(36) NOT NULL) on commit drop;
create unique index on visited (id);

COPY "user" (id, name) FROM stdin;
U1	U1
U2	U2
\.

COPY rawpost (id, content, isdeleted, author) FROM stdin;
1	1	f	U1
2	2	f	U1
3	3	f	U1
4	4	f	U1
5	5	f	U1
6	6	f	U1
\.

COPY rawconnection (sourceid, targetid, label) FROM stdin;
2	3	2
2	1	2
6	5	2
\.

COPY membership (userid, postid) FROM stdin;
U1	1
U1	4
U2	4
U2	5
\.

insert into membership(userid, postid) values ('U2', '1');

-- no membership exists, therefore not allowed to see anything
SELECT cmp_ok(readable_posts('U1', array['2','4','6']), '=', array['2', '4']::varchar(36)[]);

SELECT * FROM finish();
ROLLBACK;
