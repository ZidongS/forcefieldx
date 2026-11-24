#!/bin/bash
# User-local Debian 13 Xvfb for FFX Java3D initialization; no root required.
set -euo pipefail
TOOLS=$(realpath "$(dirname "$0")/../../..")/.ffx-tools
mkdir -p "$TOOLS/xvfb-pkgs" "$TOOLS/xvfb/bin"
cd "$TOOLS/xvfb-pkgs"
apt-get download xvfb xserver-common x11-xkb-utils xauth libpixman-1-0 libxfont2 libfontenc1 libxkbfile1
for package in ./*.deb; do dpkg-deb -x "$package" "$TOOLS/xvfb"; done
python3 - "$TOOLS/xvfb" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
p = root / 'usr/bin/Xvfb'
b = p.read_bytes()
# Debian Xvfb hard-codes the XKB compiler directory. Relocate this prefix
# without changing ELF string offsets, then start it from usr/bin.
assert b.count(b'/usr/bin\0') == 1, 'Unexpected Xvfb binary; review relocation'
p.write_bytes(b.replace(b'/usr/bin\0', b'.' + b'\0' * 8))
wrapper = root / 'bin/Xvfb'
wrapper.write_text('#!/bin/bash\ncd "$(dirname "$(realpath "$0")")/../usr/bin"\nexec ./Xvfb "$@"\n')
wrapper.chmod(0o755)
PY
sha256sum ./*.deb
LD_LIBRARY_PATH="$TOOLS/xvfb/usr/lib/x86_64-linux-gnu:${LD_LIBRARY_PATH:-}" ldd "$TOOLS/xvfb/usr/bin/Xvfb"
