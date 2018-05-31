BEGIN;
SELECT plan(4);

/* structure */
SELECT col_not_null('connection', 'sourceid');
SELECT col_not_null('connection', 'targetid');

SELECT isnt_empty(
    $$ INSERT INTO post (id, content, author, created, modified)
        VALUES ('hester', '{}'::jsonb, 1, NOW(), NOW())
        RETURNING (id, content, author, created, modified) $$,
    'insert post'
);

SELECT isnt_empty(
    $$ INSERT INTO post (id, content, author, created, modified) VALUES ('invester', '{}'::jsonb, 1, NOW(), NOW()) RETURNING (id, content, author, created, modified) $$,
    'insert second post'
);

SELECT * FROM finish();
ROLLBACK;
