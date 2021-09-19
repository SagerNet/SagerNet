#!/bin/bash

source "bin/init/env.sh"

./gradlew :app:lint
E=$?
cat app/build/lint.txt
exit $E