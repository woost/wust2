#!/usr/bin/env zsh

# kill background processes on exit
trap 'kill $(jobs -p)' EXIT

# cancel on error
set -e  

LOCALDIR=${LOCALDIR:-$HOME/projects/wust2}
REMOTEHOST=${REMOTEHOST:-fff}

DEVPORT=$(shuf -i 40000-41000 -n 1)
BACKEND=$(shuf -i 50000-51000 -n 1)
REMOTETMP=$(mktemp)

rsync -aP --delete ~/projects/wust2/ fff:$REMOTETMP/ --exclude-from=$LOCALDIR/.ignore

set +e  

lsyncd =(cat <<EOF
settings {
   nodaemon   = true,
   logfile = "/dev/null"
}

sync {
   default.rsync,
   delay     = 1, 
   source = "$LOCALDIR",
   target = "$REMOTEHOST:$REMOTETMP",
   excludeFrom="$LOCALDIR/.ignore"
}
EOF
) &

LSYNCDPID=$!
echo $LSYNCDPID

ssh -L 12345:localhost:${DEVPORT} ${REMOTEHOST} "mkdir -p $REMOTETMP; cd $REMOTETMP; nix-shell --run \"WUST_BACKEND_PORT=$BACKEND WUST_DEVSERVER_PORT=$DEVPORT WUST_DEVSERVER_COMPRESS=true ./start sbt\""

kill $LSYNCDPID
