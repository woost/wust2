CREATE TABLE stipecustomer (
    customerid text PRIMARY KEY,
    userid uuid not null references userdetail(userid),
    UNIQUE(userid)
);
