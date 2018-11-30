CREATE TABLE userdetail (
    userid uuid PRIMARY KEY REFERENCES node ON DELETE CASCADE,
    email text,
    verified boolean NOT NULL
);

CREATE UNIQUE INDEX unique_user_email ON userdetail (email) WHERE email IS NOT NULL;

insert into userdetail(userid, verified) (select node.id as userid, false as verified from node where node.data->>'type' = 'User' and (node.data->>'isImplicit')::boolean = false);
