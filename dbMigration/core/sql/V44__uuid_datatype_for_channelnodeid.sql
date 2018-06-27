-- source: http://www.jamiebegin.com/base36-conversion-in-postgresql/
CREATE OR REPLACE FUNCTION base36_decode(IN base36 varchar)
  RETURNS bigint AS $$
        DECLARE
			a char[];
			ret bigint;
			i int;
			val int;
			chars varchar;
		BEGIN
		chars := '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';
 
		FOR i IN REVERSE char_length(base36)..1 LOOP
			a := a || substring(upper(base36) FROM i FOR 1)::char;
		END LOOP;
		i := 0;
		ret := 0;
		WHILE i < (array_length(a,1)) LOOP		
			val := position(a[i+1] IN chars)-1;
			ret := ret + (val * (36 ^ i));
			i := i + 1;
		END LOOP;
 
		RETURN ret;
 
END;
$$ LANGUAGE 'plpgsql' IMMUTABLE;

CREATE OR REPLACE FUNCTION cuid_to_uuid(IN base36 varchar)
  RETURNS uuid AS $$
    select uuid(lpad(to_hex(base36_decode(substring(base36 from 2 for 12))), 16, '0') || lpad(to_hex(base36_decode(substring(base36 from 14 for 12))), 16, '0'))
$$ LANGUAGE 'sql' IMMUTABLE;

update node set data = data || json_build_object('channelNodeId', cuid_to_uuid(data->>'channelNodeId'))::jsonb where data->>'type' = 'User' and not(data->>'channelNodeId' ~ '-');

drop function base36_decode;
drop function cuid_to_uuid;

