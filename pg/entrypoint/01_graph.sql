drop schema public cascade;
create schema public;

CREATE TABLE atoms (
    id serial PRIMARY KEY
);

CREATE TABLE _posts (
    id integer PRIMARY KEY REFERENCES atoms ON DELETE RESTRICT,
    title text NOT NULL
);

CREATE TABLE _connects (
    id integer PRIMARY KEY REFERENCES atoms ON DELETE RESTRICT
);

CREATE TABLE _contains (
    id integer PRIMARY KEY REFERENCES atoms ON DELETE RESTRICT
);

CREATE TABLE incidences (
    id integer PRIMARY KEY REFERENCES atoms ON DELETE RESTRICT,
    source integer NOT NULL REFERENCES atoms ON DELETE CASCADE,
    target integer NOT NULL REFERENCES atoms ON DELETE CASCADE,
    UNIQUE(source, target)
);

create view posts as select * from _posts;

create or replace rule posts_insert as on insert to posts do instead
with ins as (insert into atoms(id) values(DEFAULT) returning id)
insert into _posts (id, title) select id, NEW.title from ins;

create view connects as select _connects.id, source, target from _connects join incidences on _connects.id = incidences.id;

create or replace rule connects_insert as on insert to connects do instead
with ins as (insert into atoms(id) values(DEFAULT) returning id),
ins2 as (insert into _connects (id) select id from ins)
insert into incidences(id, source, target) select id, NEW.source, NEW.target from ins;

create view contains as select _contains.id, source, target from _contains join incidences on _contains.id = incidences.id;

create or replace rule contains_insert as on insert to contains do instead
with ins as (insert into atoms(id) values(DEFAULT) returning id),
ins2 as (insert into _contains (id) select id from ins)
insert into incidences(id, source, target) select id, NEW.source, NEW.target from ins;
