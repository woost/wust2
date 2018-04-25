CREATE OR REPLACE FUNCTION mergeFirstUserIntoSecond(oldUser varchar(36), keepUser varchar(36)) RETURNS VOID AS $$
 DECLARE
 tables CURSOR FOR
    select conrelid::regclass as table, a.attname as col
    from pg_attribute af, pg_attribute a,
    (select conrelid,confrelid,conkey[i] as conkey, confkey[i] as confkey
    from (select conrelid,confrelid,conkey,confkey,
                    generate_series(1,array_upper(conkey,1)) as i
            from pg_constraint where contype = 'f') ss) ss2
    where af.attnum = confkey and af.attrelid = confrelid and
        a.attnum = conkey and a.attrelid = conrelid 
    AND confrelid::regclass = 'user'::regclass AND af.attname = 'id';
 BEGIN

 FOR table_record IN tables LOOP
    BEGIN
        execute format('update %I set %I = ''%I'' where %I = ''%I''', table_record.table, table_record.col, keepUser, table_record.col, oldUser);
    EXCEPTION WHEN OTHERS THEN
        --do nothing or write NULL means do nothing
    END;
    execute format('delete from %I where %I = ''%I''', table_record.table, table_record.col, oldUser);
 END LOOP;

 execute format('delete from "user" where id = ''%I''', oldUser);
 END;
$$ LANGUAGE plpgsql;
