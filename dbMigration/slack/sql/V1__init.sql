-- Many to one mappings are possible and necessary to imitate linking possibilities of woost in slack

CREATE TABLE user_mapping (
    slack_id text NOT NULL,
    wust_id uuid NOT NULL,
    slack_token text NOT NULL,
    wust_token text NOT NULL,
    PRIMARY KEY(slack_id, wust_id)
);

 -- channels
CREATE TABLE conversation_mapping (
    slack_conversation_id text NOT NULL,
    wust_id uuid NOT NULL,
    PRIMARY KEY(slack_conversation_id, wust_id)
);

CREATE TABLE message_mapping (
    slack_channel_id text NOT NULL,
    slack_message_ts text NOT NULL,
    wust_id uuid NOT NULL,
    PRIMARY KEY(slack_channel_id, slack_message_ts, wust_id)
);
