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
      androidsdk
    ];

    installPhase= ''
    '';

    shellHook=''
    echo --- Welcome to woost! ---
    echo "Make sure you have the docker service running and added your user to the group 'docker'."
    echo Now run ./start sbt dev
    echo Then point your browser to http://localhost:12345

    mkdir -p ~/.android/sbt/sdk/{platform-tools,build-tools/27.0.3}/
    cp -a ${pkgs.androidsdk}/bin/* ~/.android/sbt/sdk/platform-tools/
    cp -a ${pkgs.androidsdk}/bin/* ~/.android/sbt/sdk/build-tools/27.0.3/
    '';
  }
