update edge set data = '{"type": "Expanded"}' where data->>'type' = 'StaticParentIn';
