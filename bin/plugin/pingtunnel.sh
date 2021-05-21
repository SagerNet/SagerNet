#!/bin/bash

bin/plugin/pingtunnel/init.sh &&
  bin/plugin/pingtunnel/armeabi-v7a.sh &&
  bin/plugin/pingtunnel/arm64-v8a.sh &&
  bin/plugin/pingtunnel/x86.sh &&
  bin/plugin/pingtunnel/x86_64.sh &&
  bin/plugin/pingtunnel/end.sh
