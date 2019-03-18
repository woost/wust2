update edge set data = data || json_build_object('isExpanded', true)::jsonb where data->>'type' = 'Expanded';
