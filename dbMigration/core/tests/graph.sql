BEGIN;
SELECT plan(17);

/* structure */
SELECT col_not_null('rawpost', 'content');
SELECT col_not_null('rawpost', 'isdeleted');
SELECT col_not_null('rawconnection', 'sourceid');
SELECT col_not_null('rawconnection', 'targetid');
SELECT col_not_null('membership', 'userid');
SELECT col_not_null('membership', 'postid');

INSERT INTO post (id, content, author, created, modified) VALUES ('bla', '{}'::jsonb, 1, NOW(), NOW());
insert into "user" (id, name, revision, isimplicit, channelpostid) values ('bubuid', 'bubu', 0, false, 'bla');

/* insert label */
SELECT isnt_empty(
  $$ INSERT INTO label (name) VALUES ('labello')
   RETURNING (id, name) $$,
  'insert post'
);

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
  $$ INSERT INTO connection (sourceid, label, targetid)
    VALUES ('hester', 'charals', 'invester')
   RETURNING (sourceid, targetid) $$,
  'insert connection'
);

SELECT throws_ok(
  $$ INSERT INTO connection (sourceid, label, targetid)
    VALUES (3, 'charals', 3) $$,
  23514,
  'new row for relation "rawconnection" violates check constraint "selfloop"',
  'connection self-loop constraint'
);

/* delete edges */
SELECT lives_ok(
  $$ DELETE FROM connection WHERE TRUE $$,
  'delete connection'
);

select is_empty(
  $$ SELECT * FROM connection $$,
  'connection is empty'
);

/* insert edges again */
SELECT isnt_empty(
  $$ INSERT INTO connection (sourceid, label, targetid)
    VALUES ('hester', 'charals', 'invester')
    RETURNING (sourceid, targetid) $$,
  'insert connection after delete'
);

/* delete post and collapse on edges (except channelpost of user) */
SELECT lives_ok(
  $$ DELETE FROM post WHERE id not in (select channelpostid from "user") $$,
  'delete post'
);

select is_empty(
  $$ SELECT * FROM post WHERE id not in (select channelpostid from "user") $$,
  'post is empty'
);

select is_empty(
  $$ select * from connection $$,
  'connection is empty after post collapse'
);

SELECT * FROM finish();
ROLLBACK;
