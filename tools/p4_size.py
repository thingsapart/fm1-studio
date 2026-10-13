#!/usr/bin/env python3
"""Read all DRAM banks from the ELF; PlatformIO's headline omits DRAM1 BSS."""
from pathlib import Path
import json
import struct
import sys

root=Path(__file__).resolve().parents[1]
folder=Path(sys.argv[1]) if len(sys.argv)>1 else root/'build/p4-board/.pio/build/fm1_wrapper_p4'
elf=(folder/'firmware.elf').read_bytes()
if elf[:6]!=b'\x7fELF\x01\x01':raise SystemExit('Expected little-endian 32-bit ESP32-P4 ELF')
offset=struct.unpack_from('<I',elf,32)[0]
stride,count,strings=struct.unpack_from('<HHH',elf,46)
headers=[struct.unpack_from('<10I',elf,offset+i*stride) for i in range(count)]
names=elf[headers[strings][4]:headers[strings][4]+headers[strings][5]]
sections={names[h[0]:].split(b'\0',1)[0].decode():h[5] for h in headers}
dram=sum(sections.get(s,0) for s in ('.dram0.data','.dram1.data','.dram0.bss','.dram1.bss','.noinit'))
report={'image_bytes':(folder/'firmware.bin').stat().st_size,'internal_dram_static_bytes':dram,
        'external_bss_bytes':sections.get('.ext_ram.bss',0),'factory_partition_bytes':6*1024*1024,
        'excludes':'runtime heaps, stacks, IRAM code, TCM and RTC sections'}
if report['image_bytes']>report['factory_partition_bytes']:raise SystemExit('Firmware exceeds the 6 MiB factory partition')
(folder/'size.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
