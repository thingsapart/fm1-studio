#!/usr/bin/env python3
"""Stage statically linked engines and shared descriptors for an ESP-IDF host."""
import argparse
import json
from pathlib import Path
import re
import shutil
import os
import platform
import subprocess
from prepare import PROJECT, prepare
from definitions import c
from firmwares import FIRMWARES

def main():
    from build_lock import acquire
    acquire()
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--firmwares',default='sloop,move,felucca,sloopdx')
    parser.add_argument('--cc',default=os.environ.get('CC','cc'),help='Compiler for exported-symbol discovery')
    parser.add_argument('--nm',default=os.environ.get('NM','nm'),help='Matching nm for exported-symbol discovery')
    args=parser.parse_args();names=args.firmwares.split(',')
    if len(set(names))!=len(names) or any(n not in {f.id for f in FIRMWARES} for n in names):parser.error('Invalid firmware ids')
    selected=[next(f for f in FIRMWARES if f.id==n) for n in names]
    out=PROJECT/'build/p4';out.mkdir(parents=True,exist_ok=True)
    stages=[prepare(f) for f in selected]
    for f,stage in zip(selected,stages):
        target=out/f.id
        if target.exists():shutil.rmtree(target)
        # No need to copy upstream generator caches or the assets again.
        target.mkdir()
        for name in ('native','build'):
            shutil.copytree(stage/name,target/name)
        plugin=(stage/'plugin.c').read_text()
        for path in list((target/'build/firmware/src').glob('*'))+list((target/'native').glob('*')):
            if path.suffix in ('.c','.h'):
                body=path.read_text().replace('section(".pool")','section(".ext_ram.bss")').replace('section(".noinit")','section(".ext_ram.bss")')
                # These were in the FM-1's internal RAM because its pool was
                # full. Keep combined-engine buffers out of the P4's small SRAM.
                body=re.sub(r'(static\s+(?:track_t|int16_t|float)\s+(?:trk|rev_line|sim_pcm|ring)\[[^;\n]+\])\s*;',
                            r'\1 __attribute__((section(".ext_ram.bss")));',body)
                body=body.replace('static uint8_t android_flash[SLOOP_STORAGE_SIZE] __attribute__((aligned(16)));',
                                  'static uint8_t android_flash[SLOOP_STORAGE_SIZE] __attribute__((section(".ext_ram.bss"),aligned(16)));')
                body=body.replace('static uint8_t nor[0x100000];','static uint8_t nor[0x100000] __attribute__((section(".ext_ram.bss")));')
                for name in ('screen','sim_screen','web_fb'):
                    body=body.replace('static uint16_t '+name+'[240 * 240];',
                                      'static uint16_t '+name+'[240 * 240] __attribute__((section(".ext_ram.bss")));')
                path.write_text(body)
        (target/'plugin.c').write_text(plugin)
        # Discover definitions, including mutable globals in the upstream HAL
        # doubles. Header-only function renaming misses panel/settings/bootguard.
        probe=target/'symbols.o'
        subprocess.run([args.cc,'-std=c11','-O2','-fwrapv','-ffp-contract=off','-Wno-int-to-pointer-cast',
                        '-I'+str(PROJECT/'native'),'-I'+str(target/'build/gen'),
                        '-I'+str(target/'build/firmware/src'),'-c',str(target/'plugin.c'),'-o',str(probe)],check=True)
        mac=platform.system()=='Darwin' and Path(args.nm).name=='nm'
        flags=['-g','-U'] if mac else ['-g','--defined-only']
        symbols=subprocess.check_output([args.nm,*flags,str(probe)],text=True)
        names_to_map=set()
        for line in symbols.splitlines():
            if not line.strip():continue
            name=line.split()[-1]
            if mac and name.startswith('_'):name=name[1:]
            if re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*',name):names_to_map.add(name)
        if 'fm1_get_api' not in names_to_map:raise RuntimeError('Compiler/nm mismatch: cannot discover adapter symbols')
        rename='\n'.join(f'#define {name} '+(f'fm1_get_api_{f.id}' if name=='fm1_get_api' else f'fm1_{f.id}_{name}') for name in sorted(names_to_map))
        (target/f'plugin_{f.id}.c').write_text(rename+'\n'+plugin)
    c(selected,out/'screens.h')
    entries=[]
    header=['/* Generated engine registry. */']
    for f in selected:
        header.append(f'const fm1_firmware *fm1_get_api_{f.id}(void);')
        entries.append('{%s,%s,fm1_get_api_%s,fm1_%s_screens,%d}' %
                       (json.dumps(f.id),json.dumps(f.name),f.id,f.id,len(f.screens)))
    header.append('static const fm1_definition fm1_engines[] = {'+','.join(entries)+'};')
    (out/'engines.h').write_text('\n'.join(header)+'\n')
    q=lambda p:'"'+str(p).replace('\\','/')+'"'
    (out/'engines.cmake').write_text('set(FM1_ENGINE_IDS '+ ' '.join(names)+')\nset(FM1_ENGINE_SOURCES '+
        ' '.join(q(out/f.id/f'plugin_{f.id}.c') for f in selected)+')\n')
    print(f'ESP-IDF component staged at {out}. Board drivers and image qualification are supplied by the host.')

if __name__=='__main__':main()
