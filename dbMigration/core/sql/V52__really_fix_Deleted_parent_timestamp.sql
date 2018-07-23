create or replace function is_timestamp(s varchar) returns boolean as $$
begin
  perform s::timestamp without time zone;
  return true;
exception when others then
  return false;
end;
$$ language plpgsql;

update edge set data = data || jsonb_build_object('timestamp', (extract(epoch from (data->>'timestamp')::timestamp without time zone) * 1000)::bigint) where data->>'type' = 'DeletedParent' and is_timestamp((data->>'timestamp'));

drop function is_timestamp;
