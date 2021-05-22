#!/usr/bin/env bash

bin/plugin/brook/init.sh &&
  bin/plugin/brook/armeabi-v7a.sh &&
  bin/plugin/brook/arm64-v8a.sh &&
  bin/plugin/brook/x86.sh &&
  bin/plugin/brook/x86_64.sh &&
  bin/plugin/brook/end.sh
