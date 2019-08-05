update edge set data = data || json_build_object('enabled', true)::jsonb where data->>'type' = 'Notify';
