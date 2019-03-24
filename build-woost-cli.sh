#/bin/sh

[[ -n "$GRAAL_HOME" ]] && export PATH="$PATH:$GRAAL_HOME/bin"
sbt cli/graalvm-native-image:packageBin
