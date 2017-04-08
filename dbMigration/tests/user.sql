BEGIN;
SELECT plan(2);

-- TODO: count?
SELECT isnt_empty(
    'select * from usergroup where id = 1'
);

SELECT is_empty(
    'select * from usergroup where id <> 1'
);

SELECT * FROM finish();
ROLLBACK;
