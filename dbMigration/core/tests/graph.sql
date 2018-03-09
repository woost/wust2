BEGIN;
SELECT plan(17);

/* structure */
SELECT col_not_null('rawpost', 'content');
SELECT col_not_null('rawpost', 'isdeleted');
SELECT col_not_null('rawconnection', 'sourceid');
SELECT col_not_null('rawconnection', 'targetid');
SELECT col_not_null('membership', 'userid');
SELECT col_not_null('membership', 'postid');

/* insert label */
SELECT isnt_empty(
  $$ INSERT INTO label (name) VALUES ('labello')
   RETURNING (id, name) $$,
  'insert post'
);

/* insert small graph */
SELECT isnt_empty(
    $$ INSERT INTO post (id, content, author, created, modified)
        VALUES ('hester', 'Schneider', 1, NOW(), NOW())
        RETURNING (id, content, author, created, modified) $$,
    'insert post'
);

SELECT isnt_empty(
    $$ INSERT INTO post (id, content, author, created, modified)
        VALUES ('invester', 'Schneider2', 1, NOW(), NOW())
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

/* delete post and collapse on edges */
SELECT lives_ok(
  $$ DELETE FROM post WHERE TRUE $$,
  'delete post'
);

select is_empty(
  $$ SELECT * FROM post $$,
  'post is empty'
);

select is_empty(
  $$ select * from connection $$,
  'connection is empty after post collapse'
);

SELECT * FROM finish();
ROLLBACK;
