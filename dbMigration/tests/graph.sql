BEGIN;
SELECT plan(19);
-- SELECT plan(8);

/* structure */
SELECT col_not_null('rawpost', 'title');
SELECT col_not_null('rawpost', 'isdeleted');
SELECT col_not_null('rawconnection', 'sourceid');
SELECT col_not_null('rawconnection', 'targetid');
SELECT col_not_null('ownership', 'postid');
SELECT col_not_null('ownership', 'groupid');
SELECT col_not_null('membership', 'userid');
SELECT col_not_null('membership', 'groupid');

/* insert label */
SELECT isnt_empty(
  'INSERT INTO
    label (name)
  VALUES
    ($$labello$$)
   RETURNING
    (id, name);',
  'insert post'
);

/* insert small graph */
SELECT isnt_empty(
  'INSERT INTO
    post (id, title)
  VALUES
    ($$hester$$, $$Schneider$$)
   RETURNING
    (id, title);',
  'insert post'
);

SELECT isnt_empty(
  'INSERT INTO
    post (id, title)
  VALUES
    ($$invester$$, $$Schneider2$$)
   RETURNING
    (id, title);',
  'insert second post'
);

SELECT isnt_empty(
  'INSERT INTO
    connection (sourceid, label, targetid)
  VALUES
    ($$hester$$, $$charals$$, $$invester$$)
   RETURNING
    (sourceid, targetid);',
  'insert connection'
);

SELECT throws_ok(
  'INSERT INTO
    connection (sourceid, label, targetid)
  VALUES
    (3, $$charals$$, 3);',
  23514,
  'new row for relation "rawconnection" violates check constraint "selfloop"',
  'connection self-loop constraint'
);

/* delete edges */
SELECT lives_ok(
  'delete from connection where true',
  'delete connection'
);

select is_empty(
  'select * from connection',
  'connection is empty'
);

/* insert edges again */
SELECT isnt_empty(
  'INSERT INTO
    connection (sourceid, label, targetid)
  VALUES
    ($$hester$$, $$charals$$, $$invester$$)
   RETURNING
    (sourceid, targetid);',
  'insert connection after delete'
);

/* delete post and collapse on edges */
SELECT lives_ok(
  'delete from post where true',
  'delete post'
);

select is_empty(
  'select * from post',
  'post is empty'
);

select is_empty(
  'select * from connection',
  'connection is empty after post collapse'
);

SELECT * FROM finish();
ROLLBACK;
