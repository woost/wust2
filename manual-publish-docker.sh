#!/usr/bin/env bash

set -e

sbt clean

ci/build-docker

ci/publish-docker $1
