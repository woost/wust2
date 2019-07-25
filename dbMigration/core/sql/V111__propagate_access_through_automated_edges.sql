-- we do not have Mention edges in the traversal or access because we do not know whether they point
-- to users or nodes. Our access management does not really handle user strictly. To be sure that users
-- cannot be accessed, we just ignore them right now, because the frontend does not need them.
drop view useredge;
create view useredge as
    select sourceid as source_nodeid, targetid as target_userid, data
    from edge
    where not(data->>'type' = any(
            array[ 'Child', 'LabeledProperty', 'DerivedFromTemplate', 'Automated', 'ReferencesTemplate' ]
    ));
drop view contentedge;
create view contentedge as
    select sourceid as source_nodeid, targetid as target_nodeid, data
    from edge
    where data->>'type' = any(
            array[ 'Child', 'LabeledProperty', 'DerivedFromTemplate', 'Automated', 'ReferencesTemplate' ]
    );
drop view accessedge;
create view accessedge as
    select sourceid as source_nodeid, targetid as target_nodeid, data
    from edge
    where data->>'type' = any(
            array[ 'Child', 'LabeledProperty', 'Automated' ]
    );
