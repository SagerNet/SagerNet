source "bin/init/env.sh"
source "bin/plugin/wireguard/build.sh"

git reset HEAD --hard
git clean -fdx
