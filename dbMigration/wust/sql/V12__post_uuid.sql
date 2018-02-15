ALTER TABLE connection
    drop constraint connection_sourceid_fkey,
    drop constraint connection_targetid_fkey,
    drop constraint connection_pkey,
    drop constraint selfloop;
ALTER TABLE containment
    drop constraint containment_parentid_fkey,
    drop constraint containment_childid_fkey,
    drop constraint containment_pkey,
    drop constraint selfloop;
ALTER TABLE ownership
    drop constraint ownership_postid_fkey;

DROP SEQUENCE post_id_seq CASCADE;

ALTER TABLE post
    ALTER COLUMN id TYPE CHARACTER VARYING(36);
ALTER TABLE connection
    ALTER COLUMN sourceId TYPE CHARACTER VARYING(36),
    ALTER COLUMN targetId TYPE CHARACTER VARYING(36);
ALTER TABLE containment
    ALTER COLUMN parentId TYPE CHARACTER VARYING(36),
    ALTER COLUMN childId TYPE CHARACTER VARYING(36);
ALTER TABLE ownership
    ALTER COLUMN postId TYPE CHARACTER VARYING(36);

ALTER TABLE connection
    ADD constraint connection_sourceid_fkey foreign key (sourceId) references post (id) on delete cascade,
    ADD constraint connection_targetid_fkey foreign key (targetId) references post (id) on delete cascade,
    ADD constraint connection_pkey primary key (sourceId, targetId),
    ADD constraint selfloop check (sourceId <> targetId);
ALTER TABLE containment
    ADD constraint containment_parentid_fkey foreign key (parentId) references post (id) on delete cascade,
    ADD constraint containment_childid_fkey foreign key (childId) references post (id) on delete cascade,
    ADD constraint containment_pkey primary key (parentId, childId),
    ADD constraint selfloop check (parentId <> childId);
ALTER TABLE ownership
    ADD constraint ownership_postid_fkey foreign key (postId) references post (id) on delete cascade;
