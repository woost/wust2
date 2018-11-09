DROP index connection_unique_index;
CREATE index edge_index on edge using btree(sourceId, targetId);

CREATE UNIQUE index edge_unique_index
    ON edge
    USING btree(sourceId, (data->>'type'), targetId)
    WHERE data->>'type' NOT IN('Author', 'Before');
