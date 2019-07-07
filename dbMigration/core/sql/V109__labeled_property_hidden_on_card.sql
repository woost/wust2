update edge set data = data || json_build_object('showOnCard', false)::jsonb where data->>'type' = 'LabeledProperty';
