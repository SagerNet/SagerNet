#!/usr/bin/env bash

bin/plugin/tuic/init.sh &&
  bin/plugin/tuic/armeabi-v7a.sh &&
  bin/plugin/tuic/arm64-v8a.sh &&
  bin/plugin/tuic/x86.sh &&
  bin/plugin/tuic/x86_64.sh &&
  bin/plugin/tuic/end.sh
