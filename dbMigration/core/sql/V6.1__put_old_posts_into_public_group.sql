INSERT INTO ownership (
    SELECT post.id,1
    FROM post
    LEFT OUTER JOIN ownership ON post.id = ownership.postid
    WHERE ownership.groupid IS NULL
);
