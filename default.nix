{ }:

let
  pkgs = import <nixpkgs> { };
in
  pkgs.stdenv.mkDerivation {
    name = "Woost";
    buildInputs = with pkgs; [
      awscli
      git zsh
      scala sbt
      docker
      # graalvm8 # broken -> segfaults webpack
      docker-compose
      # python37Packages.docker_compose
      # python27Packages.docker_compose
      # python27Packages.backports_ssl_match_hostname
      # ngrok # github app -> webhooks to localhost
      nodejs-12_x yarn
      phantomjs
      # Dev tools
      #jetbrains.idea-community
      # pgadmin -> does not compile in nixos-unstable
      pgmanage
      pgcli
      # redis-dump
      visualvm
    ];

    installPhase= ''
    '';

    shellHook=''
    echo --- Welcome to woost! ---
    echo "Make sure you have the docker service running and added your user to the group 'docker'."
    echo Now run ./start sbt
    echo In the sbt prompt type: dev
    echo Then point your browser to http://localhost:12345
    #zsh -ic "                                   \
    #    if [[ -f tokens.sh ]]; then;            \
    #        source ./tokens.sh;                 \
    #    fi;                                     \
    #    if [[ -f .zsh_completion ]]; then;      \
    #        source ./.zsh_completion;           \
    #    fi;                                     \
    #    "

    cat <<EOF
    Cli:
    You can build the cli with ./build-woost-cli.sh. Then run it with ./woost-cli
    Requirement for running ./woost-cli:
    # wget https://github.com/oracle/graal/releases/download/vm-1.0.0-rc14/graalvm-ce-1.0.0-rc14-linux-amd64.tar.gz
    # tar -xvf graalvm-ce-1.0.0-rc14-linux-amd64.tar.gz
    # export GRAAL_HOME=/path/to/graalvm-ce-1.0.0-rc14-linux-amd64
    EOF
    '';
  }
