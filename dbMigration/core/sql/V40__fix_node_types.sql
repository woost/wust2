update node set data = data || jsonb '{"type":"PlainText"}' where data->>'type' = 'Text'
