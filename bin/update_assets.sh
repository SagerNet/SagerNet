#!/usr/bin/env bash
source "bin/init/env.sh"

curl -L -o app/src/main/assets/geofile/geoip.dat "https://github.com/v2fly/geoip/raw/release/geoip.dat"
curl -L -o app/src/main/assets/geofile/geosite.dat "https://github.com/v2fly/domain-list-community/raw/release/dlc.dat"

curl -L -o v2ray-extra.zip https://github.com/v2fly/v2ray-core/releases/download/v4.37.3/v2ray-extra.zip
unzip -o v2ray-extra.zip -d app/src/main/assets
rm v2ray-extra.zip
