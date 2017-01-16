BEGIN;
SELECT plan(8);

-- does your table even exist and does it have a primary key?
SELECT has_table('users');
SELECT has_pk('users');

-- does it have the column username and what type is it?
SELECT has_column('users', 'username');
SELECT col_not_null('users', 'username');
SELECT col_type_is('users', 'username', 'character varying(50)');

-- can you insert new data?
SELECT lives_ok(
  'INSERT INTO
    users (username, lastname, firstname, email)
  VALUES
    ($$poppi$$, $$Schneider$$, $$Petra$$, $$petra@foobar.baz$$);',
  'inserting a new user with a unique username should be ok'
);

-- are you properly rejected when inserting bad data?
SELECT throws_ok(
  'INSERT INTO
    users (username, lastname, firstname, email)
  VALUES
    ($$testi1$$, $$Müller$$, $$Peter$$, $$muelli@foobar.baz$$);',
  'duplicate key value violates unique constraint "users_pkey"',
  'inserting a username already existing should throw a unique constraint violation'
);

-- does your super fancy complicated select return what you expect?
PREPARE basic_select AS
  SELECT username, lastname, firstname
  FROM users
  WHERE lastname = 'Schmidt';

SELECT results_eq (
  'basic_select',
  $$VALUES('neu3'::character varying(50), 'Schmidt'::character varying, 'Hans-Peter'::character varying)$$,
  'should select all users with a lastname "Schmidt"'
);


SELECT * FROM finish();
ROLLBACK;
