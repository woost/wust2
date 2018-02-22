CREATE TABLE user_mapping (
    wustid VARCHAR(36) PRIMARY KEY,
    githubid INTEGER
);

CREATE TABLE issue_mapping (
    wustid VARCHAR(36) PRIMARY KEY,
    owner VARCHAR(39),
    repo VARCHAR(100),
    number INTEGER,
    githubid INTEGER
);

CREATE TABLE comment_mapping (
    wustid VARCHAR(36) PRIMARY KEY,
    owner VARCHAR(39),
    repo VARCHAR(100),
    issue_number INTEGER,
    githubid INTEGER
);
