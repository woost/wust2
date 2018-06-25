create index on node((data->>'isImplicit')) where data->>'type' = 'User';
create index on node((data->>'name')) where data->>'type' = 'User';
