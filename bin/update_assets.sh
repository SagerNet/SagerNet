#!/usr/bin/env bash
source "bin/init/env.sh"

rm -rf app/src/main/assets/v2ray
mkdir -p app/src/main/assets/v2ray
curl -L -o app/src/main/assets/v2ray/geoip.dat "https://github.com/v2fly/geoip/raw/release/geoip.dat"
curl -L -o app/src/main/assets/v2ray/geosite.dat "https://github.com/v2fly/domain-list-community/raw/release/dlc.dat"

curl -L -o v2ray-extra.zip https://github.com/v2fly/v2ray-core/releases/download/v4.39.1/v2ray-extra.zip
unzip -o v2ray-extra.zip -d app/src/main/assets
rm v2ray-extra.zip
mv app/src/main/assets/browserforwarder/* app/src/main/assets/v2ray
rm -r app/src/main/assets/browserforwarder