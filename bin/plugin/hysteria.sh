#!/usr/bin/env bash

bin/plugin/hysteria/init.sh &&
  bin/plugin/hysteria/armeabi-v7a.sh &&
  bin/plugin/hysteria/arm64-v8a.sh &&
  bin/plugin/hysteria/x86.sh &&
  bin/plugin/hysteria/x86_64.sh &&
  bin/plugin/hysteria/end.sh
