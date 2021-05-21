#!/usr/bin/env bash

bin/plugin/relaybaton/init.sh &&
  bin/plugin/relaybaton/armeabi-v7a.sh &&
  bin/plugin/relaybaton/arm64-v8a.sh &&
  bin/plugin/relaybaton/x86.sh &&
  bin/plugin/relaybaton/x86_64.sh &&
  bin/plugin/relaybaton/end.sh
