#!/usr/bin/env python3
"""Build the Android typeface from the exact bold bitmap used by firmware.
Reuse the upstream converter as a library; do not run its web-editing main().
"""
import importlib.util
import sys
from pathlib import Path
output=Path(sys.argv[1]);root=Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('sloop_webfont', root / 'gen_webfont.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
font, count = module.build(root / 'assets/ter-u16b.bdf')
output.parent.mkdir(parents=True, exist_ok=True)
output.write_bytes(font)
print(f'Android Terminus Bold: {count} glyphs from the firmware BDF')
