BEGIN;
SELECT plan(12);

SELECT col_not_null('_post', 'title');
SELECT has_column('post', 'title');

SELECT col_not_null('_incidence', 'sourceid');
SELECT col_not_null('_incidence', 'targetid');

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

SELECT results_eq(
  'select graph_component(1)',
  'select id from post',
  'graph component'
);

SELECT lives_ok(
  'delete from post where true',
  'delete post'
);

SELECT lives_ok(
  'delete from connects where true',
  'delete connects'
);

SELECT lives_ok(
  'delete from contains where true',
  'delete contains'
);


SELECT * FROM finish();
ROLLBACK;
