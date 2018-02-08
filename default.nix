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
    export ANDROID_HOME=~/Android/Sdk
    cp -a /nix/store/6k288pvgs447878qnpiad87mwqidk6bs-android-sdk-25.2.5/bin/* ~/Android/Sdk/build-tools/27.0.3
    echo --- Welcome to woost! ---
    echo "Make sure you have the docker service running and added your user to the group 'docker'."
    echo Now run ./start sbt dev
    echo Then point your browser to http://localhost:12345
    '';
  }
