create extension pgtap;

-- http://pgtap.org/documentation.html#usingpgtap


SELECT plan( 42 ); -- how many tests will be max run?
-- SELECT plan( COUNT(*) )
--  FROM foo;

--SELECT is()
SELECT * FROM runtests();

SELECT * FROM finish();
