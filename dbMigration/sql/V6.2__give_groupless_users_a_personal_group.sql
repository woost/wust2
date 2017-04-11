with user_without_group AS (
    SELECT row_number() OVER () AS row, "user".id
    FROM "user"
    LEFT OUTER JOIN usergroupmember ON usergroupmember.userid = "user".id WHERE usergroupmember.groupid IS NULL
), new_group AS (
    INSERT INTO usergroup SELECT FROM user_without_group returning id
), new_group_row AS (
    SELECT row_number() OVER () AS row, id FROM new_group
)
INSERT INTO usergroupmember
SELECT new_group_row.id, user_without_group.id
FROM user_without_group
JOIN new_group_row ON user_without_group.row = new_group_row.row;
