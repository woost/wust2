BEGIN;
SELECT plan(4);

SELECT col_is_pk('usergroup', 'id');
SELECT col_not_null('usergroup', 'id');

SELECT isnt_empty(
    'select * from usergroup where id = 1'
);

SELECT is_empty(
    'select * from usergroup where id <> 1'
);

SELECT * FROM finish();
ROLLBACK;
