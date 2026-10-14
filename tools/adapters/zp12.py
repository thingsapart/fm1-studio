"""Port zp12's native simulator to an audio-driven host, without coroutines."""
from pathlib import Path
import shutil
import re
from .common import assets, replace, api

BRIDGE = Path(__file__).resolve().parents[2] / 'adapters/zp12'

def verify(root):
    for name in ('firmware/src/zp12.c', 'sim/sim.c', 'sim/hal/fm1_flash.h'):
        if not (root/name).is_file():
            raise RuntimeError('Unsupported zp12 source: '+name)

def stage(root, out, definition):
    # Only Terminus and the CC0 factory kit are consumed; no inherited icon atlas.
    assets(out, [('font', 'felucca_font.h'), ('kit', 'zp12_kit.h')])
    native = out/'native'
    if native.exists(): shutil.rmtree(native)
    shutil.copytree(root/'sim', native/'sim')
    target = out/'build/firmware'
    if target.exists(): shutil.rmtree(target)
    shutil.copytree(out/'source/firmware', target)
    body = (target/'src/zp12.c').read_text()
    body = replace(body, 'uint32_t i, prev_notes = 0,', 'static uint32_t i, prev_notes = 0,')
    body = replace(body, 'int32_t knob_avg = 512 * 16;', 'static int32_t knob_avg = 512 * 16;')
    # Keep main-loop locals across host calls. The device's blocking splash is
    # skipped; sound, input, drawing, saving and sequencing use upstream code.
    first = body.index('    {   /* the start (sp_ui.c ui_splash)')
    last = body.index('        uint32_t n = fm1_in.notes', first)
    state = body[body.index('    static uint32_t i, prev_notes'):body.index('    for (i = 0; i < KIT_NWAVE;')]
    body = body[:first] + '    ui.force = 1;\n}\n\n' + state + 'static void zp12_step(void) {\n' + body[last:]
    body = body.replace(state, '', 1)
    body = body.replace('static void zp12_main(void)', state + 'static void zp12_main(void)')
    body = body.replace('\n' + state + 'static void zp12_step(void)', '\nstatic void zp12_step(void)')
    body = replace(body, '    }\n}\n\nvoid fm1_cstart(void)', '}\n\nvoid fm1_cstart(void)')
    # Android has no power-on GPIO scan or USB rescue entry.
    body = replace(body, 'boot_mode == BOOT_RECOVERY || recovery_key()', 'boot_mode == BOOT_RECOVERY')
    body = replace(body, '    zp12_main();\n    for (;;)\n        fm1_service();', '    zp12_main();')
    (target/'src/zp12.c').write_text(body)
    sim = (root/'sim/sim.c').read_text()
    sim = replace(sim, '#include "../firmware/src/zp12.c"', '#include "../../build/firmware/src/zp12.c"')
    first = sim.index('/* ---- the main loop as a coroutine */')
    last = sim.index('static void sim_halt(uint32_t why)', first)
    sim = sim[:first] + '''static uint32_t booted, halted;
static void sim_yield(void) {}
static void sim_pass(void) { if (!booted) { booted=1; fm1_cstart(); } else zp12_step(); }
''' + sim[last:]
    # Keep host linker and ISR names private to this shared library.
    version = re.search(r'\*\*Status: ([0-9.]+)\*\*', (root/'README.md').read_text())
    if not version:raise RuntimeError('Unsupported zp12 source: missing release version')
    sim = '#define ZP12_VER "'+version[1]+'"\n' + sim
    sim = '#define SIM_NATIVE 1\n#define fm1_cstart zp12_cstart\n#define isr_timer5 zp12_timer5\n#define isr_alnk0 zp12_alnk0\n' + sim
    for symbol in ('sim_nil',): sim = sim.replace('uint32_t '+symbol+'[1];', 'static uint32_t '+symbol+'[1];')
    (native/'sim/sim.c').write_text(sim)
    p = native/'sim/hal/fm1_time.h'
    time = p.read_text().replace('        sim_yield();', '        sim_ticks += FM1_TICKS_PER_US;')
    p.write_text(time)
    plugin = '''/* SPDX-License-Identifier: GPL-3.0-only */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "firmware.h"
#include "studio.h"
#ifdef __APPLE__
#define __attribute__(x)
#endif
#undef memset
#undef memcpy
#include "native/sim/sim.c"
#undef __attribute__
static unsigned wrapper_saved_writes;
static void wrapper_init(const uint8_t *image) {
    flash_prep(); if(image) memcpy(sim_flash,image,sizeof sim_flash);
    if(booted){memset(sp_ch,0,sizeof sp_ch);sq.playing=sq.recording=sq.rec_arm=0;zp12_main();}
    else sim_init(SP_FS);
    wrapper_saved_writes=sim_flash_writes;
}
static void wrapper_render(float *out,unsigned n) {
    while(n){unsigned count=n>1024?1024:n;sim_render(count);
        for(unsigned i=0;i<count;i++){out[2*i]=out_l[i];out[2*i+1]=out_r[i];}
        out+=count*2;n-=count;}
}
static void wrapper_tick(uint32_t ms){(void)ms;}
static void wrapper_pixels(uint32_t *out){for(unsigned i=0;i<240*240;i++){unsigned p=sim_fb[i];out[i]=0xff000000u|((p>>11)*255/31<<16)|(((p>>5)&63)*255/63<<8)|((p&31)*255/31);}}
static int wrapper_playing(void){return sq.playing;}
static int wrapper_checkpoint(void){sq.playing=sq.recording=sq.rec_arm=0;return zs_save()==0;}
static const uint8_t *wrapper_storage(void){return sim_flash;}
static int wrapper_dirty(void){return sim_flash_writes!=wrapper_saved_writes;}
static void wrapper_clean(void){wrapper_saved_writes=sim_flash_writes;}
static unsigned wrapper_midi(uint32_t *out,unsigned max){(void)out;(void)max;return 0;}
'''
    # zp12's simulator HAL is resolved relative to its private source directory.
    for p in (native/'sim/hal').glob('*.h'):
        shutil.copy2(p, target/'src'/p.name)
    shutil.copy2(out/'build/gen/felucca_font.h', target/'src/felucca_font.h')
    (out/'plugin.c').write_text(plugin + (BRIDGE/'parameters.c').read_text() + (BRIDGE/'studio.c').read_text() + api(definition.id).replace('FS, 240, 240, sizeof nor', 'SP_FS, 240, 240, sizeof sim_flash'))
