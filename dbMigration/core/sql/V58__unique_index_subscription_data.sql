delete from webpushsubscription as s
    where exists(
        select * from webpushsubscription where endpointurl = s.endpointurl and p256dh = s.p256dh and auth = s.auth and id <> s.id
    );

CREATE UNIQUE INDEX on webpushsubscription(endpointurl, p256dh, auth);
