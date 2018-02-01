#!/usr/bin/env zsh

LOCALDIR=${LOCALDIR:-$(pwd)}
REMOTEHOST=${REMOTEHOST:-fff}

DEVPORT=$(shuf -i 40000-41000 -n 1)
BACKEND=$(shuf -i 50000-51000 -n 1)
REMOTETMP=$(mktemp)

rsync -aP --delete $LOCALDIR/ fff:$REMOTETMP/ --exclude-from=$LOCALDIR/.ignore || exit 1

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

ssh -tL 12345:localhost:${DEVPORT} ${REMOTEHOST} "mkdir -p $REMOTETMP; cd $REMOTETMP; nix-shell --run \"WUST_BACKEND_PORT=$BACKEND WUST_DEVSERVER_PORT=$DEVPORT WUST_DEVSERVER_COMPRESS=true ./start sbt\""

ssh ${REMOTEHOST} "rm -rf $REMOTETMP"

kill $LSYNCDPID
