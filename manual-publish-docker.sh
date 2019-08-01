#!/usr/bin/env bash

set -e

sbt clean

ci/build-artifacts

ci/publish-docker $1
