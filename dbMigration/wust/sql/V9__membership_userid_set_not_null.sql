DELETE FROM membership where userid IS NULL;
ALTER TABLE membership ALTER COLUMN userid SET NOT NULL;
DELETE FROM usergroup WHERE id = 1;
