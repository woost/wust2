#!/bin/bash

if [[ "$TRAVIS_REPO_SLUG" == "woost/wust2" && "$TRAVIS_PULL_REQUEST" == "false" && "$TRAVIS_BRANCH" == "master" ]]; then

    eval "$(ssh-agent -s)";
    openssl aes-256-cbc -K $encrypted_b3beb67678c9_key -iv $encrypted_b3beb67678c9_iv -in ./travis/travis-\>github.enc -out travis-\>github -d;
    chmod 600 $TRAVIS_BUILD_DIR/travis-\>github;
    ssh-add $TRAVIS_BUILD_DIR/travis-\>github;

    echo -e "Publishing scaladoc...\n"

    sbt ghpagesPushSite

    echo -e "Published scaladoc to gh-pages.\n"

fi
