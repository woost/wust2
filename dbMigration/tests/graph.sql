BEGIN;
SELECT plan(19);

/* structure */
SELECT col_not_null('_post', 'title');
SELECT col_not_null('_incidence', 'sourceid');
SELECT col_not_null('_incidence', 'targetid');

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
    connection (id, sourceId, targetId)
  VALUES
    (DEFAULT, 1, 2)
   RETURNING
    (id, sourceId, targetId);',
  'insert connection'
);

SELECT isnt_empty(
  'INSERT INTO
    containment (id, parentId, childId)
  VALUES
    (DEFAULT, 2, 1)
   RETURNING
    (id, parentId, childId);',
  'insert containment'
);

/* graph component */
SELECT results_eq(
  'select graph_component(1)',
  'select id from post',
  'graph component'
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
    connection (id, sourceId, targetId)
  VALUES
    (DEFAULT, 1, 2)
   RETURNING
    (id, sourceId, targetId);',
  'insert connection after delete'
);

SELECT isnt_empty(
  'INSERT INTO
    containment (id, parentId, childId)
  VALUES
    (DEFAULT, 2, 1)
   RETURNING
    (id, parentId, childId);',
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

select is_empty(
  'select * from atom',
  'atoms is empty'
);


SELECT * FROM finish();
ROLLBACK;
