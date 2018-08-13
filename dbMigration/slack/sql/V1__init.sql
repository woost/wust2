-- Many to one mappings are possible and necessary to imitate linking possibilities of woost in slack

-- For all nodes in wust, an author is necessary (user). When we receive events like for the creation of a message,
-- an implicit user is created. This user however is not registered in wust and therefore it is not possible to issue
-- an oauth token for slack. Hence, the slack_token field is nullable
CREATE TABLE user_mapping (
    slack_user_id text NOT NULL,
    wust_id uuid NOT NULL,
    slack_token text, -- Users that are only in slack
    wust_token text NOT NULL,
    PRIMARY KEY(slack_user_id) --TODO: prevents same user on different wust_ids. previously: (slack_user_id, wust_id).
);
CREATE INDEX wust_user_id ON user_mapping USING btree(wust_id);

-- NOT NULL values: In order to be able to persist before sending a request, the slack ids should be nullable
 -- channels == teams
CREATE TABLE team_mapping (
    slack_team_id text,
    slack_team_name text UNIQUE NOT NULL,
    slack_deleted_flag boolean NOT NULL,
    wust_id uuid NOT NULL,
    PRIMARY KEY(wust_id)
);
create unique index on team_mapping using btree(slack_team_id, wust_id);

CREATE TABLE conversation_mapping (
    slack_conversation_id text,
    wust_id uuid NOT NULL,
    PRIMARY KEY(wust_id)
);
create unique index on conversation_mapping using btree(slack_conversation_id, wust_id);

CREATE TABLE message_mapping (
    slack_channel_id text,
    slack_message_ts text,
    slack_deleted_flag boolean NOT NULL,
    slack_message_text text NOT NULL,
    wust_id uuid NOT NULL,
    PRIMARY KEY(wust_id)
);
create unique index on message_mapping using btree(slack_channel_id, slack_message_ts, wust_id);
