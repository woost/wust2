drop schema public cascade;
create schema public;

CREATE TABLE atom (
    id serial PRIMARY KEY
);

CREATE TABLE _post (
    id integer PRIMARY KEY REFERENCES atom ON DELETE CASCADE,
    title text NOT NULL
);

CREATE TABLE _connects (
    id integer PRIMARY KEY REFERENCES atom ON DELETE CASCADE
);

CREATE TABLE _contains (
    id integer PRIMARY KEY REFERENCES atom ON DELETE CASCADE
);

CREATE TABLE _incidence (
    id integer PRIMARY KEY REFERENCES atom ON DELETE CASCADE,
    sourceId integer NOT NULL REFERENCES atom ON DELETE CASCADE,
    targetId integer NOT NULL REFERENCES atom ON DELETE CASCADE,
    UNIQUE(sourceId, targetId)
);

/* post nodes */
create view post as select * from _post;

create or replace rule post_insert as on insert to post do instead
with ins as (
    insert into atom(id) values(DEFAULT) returning id
)
insert into _post (id, title) select id, NEW.title from ins returning id, title;

create or replace rule post_delete as on delete to post do instead delete from atom where id = OLD.id;

/* connects edges */
create view connects as select _connects.id, sourceId, targetId from _connects join _incidence on _connects.id = _incidence.id;

create or replace rule connects_insert as on insert to connects do instead
with ins as (
    insert into atom(id) values(DEFAULT) returning id
), ins2 as (
    insert into _connects (id) select id from ins
)
insert into _incidence(id, sourceId, targetId) select id, NEW.sourceId, NEW.targetId from ins returning id, sourceId, targetId;

create or replace rule conncets_delete as on delete to connects do instead delete from atom where id = OLD.id;

/* contains edges */
create view contains as select _contains.id, sourceId as parent, targetId as child from _contains join _incidence on _contains.id = _incidence.id;

create or replace rule contains_insert as on insert to contains do instead
with ins as (
    insert into atom(id) values(DEFAULT) returning id
), ins2 as (
    insert into _contains (id) select id from ins
)
insert into _incidence(id, sourceId, targetId) select id, NEW.parent, NEW.child from ins returning id, sourceId, targetId;

create or replace rule contains_delete as on delete to contains do instead delete from atom where id = OLD.id;
