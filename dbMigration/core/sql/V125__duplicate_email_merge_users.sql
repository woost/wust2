select mergefirstuserintosecond(olduser := '24825b02-7dec-b500-03f7-8deb12be08dd'::uuid, keepuser := '249bb71a-6549-140a-000e-189e57d1f903'::uuid)

-- see all duplicate users with same email but different case:
-- select * from (select lower(userdetail.email) as email, array_agg(userdetail.userid) as userids, bool_or(userdetail.verified) as verified from userdetail where userdetail.email is not null  group by lower(userdetail.email)) u where array_length(u.userids, 1) > 1
