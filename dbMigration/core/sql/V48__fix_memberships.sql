update edge set data = data || json_build_object('level', 'readwrite')::jsonb where data->>'type' = 'Member';
