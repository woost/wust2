BEGIN;
SELECT plan(21);

/* structure */
SELECT col_not_null('post', 'title');
SELECT col_not_null('connection', 'sourceid');
SELECT col_not_null('connection', 'targetid');
SELECT col_not_null('containment', 'parentid');
SELECT col_not_null('containment', 'childid');

/* insert small graph */
SELECT isnt_empty(
  'INSERT INTO
    post (id, title)
  VALUES
    (DEFAULT, $$Schneider$$)
   RETURNING
    (id, title);',
  'insert post'
);

SELECT isnt_empty(
  'INSERT INTO
    post (id, title)
  VALUES
    (DEFAULT, $$Schneider$$)
   RETURNING
    (id, title);',
  'insert second post'
);

SELECT isnt_empty(
  'INSERT INTO
    connection (sourceid, targetid)
  VALUES
    (1, 2)
   RETURNING
    (sourceid, targetid);',
  'insert connection'
);

SELECT isnt_empty(
  'INSERT INTO
    containment (parentid, childid)
  VALUES
    (2, 1)
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
    (1, 2)
   RETURNING
    (sourceid, targetid);',
  'insert connection after delete'
);

SELECT isnt_empty(
  'INSERT INTO
    containment (parentid, childid)
  VALUES
    (2, 1)
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
