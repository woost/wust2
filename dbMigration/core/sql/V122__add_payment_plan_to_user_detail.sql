ALTER TABLE "userdetail" ADD COLUMN plan jsonb DEFAULT json_build_object('type', 'Free');
