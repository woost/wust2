{ }:

let
  pkgs = import <nixpkgs> { };
in
  pkgs.stdenv.mkDerivation {
    name = "Woost";
    buildInputs = with pkgs; [
      sbt
      docker docker_compose
      nodejs yarn
      phantomjs
    ];

    installPhase= ''
    '';

    # TODO: how to use installPhase to install dependencies only once?
    shellHook=''
    .travis/install
    npm list jsdom || npm install jsdom
    echo --- Welcome to woost! ---
    echo now run ./start dev dev
    echo and open browser at http://localhost:12345/workbench/index.html
    '';
  }
