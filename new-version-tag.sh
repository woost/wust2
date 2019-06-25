#!/usr/bin/env bash

BASE_VERSION=v0.1

git_head_commit="$(git rev-parse --short HEAD 2> /dev/null)"
if [ -z "$git_head_commit" ]; then
    echo "No HEAD commit, exiting."
    exit 1
fi

git_head_tag="$(git describe --tags --exact-match HEAD 2> /dev/null)"
if [ -n "$git_head_tag" ]; then
    echo "HEAD is already tagged, exiting."
    exit 1
fi

git_last_tag="$(git describe --tags --abbrev=0 2> /dev/null)"
if [ -z "$git_last_tag" ] || [ -z "$(echo v$git_last_tag | grep $BASE_VERSION)" ]; then
    new_tag="$BASE_VERSION.0"
else
    new_tag=$(echo $git_last_tag | awk -F. '{$NF = $NF + 1;} 1' | sed 's/ /./g')
fi

git tag "$new_tag"
echo "Git tag was set: $new_tag"
