BEGIN;
SELECT plan(10);

/* structure */
SELECT col_not_null('post', 'content');
SELECT col_not_null('post', 'deleted');
SELECT col_not_null('connection', 'sourceid');
SELECT col_not_null('connection', 'targetid');
SELECT col_not_null('membership', 'userid');
SELECT col_not_null('membership', 'postid');

INSERT INTO post (id, content, author, created, modified) VALUES ('bla', '{}'::jsonb, 1, NOW(), NOW());
INSERT INTO post (id, content, author, created, modified) VALUES ('upid', '{}'::jsonb, 1, NOW(), NOW());
insert into "user" (id, name, revision, isimplicit, channelpostid, userpostid) values ('bubuid', 'bubu', 0, false, 'bla', 'upid');

/* insert small graph */
SELECT isnt_empty(
    $$ INSERT INTO post (id, content, author, created, modified)
        VALUES ('hester', '{}'::jsonb, 'bubuid', NOW(), NOW())
        RETURNING (id, content, author, created, modified) $$,
    'insert post'
);

SELECT isnt_empty(
    $$ INSERT INTO post (id, content, author, created, modified)
        VALUES ('invester', '{}'::jsonb, 'bubuid', NOW(), NOW())
        RETURNING (id, content, author, created, modified) $$,
    'insert second post'
);

SELECT isnt_empty(
  $$ INSERT INTO connection (sourceid, content, targetid)
    VALUES ('hester', '{"type": "charals" }'::jsonb, 'invester')
   RETURNING (sourceid, targetid) $$,
  'insert connection'
);

SELECT throws_ok(
  $$ INSERT INTO connection (sourceid, content, targetid)
    VALUES (3, '{"type": "charals" }'::jsonb, 3) $$,
  23514,
  'new row for relation "connection" violates check constraint "selfloop"',
  'connection self-loop constraint'
);

SELECT * FROM finish();
ROLLBACK;
