#!/bin/bash

pushd library/core
git fetch origin main || exit 1
git reset origin/main --hard
popd

pushd external/Xray-core
git fetch origin main || exit 1
git reset origin/main --hard
popd

git add .