#!/usr/bin/env bash

HOST=$1
PORT=$2

echo -n "waiting for server $HOST:$PORT to be up..."
while [ -z "`nc -z $HOST $PORT && echo connected`" ]; do
    sleep 1
    echo -n "."
done

echo "done"
