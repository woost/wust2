update edge set data = data || '{"timestamp":"1970-01-01T00:00:00.000"}'::jsonb where data->>'type' = 'DeletedParent' and data->>'timestamp' = '1970-01-01T00:00:00';
