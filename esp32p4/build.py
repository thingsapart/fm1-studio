#!/usr/bin/env python3
"""Prepare all engines, generate the board host, build and check actual ELF size."""
from pathlib import Path
import argparse
import os
import subprocess
import sys

PROJECT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(PROJECT))
sys.path.insert(0,str(PROJECT/'tools'))
from build_lock import acquire
from dependencies import check_dependencies, setup_commands
from firmwares import FIRMWARES

def run(args):subprocess.run([str(a) for a in args],check=True)
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--firmwares',default='sloop,move,felucca,sloopdx')
    parser.add_argument('--pio',default=os.environ.get('PIO','pio'))
    parser.add_argument('--cc',default=os.environ.get('CC'),help='RISC-V compiler used to discover engine symbols')
    parser.add_argument('--nm',default=os.environ.get('NM'),help='Matching symbol inspector')
    parser.add_argument('--setup',action='store_true',help='Print installation commands for this host; do not build')
    parser.add_argument('--check-deps',action='store_true',help='Check prerequisites without building')
    args=parser.parse_args()
    if args.setup:
        print(setup_commands('esp32p4'));return
    names=args.firmwares.split(',')
    if not names or len(names)!=len(set(names)) or any(n not in {f.id for f in FIRMWARES} for n in names):
        parser.error('Select distinct ids from: '+', '.join(f.id for f in FIRMWARES))
    core=Path(os.environ.get('PLATFORMIO_CORE_DIR',Path.home()/'.platformio'))
    toolchain=core/'packages/toolchain-riscv32-esp/bin'
    cc=args.cc or (str(toolchain/'riscv32-esp-elf-gcc') if (toolchain/'riscv32-esp-elf-gcc').exists() else 'cc')
    nm=args.nm or (str(toolchain/'riscv32-esp-elf-nm') if (toolchain/'riscv32-esp-elf-nm').exists() else 'nm')
    check_dependencies('esp32p4',[next(f for f in FIRMWARES if f.id==n) for n in names],pio=args.pio,cc=cc,nm=nm)
    if args.check_deps:return
    # Child generators own the build lock. Hold it again during PIO so no
    # concurrent preparation can replace sources the compiler is reading.
    run([sys.executable,PROJECT/'tools/prepare_p4.py','--firmwares',args.firmwares,'--cc',cc,'--nm',nm])
    run([sys.executable,PROJECT/'tools/make_p4_board.py'])
    acquire()
    run([args.pio,'run','-d',PROJECT/'build/p4-board'])
    run([sys.executable,PROJECT/'tools/p4_size.py'])

if __name__=='__main__':main()
