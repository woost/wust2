delete from edge where data->>'type' = 'Before';

CREATE UNIQUE index edge_unique_before_index
    ON edge
    USING btree(sourceId, (data->>'type'), (data->>'parent'), targetId)
    WHERE data->>'type' = 'Before';
