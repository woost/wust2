BEGIN;
SELECT plan(1);

create temporary table visited (id varchar(36) NOT NULL) on commit drop;
create unique index on visited (id);

INSERT INTO post (id, content, author, created, modified) VALUES ('bla', '{}'::jsonb, 1, NOW(), NOW());
INSERT INTO post (id, content, author, created, modified) VALUES ('upid', '{}'::jsonb, 1, NOW(), NOW());
COPY "user" (id, name, channelpostid, userpostid) FROM stdin;
U1	U1	bla	upid
U2	U2	bla	upid
\.

COPY post (id, content, deleted, author) FROM stdin;
1	{}	'294276-01-01 00:00:00.000'	U1
2	{}	'294276-01-01 00:00:00.000'	U1
3	{}	'294276-01-01 00:00:00.000'	U1
4	{}	'294276-01-01 00:00:00.000'	U1
5	{}	'294276-01-01 00:00:00.000'	U1
6	{}	'294276-01-01 00:00:00.000'	U1
\.

COPY connection (sourceid, targetid, content) FROM stdin;
2	3	{"type": "Parent" }
2	1	{"type": "Parent" }
6	5	{"type": "Parent" }
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
