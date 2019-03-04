-- convert all message files to a message with file description as content and the files as attachment properties
with get_data as (
    select uuid_in(('0' || substring(overlay(overlay(md5(random()::text || ':' || clock_timestamp()::text) placing '4' from 12) placing to_hex(floor(random()*(11-8+1) + 8)::int)::text from 15) for 15) || '0' || substring(overlay(overlay(md5(random()::text || ':' || clock_timestamp()::text) placing '4' from 12) placing to_hex(floor(random()*(11-8+1) + 8)::int)::text from 15) from 16 for 15))::cstring) as new_id, id as id, data as data, accesslevel as accesslevel from node where data->>'type' = 'File' and role->>'type' <> 'Neutral'
), update_node as (
	update node set data = json_build_object('type', 'Markdown', 'content', get_data.data->>'description')::jsonb from get_data where get_data.id = node.id
), create_property as (
	insert into node (select new_id, data, accesslevel, json_build_object('type', 'Neutral')::jsonb, NULL from get_data)
), create_edge as (
	insert into edge (select id, new_id, json_build_object('type', 'LabeledProperty', 'key', 'Attachment')::jsonb from get_data)
)
insert into edge (select get_data.new_id as sourceid, edge.targetid, edge.data from edge join get_data on edge.sourceid = get_data.id and edge.data->>'type' = 'Author');

-- remove description from files
update node set data = node.data - 'description' where node.data->>'type' = 'File';
