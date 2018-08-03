-- Many to one mappings are possible and necessary to imitate linking possibilities of woost in slack

CREATE TABLE team_mapping (
    slack_team_id text NOT NULL,
    wust_id uuid NOT NULL,
    PRIMARY KEY(slack_team_id, wust_id)
);

-- For all nodes in wust, an author is necessary (user). When we receive events like for the creation of a message,
-- an implicit user is created. This user however is not registered in wust and therefore it is not possible to issue
-- an oauth token for slack. Hence, the slack_token field is nullable
CREATE TABLE user_mapping (
    slack_user_id text NOT NULL,
    wust_id uuid NOT NULL,
    slack_token text, -- Users that are only in slack
    wust_token text NOT NULL,
    PRIMARY KEY(slack_user_id, wust_id)
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
