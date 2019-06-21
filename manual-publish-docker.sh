#!/usr/bin/env bash

set -e

# build
export OVERRIDE_BRANCH=$version
sbt "clean; core/docker; dbMigration/docker; webApp/fullOptJS::webpack"

ci/publish-docker $1
