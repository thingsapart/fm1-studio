"""Read-only source copies, checked overlays and native emulator adaptation."""
import re
import shutil
import subprocess
import sys

def replace(body,old,new,count=1):
    if body.count(old)!=count:raise RuntimeError(f'Upstream overlay mismatch: expected {count} occurrence(s) of {old!r}')
    return body.replace(old,new)

def assets(out,scripts):
    source=out/'source';gen=out/'build/gen';gen.mkdir(parents=True,exist_ok=True)
    for script,header,*args in scripts:
        subprocess.run([sys.executable,str(source/'tools'/f'gen_{script}.py'),str(gen/header),*args],cwd=source,check=True)

def firmware(out):
    target=out/'build/firmware'
    if target.exists():shutil.rmtree(target)
    shutil.copytree(out/'source/firmware',target)
    native=out/'native'
    if native.exists():shutil.rmtree(native)
    native.mkdir()
    for filename in ('eng_sample.c','eng_grain.c','eng_slice.c'):
        p=target/'src'/filename;body=p.read_text()
        if filename=='eng_sample.c':
            body=replace(body,'base = (uint32_t)(uintptr_t)(smp_user_xip(k) + SMP_USER_DATA) - (uint32_t)(uintptr_t)SMP_DATA;',
                         'base = 0x80000000u | (k << 24);')
        if filename=='eng_slice.c':body=replace(body,'SMP_DATA[g->off + (rel >> 1)]','wrapper_sample_byte(g->off + (rel >> 1))')
        else:body=replace(body,'SMP_DATA[z->off + (pos >> 1)]','wrapper_sample_byte(z->off + (pos >> 1))')
        p.write_text(body)
    return native

def includes(body):
    return re.sub(r'#include "(?:\.\./)+firmware/src/([^"\n]+)"',r'#include "\1"',body).replace(
        '"../../firmware/hal/fm1_led_anim.h"','"../build/firmware/hal/fm1_led_anim.h"')

SAMPLE_HELPER='''
static uint8_t wrapper_sample_byte(uint32_t off) {
    if (!(off & 0x80000000u)) return off < sizeof SMP_DATA ? SMP_DATA[off] : 0;
    unsigned slot=(off>>24)&3u, pos=off&0xffffffu;
    if(slot>=SMP_USER_SLOTS || pos>=SMP_USER_SIZE-SMP_USER_DATA) return 0;
    return SMP_USER_XIP(slot)[SMP_USER_DATA+pos];
}
'''

def api(id):
    return '''
static const fm1_firmware api = {
    FM1_ABI, sizeof(fm1_firmware), "%s", FS, 240, 240, sizeof nor,
    wrapper_init, wrapper_render, wrapper_event, wrapper_tick, wrapper_pixels,
    wrapper_playing, wrapper_checkpoint, wrapper_storage, wrapper_dirty, wrapper_clean, wrapper_midi
};
__attribute__((visibility("default"))) const fm1_firmware *fm1_get_api(void) { return &api; }
''' % id
