BEGIN;
SELECT plan(11);

/* structure */
SELECT col_not_null('rawconnection', 'sourceid');
SELECT col_not_null('rawconnection', 'targetid');

SELECT isnt_empty(
    $$ INSERT INTO "user" (id, name) VALUES ('heidi', 'Rolufus') RETURNING (id, name) $$,
    'insert user'
);

SELECT isnt_empty(
    $$ INSERT INTO label (name) VALUES ('labello') RETURNING (id, name) $$,
    'insert label'
);

SELECT results_eq(
    $$ SELECT * FROM label WHERE name = 'labello' $$,
    $$ VALUES(3::INTEGER, 'labello'::TEXT) $$,
    'test if new label is persistent'
);

SELECT isnt_empty(
    $$ INSERT INTO post (id, content, author, created, modified)
        VALUES ('hester', 'Schneider', 1, NOW(), NOW())
        RETURNING (id, content, author, created, modified) $$,
    'insert post'
);

SELECT isnt_empty(
    $$ INSERT INTO post (id, content, author, created, modified) VALUES ('invester', 'Schneider2', 1, NOW(), NOW()) RETURNING (id, content, author, created, modified) $$,
    'insert second post'
);

SELECT results_eq(
    $$ INSERT INTO connection (sourceid, label, targetid)
        VALUES ('hester', 'charals', 'invester')
        RETURNING (sourceid, label, targetid) $$,
    $$ VALUES( ('hester'::VARCHAR, 'charals'::TEXT, 'invester'::VARCHAR) ) $$,
    'insert connection'
);

SELECT results_eq(
    $$ INSERT INTO connection (sourceid, label, targetid)
    VALUES ('hester', 'babe', 'invester')
    RETURNING (sourceid, label, targetid) $$,
    $$ VALUES( ('hester'::VARCHAR, 'babe'::TEXT, 'invester'::VARCHAR) ) $$,
    'insert second connection with different label'
);

SELECT results_eq(
    $$ SELECT * FROM label WHERE name = 'babe' $$,
    $$ VALUES(5::INTEGER, 'babe'::TEXT) $$,
    'test if new label is persistent in connection view'
);

SELECT results_eq(
    $$ DELETE FROM connection
        WHERE sourceid = 'hester' AND label = 'babe' AND targetid = 'invester'
        RETURNING (sourceid, label, targetid) $$,
    $$ VALUES( ('hester'::VARCHAR, 'babe'::TEXT, 'invester'::VARCHAR) ) $$,
    'delete connection'
);

SELECT * FROM finish();
ROLLBACK;
