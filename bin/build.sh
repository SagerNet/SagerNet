#!/bin/bash

source "bin/init/env.sh"

rm -rf app/build/outputs
./gradlew --stop
./gradlew :app:assembleExpertRelease
