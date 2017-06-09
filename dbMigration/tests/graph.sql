BEGIN;
SELECT plan(26);

/* structure */
SELECT col_not_null('rawpost', 'title');
SELECT col_not_null('rawpost', 'isdeleted');
SELECT col_not_null('connection', 'sourceid');
SELECT col_not_null('connection', 'targetid');
SELECT col_not_null('containment', 'parentid');
SELECT col_not_null('containment', 'childid');
SELECT col_not_null('ownership', 'postid');
SELECT col_not_null('ownership', 'groupid');
SELECT col_not_null('membership', 'userid');
SELECT col_not_null('membership', 'groupid');

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
    connection (sourceid, targetid)
  VALUES
    ($$hester$$, $$invester$$)
   RETURNING
    (sourceid, targetid);',
  'insert connection'
);

SELECT isnt_empty(
  'INSERT INTO
    containment (parentid, childid)
  VALUES
    ($$invester$$, $$hester$$)
   RETURNING
    (parentid, childid);',
  'insert containment'
);

SELECT throws_ok(
  'INSERT INTO
    connection (sourceid, targetid)
  VALUES
    (3, 3);',
  23514,
  'new row for relation "connection" violates check constraint "selfloop"',
  'connection self-loop constraint'
);

SELECT throws_ok(
  'INSERT INTO
    containment (parentid, childid)
  VALUES
    (3, 3);',
  23514,
  'new row for relation "containment" violates check constraint "selfloop"',
  'containment self-loop constraint'
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

SELECT lives_ok(
  'delete from containment where true',
  'delete containment'
);

select is_empty(
  'select * from containment',
  'containment is empty'
);

/* insert edges again */
SELECT isnt_empty(
  'INSERT INTO
    connection (sourceid, targetid)
  VALUES
    ($$hester$$, $$invester$$)
   RETURNING
    (sourceid, targetid);',
  'insert connection after delete'
);

SELECT isnt_empty(
  'INSERT INTO
    containment (parentid, childid)
  VALUES
    ($$invester$$, $$hester$$)
   RETURNING
    (parentid, childid);',
  'insert containment'
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

select is_empty(
  'select * from containment',
  'containment is empty after post collapse'
);


SELECT * FROM finish();
ROLLBACK;
