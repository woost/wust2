#!/bin/sh

echo -n "waiting for postgres to be up..."
#TODO without nc: (echo >/dev/tcp/postgres/5432) > /dev/null 2>&1 && echo connected
while [ -z "`nc -z postgres 5432 && echo connected`" ]; do
    sleep 5
done

echo "done"

flyway $@
