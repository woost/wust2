update rawpost
    set (content) = (select (json_build_object('type', 'Markdown')::jsonb || (content->'Markdown')::jsonb))
    where content @> '{"Markdown":{}}'::jsonb;
update rawpost
    set (content) = (select (json_build_object('type', 'Text')::jsonb || (content->'Text')::jsonb))
    where content @> '{"Text":{}}'::jsonb;
update rawpost
    set (content) = (select (json_build_object('type', 'Link')::jsonb || (content->'Link')::jsonb))
    where content @> '{"Link":{}}'::jsonb;
update rawpost
    set (content) = (select (json_build_object('type', 'Channels')::jsonb || (content->'Channels')::jsonb))
    where content @> '{"Channels":{}}'::jsonb;

CREATE INDEX post_content_type ON rawpost USING btree((content->>'type'));
