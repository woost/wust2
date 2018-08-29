-- A Group is just a private channel and there is no distinction possible in wust
ALTER TABLE channel_mapping add column is_private boolean NOT NULL;

-- Rename flags
ALTER TABLE channel_mapping rename column slack_deleted_flag to is_archived;
ALTER TABLE message_mapping rename column slack_deleted_flag to is_deleted;
