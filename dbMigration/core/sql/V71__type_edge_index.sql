DROP index edge_index;
CREATE index edge_index on edge using btree(sourceId, (data->>'type'), targetId);
