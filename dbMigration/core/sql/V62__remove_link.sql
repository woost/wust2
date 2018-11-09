update node set data = json_build_object('type', 'Markdown', 'content', data->>'url') where data->>'type' = 'Link';
