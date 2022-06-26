#!/usr/bin/env bash

bin/plugin/mieru/init.sh &&
  bin/plugin/mieru/armeabi-v7a.sh &&
  bin/plugin/mieru/arm64-v8a.sh &&
  bin/plugin/mieru/x86.sh &&
  bin/plugin/mieru/x86_64.sh &&
  bin/plugin/mieru/end.sh
