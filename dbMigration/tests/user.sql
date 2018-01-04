BEGIN;
SELECT plan(3);

SELECT col_is_pk('usergroup', 'id');
SELECT col_not_null('usergroup', 'id');

SELECT is_empty(
    $$select * from usergroup$$,
    'select user in empty db'
);

SELECT * FROM finish();
ROLLBACK;
