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
      nodejs-8_x yarn
      phantomjs
      gnumake gcc # required for some weird npm things
    ];

    installPhase= ''
    '';

    # TODO: how to use installPhase to install dependencies only once?
    shellHook=''
    npm list jsdom || npm install jsdom
    echo --- Welcome to woost! ---
    echo "Make sure you have the docker service running and added your user to the group 'docker'."
    echo Now run ./start sbt dev
    echo Then point your browser to http://localhost:12345
    '';
  }
