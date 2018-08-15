-- Rename team_mapping to channel_mapping including indexes
ALTER TABLE team_mapping RENAME TO channel_mapping;

ALTER TABLE channel_mapping
RENAME slack_team_id TO slack_channel_id,
RENAME slack_team_name TO slack_channel_name;

ALTER INDEX team_mapping_pkey RENAME TO channel_mapping_pkey;
ALTER INDEX team_mapping_slack_team_id_wust_id_idx RENAME TO channel_mapping_slack_channel_id_wust_id_idx;
ALTER INDEX team_mapping_slack_team_name_key RENAME TO channel_mapping_slack_channel_name_key;

-- Add new team mapping
CREATE TABLE team_mapping (
    slack_team_id text UNIQUE NOT NULL,
    slack_team_name text UNIQUE NOT NULL,
    wust_id uuid NOT NULL,
    PRIMARY KEY(wust_id)
);
create unique index on team_mapping using btree(slack_team_id, wust_id);
