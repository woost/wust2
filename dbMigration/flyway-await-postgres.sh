#!/bin/sh

echo -n "waiting for postgres to be up..."
while [ -z "`nc -z postgres 5432 && echo connected`" ]; do
    sleep 1
done

echo "done"

flyway $@
