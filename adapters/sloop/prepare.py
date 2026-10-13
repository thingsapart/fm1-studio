#!/usr/bin/env python3
"""Generate assets and a checked Android source overlay; never modify upstream files."""
from pathlib import Path
import shutil
import subprocess
import sys

PORT = Path(__file__).resolve().parent
ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else PORT.parents[1]
OUT = PORT / 'build'
GEN = OUT / 'gen'
GEN.mkdir(parents=True, exist_ok=True)
for script, header in [('font', 'felucca_font'), ('icons', 'felucca_icons'),
                       ('tables', 'felucca_tables'), ('samples', 'felucca_samples'),
                       ('drumkits', 'felucca_drumkits'), ('fm6_patches', 'felucca_fm6'),
                       ('logo', 'sloop_logo')]:
    subprocess.run([sys.executable, str(ROOT / 'tools' / f'gen_{script}.py'),
                    str(GEN / f'{header}.h')], cwd=ROOT, check=True)
shutil.copytree(ROOT / 'firmware', OUT / 'firmware', dirs_exist_ok=True)
# Encode user sample slot offsets, rather than subtracting 64-bit pointers into uint32.
replacements = {
    'eng_sample.c': [
        ('base = (uint32_t)(uintptr_t)(smp_user_xip(k) + SMP_USER_DATA) - (uint32_t)(uintptr_t)SMP_DATA;',
         'base = 0x80000000u | (k << 24);'),
        ('SMP_DATA[z->off + (pos >> 1)]', 'android_sample_byte(z->off + (pos >> 1))')],
    'eng_grain.c': [('SMP_DATA[z->off + (pos >> 1)]', 'android_sample_byte(z->off + (pos >> 1))')],
    'eng_slice.c': [('SMP_DATA[g->off + (rel >> 1)]', 'android_sample_byte(g->off + (rel >> 1))')],
}
for name, edits in replacements.items():
    path = OUT / 'firmware' / 'src' / name
    body = path.read_text()
    for old, new in edits:
        if body.count(old) != 1:
            raise SystemExit(f'{name}: upstream changed; expected exactly one occurrence of {old!r}')
        body = body.replace(old, new)
    path.write_text(body)
print(f'Android overlay generated from {ROOT}; upstream sources untouched')
