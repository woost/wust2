-- Many to one mappings are possible and necessary to imitate linking possibilities of woost in slack

CREATE TABLE user_mapping (
    slack_id uuid PRIMARY KEY,
    wust_id uuid NULL,
    unique(slack_id, wust_id)
);

CREATE TABLE node_mapping (
    slack_id uuid PRIMARY KEY,
    wust_id uuid NULL,
    unique(slack_id, wust_id)
);

