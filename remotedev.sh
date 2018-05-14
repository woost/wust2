#!/usr/bin/env zsh
SBTARG=${SBTARG:-"core"}
EXTRASBTARGS=${EXTRASBTARGS:-""}
LOCALDIR=${LOCALDIR:-$(pwd)}
REMOTEHOST=${REMOTEHOST:-"fff"}

DEVPORT=${DEVPORT:-$(shuf -i 40000-41000 -n 1)}
BACKEND=${BACKEND:-$(shuf -i 50000-51000 -n 1)}
REMOTETMP=$(mktemp)

rsync -aP --delete ${LOCALDIR}/ ${REMOTEHOST}:${REMOTETMP}/ --exclude-from=${LOCALDIR}/remotedevignore || exit 1

lsyncd =(cat <<EOF
settings {
   nodaemon     = true,
   statusFile   = "/dev/null",
   logfile      = "/dev/null"
}

sync {
   default.rsync,
   delay       = 1,
   source      = "${LOCALDIR}",
   target      = "${REMOTEHOST}:${REMOTETMP}",
   excludeFrom = "${LOCALDIR}/.ignore"
}
EOF
) &>/dev/null &

LSYNCDPID=$!
echo $LSYNCDPID

SBT_OPTS="-Xms512M -Xmx4G -Xss1M -XX:+CMSClassUnloadingEnabled -XX:+UseConcMarkSweepGC"

TERM=xterm-256color ssh -tC -L 12345:localhost:${DEVPORT} -L ${DEVPORT}:localhost:${DEVPORT} ${REMOTEHOST} "\
    cd $REMOTETMP;          \
    nix-shell production.nix --run \"zsh -ic \\\"              \
        if [[ -f tokens.sh ]]; then;            \
            source ./tokens.sh;                 \
        fi;                                     \
        if [[ -f .zsh_completion ]]; then;      \
            source .zsh_completion;             \
        fi;                                     \
        export WUST_CORE_PORT=$BACKEND;         \
        export WUST_PORT=$DEVPORT;              \
        export DEV_SERVER_COMPRESS=true;        \
        export SBT_OPTS='$SBT_OPTS';            \
        export EXTRASBTARGS=$EXTRASBTARGS;      \
        sbt dbMigration/docker;                 \
        ./start sbtWithPoll $SBTARG;            \
        zsh -i;                                 \
    \\\"\""

ssh ${REMOTEHOST} "rm -rf $REMOTETMP"

kill ${LSYNCDPID}
