from __future__ import absolute_import, print_function, unicode_literals

import os
import pipes
import subprocess
import sys

args = [
    os.environ["RUST_ANDROID_GRADLE_CC"],
    os.environ["RUST_ANDROID_GRADLE_CC_LINK_ARG"],
] + sys.argv[1:]


def update_in_place(arglist):
    # The `gcc` library is not included starting from NDK version 23.
    # Work around by using `unwind` replacement.
    ndk_major_version = os.environ["CARGO_NDK_MAJOR_VERSION"]
    if ndk_major_version.isdigit():
        if 23 <= int(ndk_major_version):
            for i, arg in enumerate(arglist):
                if arg.startswith("-lgcc"):
                    # This is one way to preserve line endings.
                    arglist[i] = "-lunwind" + arg[len("-lgcc") :]


update_in_place(args)

for arg in args:
    if arg.startswith("@"):
        fileargs = open(arg[1:], "r").read().splitlines(keepends=True)
        update_in_place(fileargs)
        open(arg[1:], "w").write("".join(fileargs))


# This only appears when the subprocess call fails, but it's helpful then.
printable_cmd = " ".join(pipes.quote(arg) for arg in args)
print(printable_cmd)

sys.exit(subprocess.call(args))
