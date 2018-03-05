{ }:

let
  pkgs = import <nixpkgs> { };
in
  pkgs.stdenv.mkDerivation {
    name = "Woost";
    buildInputs = with pkgs; [
      scala
      sbt
      docker docker_compose
      ngrok # github app -> webhooks to localhost
      nodejs-8_x yarn
      phantomjs
      gnumake gcc # required for some weird npm things

      # Dev tools
      #jetbrains.idea-community
      pgadmin
      redis-dump
      visualvm
      travis
    ];

    installPhase= ''
    '';

    shellHook=''
    echo --- Welcome to woost! ---
    echo "Make sure you have the docker service running and added your user to the group 'docker'."
    echo Now run ./start sbt
    echo Then type devweb
    echo Then point your browser to http://localhost:12345
    #zsh -ic "                                   \
    #    if [[ -f tokens.sh ]]; then;            \
    #        source ./tokens.sh;                 \
    #    fi;                                     \
    #    if [[ -f .zsh_completion ]]; then;      \
    #        source ./.zsh_completion;           \
    #    fi;                                     \
    #    "
    '';
  }
