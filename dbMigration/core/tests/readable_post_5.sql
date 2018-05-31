BEGIN;
SELECT plan(1);

create temporary table visited (id varchar(36) NOT NULL) on commit drop;
create unique index on visited (id);

INSERT INTO post (id, content, author, created, modified) VALUES ('bla', '{}'::jsonb, 1, NOW(), NOW());
INSERT INTO post (id, content, author, created, modified) VALUES ('upid', '{}'::jsonb, 1, NOW(), NOW());
COPY "user" (id, name, channelpostid, userpostid) FROM stdin;
U1	U1	bla	upid
\.

COPY post (id, content, deleted, author) FROM stdin;
1	{}	'294276-01-01 00:00:00.000'	U1
2	{}	'294276-01-01 00:00:00.000'	U1
\.

COPY connection (sourceid, targetid, content) FROM stdin;
2	1	{"type": "Parent" }
\.

COPY membership (userid, postid) FROM stdin;
U1	1
\.

-- no membership exists, therefore not allowed to see anything
SELECT cmp_ok(readable_posts('U1', array['1', '2']), '=', array['2', '1']::varchar(36)[]);

SELECT * FROM finish();
ROLLBACK;
