DELETE FROM connection WHERE sourceid = targetid;
ALTER TABLE connection ADD CONSTRAINT selfloop CHECK (sourceid != targetid);

DELETE FROM containment WHERE parentid = childid;
ALTER TABLE containment ADD CONSTRAINT selfloop CHECK (parentid != childid);
