ALTER TABLE _incidence DROP CONSTRAINT _incidence_sourceid_targetid_key;

CREATE INDEX on _incidence(sourceId);
CREATE INDEX on _incidence(targetId);

