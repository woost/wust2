BEGIN;
SELECT plan(19);

/* structure */
SELECT col_not_null('_post', 'title');
SELECT has_column('post', 'title');

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
    connects (id, sourceId, targetId)
  VALUES
    (DEFAULT, 1, 2)
   RETURNING
    (id, sourceId, targetId);',
  'insert connects'
);

SELECT isnt_empty(
  'INSERT INTO
    contains (id, parent, child)
  VALUES
    (DEFAULT, 2, 1)
   RETURNING
    (id, parent, child);',
  'insert contains'
);

/* graph component */
SELECT results_eq(
  'select graph_component(1)',
  'select id from post',
  'graph component'
);

/* delete edges */
SELECT lives_ok(
  'delete from connects where true',
  'delete connects'
);

select is_empty(
  'select * from connects',
  'connects is empty'
);

SELECT lives_ok(
  'delete from contains where true',
  'delete contains'
);

select is_empty(
  'select * from contains',
  'contains is empty'
);

/* insert edges again */
SELECT isnt_empty(
  'INSERT INTO
    connects (id, sourceId, targetId)
  VALUES
    (DEFAULT, 1, 2)
   RETURNING
    (id, sourceId, targetId);',
  'insert connects after delete'
);

SELECT isnt_empty(
  'INSERT INTO
    contains (id, parent, child)
  VALUES
    (DEFAULT, 2, 1)
   RETURNING
    (id, parent, child);',
  'insert contains'
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
  'select * from connects',
  'connects is empty after post collapse'
);

select is_empty(
  'select * from atom',
  'atoms is empty'
);


SELECT * FROM finish();
ROLLBACK;
