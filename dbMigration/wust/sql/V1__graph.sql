CREATE TABLE atom (
    id serial PRIMARY KEY
);

CREATE TABLE _post (
    id integer PRIMARY KEY REFERENCES atom ON DELETE CASCADE,
    title text NOT NULL
);

create rule _post_delete as on delete to _post do delete from atom where id = OLD.id;

CREATE TABLE _connects (
    id integer PRIMARY KEY REFERENCES atom ON DELETE CASCADE
);

create rule _connects_delete as on delete to _connects do delete from atom where id = OLD.id;

CREATE TABLE _contains (
    id integer PRIMARY KEY REFERENCES atom ON DELETE CASCADE
);

create rule _contains_delete as on delete to _contains do delete from atom where id = OLD.id;

CREATE TABLE _incidence (
    id integer PRIMARY KEY REFERENCES atom ON DELETE CASCADE,
    sourceId integer NOT NULL REFERENCES atom ON DELETE CASCADE,
    targetId integer NOT NULL REFERENCES atom ON DELETE CASCADE,
    UNIQUE(sourceId, targetId)
);

create rule _incidence_delete as on delete to _incidence do delete from atom where id = OLD.id;

/* post nodes */
create view post as select * from _post;

create or replace rule post_insert as on insert to post do instead
with ins as (
    insert into atom(id) values(DEFAULT) returning id
)
insert into _post (id, title) select id, NEW.title from ins returning id, title;

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
create view contains as select _contains.id, sourceId as parentId, targetId as childId from _contains join _incidence on _contains.id = _incidence.id;

create or replace rule contains_insert as on insert to contains do instead
with ins as (
    insert into atom(id) values(DEFAULT) returning id
), ins2 as (
    insert into _contains (id) select id from ins
)
insert into _incidence(id, sourceId, targetId) select id, NEW.parentId, NEW.childId from ins returning id, sourceId, targetId;

create or replace rule contains_delete as on delete to contains do instead delete from atom where id = OLD.id;

create or replace function graph_component(start integer) returns setof integer as $$
declare
    queue Integer[] := array[start];
begin
    create temporary table visited (id integer NOT NULL) on commit drop;
    create unique index on visited (id);

    WHILE array_length(queue,1) > 0 LOOP
        insert into visited (select unnest(queue)) on conflict do nothing;
        queue := array(
            select targetId
            from (select unnest(queue) as id) as q
            join _incidence on sourceId = q.id
            left outer join visited on targetId = visited.id
            where visited.id is NULL
        );
    END LOOP;
    return query (select id from visited);
end;
$$ language plpgsql;
