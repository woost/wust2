update edge set data = json_build_object('isCreate', false, 'isRename', false)::jsonb where data->>'type' = 'ReferencesTemplate';
