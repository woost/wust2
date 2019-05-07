CREATE OR REPLACE FUNCTION base36_encode(IN digits bigint, IN min_width int) RETURNS varchar AS $$
DECLARE
    chars char[]; 
    ret varchar; 
    val bigint; 
BEGIN
    chars := ARRAY['0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z'  
    ];
    val := digits; 
    ret := ''; 
    IF val < 0 THEN 
        val := val * -1; 
    END IF; 
    WHILE val != 0 LOOP 
        ret := chars[(val % 36)+1] || ret; 
        val := val / 36; 
    END LOOP;

    IF min_width > 0 AND char_length(ret) < min_width THEN 
        ret := lpad(ret, min_width, '0'); 
    END IF;

    RETURN ret;
END;
$$ LANGUAGE plpgsql IMMUTABLE;
CREATE OR REPLACE FUNCTION base36_decode(IN base36 text)
  RETURNS numeric AS $$
        DECLARE
			a char[];
			ret numeric;
			i int;
			val numeric;
			chars text;
		BEGIN
		chars := '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';

		FOR i IN REVERSE char_length(base36)..1 LOOP
			a := a || substring(upper(base36) FROM i FOR 1)::char;
		END LOOP;
		i := 0;
		ret := 0;
		WHILE i < (array_length(a,1)) LOOP
			val := position(a[i+1] IN chars)-1;
			ret := ret + (val * (36::numeric ^ i));
			i := i + 1;
		END LOOP;

		RETURN ret;

END;
$$ LANGUAGE 'plpgsql' IMMUTABLE;


CREATE OR REPLACE FUNCTION uuidToCuid(IN id uuid)
  RETURNS text AS $$
    declare
        hi bigint;
        lo bigint;
    begin
        hi := ('x' || translate(left(id::text, 18), '-', ''))::bit(64)::bigint;
        lo := ('x' || translate(right(id::text, 18), '-', ''))::bit(64)::bigint;
    return 'c' || base36_encode(hi, 12) || base36_encode(lo, 12);
END;
$$ LANGUAGE 'plpgsql' IMMUTABLE;

CREATE OR REPLACE FUNCTION cuidToNumeric(IN cuid text)
  RETURNS numeric AS $$
    declare
        timestamp numeric;
        counter numeric;
        fingerprint numeric;
        random numeric;
    begin
        timestamp := base36_decode(substring(cuid, 2, 8));
        counter := base36_decode(substring(cuid, 10, 4)) / 10::numeric ^ (7 * 2);
        fingerprint := base36_decode(substring(cuid, 14, 4)) / 10::numeric ^ 7;
        random := base36_decode(substring(cuid, 18, 8)) / 10::numeric ^ (7 * 2 + 13);
    return timestamp + counter + fingerprint + random;
END;
$$ LANGUAGE 'plpgsql' IMMUTABLE;


update edge set data = data || json_build_object('ordering', (data->>'ordering')::numeric + (cuidToNumeric(uuidToCuid(targetid)) / 10::numeric ^ 13))::jsonb where data->>'type' = 'Child' and data->>'ordering' is NOT NULL;
update edge set data = data || json_build_object('ordering', cuidToNumeric(uuidToCuid(targetid)))::jsonb where data->>'type' = 'Child' and data->>'ordering' is NULL;


drop function base36_encode;
drop function base36_decode;
drop function uuidToCuid;
drop function cuidToNumeric;
