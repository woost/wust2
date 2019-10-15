DROP index edge_unique_index;
CREATE UNIQUE index edge_unique_index
    ON edge
    USING btree(sourceId, (data->>'type'), coalesce(data->>'key', ''), targetId) -- coalesce to empty string when key not present, because postgres does not index null values
    WHERE data->>'type' <> 'Author' and data->>'type' <> 'Remind';
