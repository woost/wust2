#!/bin/sh

host="$1"
echo -n "waiting for postgres to be up..."
counter=0
while ! nc -z "$host" 5432; do
    counter=$((counter+1))
    if [[ $counter -gt 30 ]]; then
        echo "cannot connect to postgres on host '$host:5432'!"
        exit 1
    fi

    sleep 1
done

echo "connected to postgres!"

flyway -url="jdbc:postgresql://$host/$POSTGRES_DB" -schemas=public -user=$POSTGRES_USER -password=$POSTGRES_PASSWORD migrate
