#!/usr/bin/env bash

bin/plugin/xtls/init.sh &&
  bin/plugin/xtls/armeabi-v7a.sh &&
  bin/plugin/xtls/arm64-v8a.sh &&
  bin/plugin/xtls/x86.sh &&
  bin/plugin/xtls/x86_64.sh &&
  bin/plugin/xtls/end.sh
