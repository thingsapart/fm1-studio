"""Wrap the existing, tested SLOOP native bridge as a portable engine plugin."""
import re
import shutil
import subprocess
import sys
from pathlib import Path

BRIDGE=Path(__file__).resolve().parents[2]/'adapters/sloop'

def verify(root):
    panel=(root/'firmware/src/panel.c').read_text()
    match=re.search(r'enum\s*\{\s*(B_FX.*?)\}',panel,re.S)
    expected=['B_FX','B_SCL','B_ENV','B_LFO','B_EDIT','B_GLO','B_HOME','B_SAVE',
              'B_ARP','B_SEQ','B_PLAY','B_REC','B_OCTDN','B_OCTUP','NB']
    if not match or [s.strip() for s in match[1].split(',')]!=expected:
        raise RuntimeError('SLOOP physical button ABI changed; review firmwares/sloop.py')

def stage(root,out,definition):
    bridge=BRIDGE;native=out/'native'
    if native.exists():shutil.rmtree(native)
    shutil.copytree(bridge/'native',native)
    shutil.copy2(bridge/'prepare.py',out/'prepare.py')
    subprocess.run([sys.executable,str(out/'prepare.py'),str(out/'source')],check=True)
    (out/'plugin.c').write_text('''/* Generated SLOOP ABI adapter; platform-independent. */
#include "firmware.h"
#include "native/core.c"
static int wrapper_checkpoint(void){sloop_checkpoint();return 1;}
static void wrapper_event(int kind,int id,int value){if(kind>=FM1_BUTTON&&kind<=FM1_STOP)sloop_event(kind,id,value);else if(kind>=72&&kind<=91)sloop_event(kind-64,id,value);}
static const fm1_firmware api = {
    FM1_ABI, sizeof(fm1_firmware), "sloop", 44100, 240, 240, SLOOP_STORAGE_SIZE,
    sloop_init, sloop_render, wrapper_event, sloop_tick, sloop_pixels, sloop_playing,
    wrapper_checkpoint, sloop_storage, sloop_storage_dirty, sloop_storage_clean, sloop_take_midi
};
__attribute__((visibility("default"))) const fm1_firmware *fm1_get_api(void) { return &api; }
static const struct fm1_studio_api studio_api={FM1_STUDIO_ABI,sizeof(struct fm1_studio_api),sloop_editor_snapshot,sloop_object_import,sloop_object_export,sloop_import_sample,sloop_validate_image};
__attribute__((visibility("default"))) const struct fm1_studio_api *fm1_get_studio_api(void){return &studio_api;}
'''.replace('"sloop", 44100', '"'+definition.id+'", 44100'))
