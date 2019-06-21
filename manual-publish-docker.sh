#!/usr/bin/env bash

set -e

# build
sbt "clean; core/docker; dbMigration/docker; webApp/fullOptJS::webpack"

ci/publish-docker $1
