#!/bin/sh

echo -n "waiting for postgres to be up..."
while ! nc -z postgres 5432; do
    sleep 1
done

echo "done"

flyway -url="jdbc:postgresql://postgres/$POSTGRES_DB" -schemas=public -user=$POSTGRES_USER -password=$POSTGRES_PASSWORD migrate
