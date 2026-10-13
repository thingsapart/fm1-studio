"""Wrap the real sloopDX DX7/FM drum firmware, preserving its native UI/storage."""
from pathlib import Path
import shutil
from .common import assets
from .sloop import verify as verify_panel

BRIDGE = Path(__file__).resolve().parents[2] / 'adapters/sloopdx'

def verify(root):
    verify_panel(root)
    for name in ('eng_dx7.c', 'dx7_core.c', 'ui_voice.c', 'project.c'):
        if not (root / 'firmware/src' / name).is_file():
            raise RuntimeError('Unsupported sloopDX source: ' + name)
    core = (root / 'firmware/src/core.h').read_text()
    if '#define NSTEP 128' not in core or '#define STEP_POOL 256u' not in core:
        raise RuntimeError('sloopDX step ABI changed; review its adapter')

def stage(root, out, definition):
    assets(out, [('font', 'felucca_font.h'), ('icons', 'felucca_icons.h'),
                 ('tables', 'felucca_tables.h'), ('logo', 'sloopdx_logo.h')])
    target = out / 'build/firmware'
    if target.exists(): shutil.rmtree(target)
    shutil.copytree(out / 'source/firmware', target)
    native = out / 'native'
    if native.exists(): shutil.rmtree(native)
    shutil.copytree(BRIDGE, native)
    shutil.copy2(BRIDGE.parent / 'sloop/native/editor.h', native / 'editor.h')
    editor = (BRIDGE.parent / 'sloop/native/editor.c').read_text()
    param = editor[editor.index('static void editor_param('):editor.index('void sloop_editor_snapshot(')]
    (native / 'parameter.c').write_text(param)
    (out / 'plugin.c').write_text('''/* SPDX-License-Identifier: GPL-3.0-only */
#include "firmware.h"
#include "native/core.c"
static void wrapper_event(int kind,int id,int value){
    if(kind>=FM1_BUTTON&&kind<=FM1_STOP)sloop_event(kind,id,value);
    else if(kind>=72&&kind<=91)sloop_event(kind-64,id,value);
}
static const fm1_firmware api={
    FM1_ABI,sizeof(fm1_firmware),"sloopdx",44100,240,240,SLOOP_STORAGE_SIZE,
    sloop_init,sloop_render,wrapper_event,sloop_tick,sloop_pixels,sloop_playing,
    sloop_checkpoint,sloop_storage,sloop_storage_dirty,sloop_storage_clean,sloop_take_midi
};
__attribute__((visibility("default"))) const fm1_firmware *fm1_get_api(void){return &api;}
static const struct fm1_studio_api studio_api={FM1_STUDIO_ABI,sizeof(struct fm1_studio_api),
    sloop_editor_snapshot,sloop_object_import,sloop_object_export,sloop_import_sample,sloop_validate_image};
__attribute__((visibility("default"))) const struct fm1_studio_api *fm1_get_studio_api(void){return &studio_api;}
''')
