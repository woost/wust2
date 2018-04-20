{ }:

let
  pkgs = import <nixpkgs> { };
in
  pkgs.stdenv.mkDerivation {
    name = "Woost";
    buildInputs = with pkgs; [
      git zsh
      scala sbt
      docker docker_compose
      ngrok # github app -> webhooks to localhost
      nodejs-9_x yarn
      phantomjs
      gnumake gcc # required for some weird npm things
      # Dev tools
      #jetbrains.idea-community
      pgadmin
      redis-dump
      visualvm
      travis
      # androidsdk
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

    # adb devices
    cat <<EOF
    Make soure your user is in the group 'adbusers' (configuration.nix):
    {
      ...
      programs.adb.enable = true;
      users.users.<your-user>.extraGroups = ["adbusers"];
    }
    Connect your android phone via usb, enable usb debugging and file transfer.
    Make sure that the android-sbt plugin sets android-home to ~/.android (if set to ~/Android, delete ~/Android)
    ./start sbt
    project androidApp
    ++2.11.12
    android:run
    If you get errors that android tool binaries cannot be executed, like:
    "E/adb: Cannot run program "~/.android/sbt/sdk/platform-tools/adb": error=2, No such file or directory"
    re-run nix-shell, since it overwrites these binaries from nix-store.
    If you get:
    "Package space.woost signatures do not match the previously installed version; ignoring"
    run: adb uninstall space.woost
    To log output only form the app, use:
    adb logcat | grep -F \$(adb shell ps | grep space.woost | cut -c10-15)
    EOF
    # mkdir -p ~/.android/sbt/sdk/{platform-tools,build-tools/27.0.3}/
    # cp -a ${pkgs.androidsdk}/bin/* ~/.android/sbt/sdk/platform-tools/
    # cp -a ${pkgs.androidsdk}/bin/* ~/.android/sbt/sdk/build-tools/27.0.3/
    '';
  }
