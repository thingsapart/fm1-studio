#!/usr/bin/env python3
"""Checked firmware staging. All generated files live inside this project."""
import hashlib
import importlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

PROJECT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT))
from firmwares import FIRMWARES

def source(firmware):
    return Path(os.environ.get(firmware.source_env, PROJECT / firmware.default_source)).expanduser().resolve()

def prepare(firmware):
    from build_lock import acquire
    acquire()
    root = source(firmware)
    if not (root / 'firmware/src/core.h').is_file():
        raise RuntimeError(f'{firmware.name}: missing source checkout at {root}; set {firmware.source_env}')
    adapter = importlib.import_module('adapters.' + firmware.adapter)
    adapter.verify(root)
    out = PROJECT / 'build' / firmware.id
    out.mkdir(parents=True, exist_ok=True)
    # Stage the bridge and asset inputs so upstream generators cannot write into
    # the dependency checkout. Never copy SDKs, builds, signing keys or .git.
    for name in ('tools', 'assets', 'firmware'):
        dest = out / 'source' / name
        if dest.exists(): shutil.rmtree(dest)
        shutil.copytree(root / name, dest, ignore=shutil.ignore_patterns('__pycache__'))
    adapter.stage(root, out, firmware)
    native = out / 'native'
    provenance = {}
    try: provenance['source_commit'] = subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True,stderr=subprocess.DEVNULL).strip()
    except subprocess.CalledProcessError: provenance['source_commit'] = 'unversioned'
    for folder in (out / 'source', native):
        for path in sorted(folder.rglob('*')):
            if path.is_file() and '__pycache__' not in path.parts:
                provenance[str(path.relative_to(out))] = hashlib.sha256(path.read_bytes()).hexdigest()
    (out / 'inputs.json').write_text(json.dumps(provenance, indent=2) + '\n')
    return out

if __name__ == '__main__':
    names = sys.argv[1:] or ['sloop']
    for name in names:
        firmware = next((f for f in FIRMWARES if f.id == name), None)
        if firmware is None: raise SystemExit(f'Unknown firmware: {name}')
        try: print(prepare(firmware))
        except RuntimeError as error: raise SystemExit(str(error))
