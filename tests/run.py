#!/usr/bin/env python3
"""Host tests exercise actual dynamic libraries and real SLOOP firmware."""
from pathlib import Path
import os
import subprocess
import sys
PROJECT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(PROJECT/'tools'))
from prepare import prepare
from definitions import java,c
from firmwares import SLOOP,FIRMWARES
from firmwares.schema import Firmware,Screen,Control,validate
from build_lock import acquire
acquire()

def run(args):subprocess.run([str(a) for a in args],check=True)
run([sys.executable,PROJECT/'tests/dependencies_test.py'])
out=PROJECT/'build/tests';out.mkdir(parents=True,exist_ok=True)
for p in out.glob('*.flash'):p.unlink()
cc=os.environ.get('CC','cc');cxx=os.environ.get('CXX','c++')
for name in ('fixture_a','fixture_b'):
    run([cc,'-shared','-fPIC','-I'+str(PROJECT/'native'),f'-DFIXTURE_ID="{name}"',PROJECT/'tests/fixture.c','-o',out/(name+'.so')])
for firmware in FIRMWARES:
    stage=prepare(firmware)
    test_source=stage/'test_plugin.c'
    sample_byte='android_sample_byte' if firmware.adapter=='sloop' else 'wrapper_sample_byte'
    extra='''
__attribute__((visibility("default"))) int fm1_test_bpm(void){return song.g[G_BPM];}
__attribute__((visibility("default"))) void fm1_test_set_bpm(int n){song.g[G_BPM]=n;}
__attribute__((visibility("default"))) int fm1_test_tracks(void){return NTRK;}
__attribute__((visibility("default"))) int fm1_test_user_sample(void){
    uint8_t *base=(uint8_t*)SMP_USER_XIP(0);uint8_t old[SMP_USER_DATA+64];memcpy(old,base,sizeof old);
    memset(base,0,sizeof old);smp_user_hdr_t *h=(smp_user_hdr_t*)base;
    h->magic=SMP_USER_MAGIC;h->version=1;h->nz=1;h->data_len=64;
    h->zone[0]=(smp_zone_t){.off=0,.n=128,.ls=0,.le=127,.rate=65536,.root16=960,.lo=0,.hi=127};
    memset(base+SMP_USER_DATA,0xA5,64);h->crc=st_crc32(base+SMP_USER_DATA,64);smp_user_scan(0);
    int ok=usr_nz[0]==1&&usr_zone[0][0].off==0x80000000u&&SAMPLE_BYTE(0x80000000u)==0xA5&&SAMPLE_BYTE(0x8000003fu)==0xA5;
    memcpy(base,old,sizeof old);smp_user_scan(0);return ok;
}
'''.replace('SAMPLE_BYTE',sample_byte)
    if firmware.id=='sloopdx':
        extra=extra[:extra.index('__attribute__((visibility("default"))) int fm1_test_user_sample')]
    if firmware.adapter=='move':extra+='__attribute__((visibility("default"))) int fm1_test_screen(void){return ctl.screen;}\n'
    test_source.write_text((stage/'plugin.c').read_text()+extra)
    run([cc,'-std=c11','-shared','-fPIC','-O2','-fwrapv','-ffp-contract=off','-Wno-int-to-pointer-cast',
        '-I'+str(PROJECT/'native'),'-I'+str(stage/'build/gen'),'-I'+str(stage/'build/firmware/src'),test_source,'-lm','-o',out/(firmware.id+'.so')])
run([cxx,'-std=c++17','-I'+str(PROJECT/'native'),PROJECT/'tests/session_test.cpp',PROJECT/'native/session.cpp','-o',out/'session_test'])
run([out/'session_test',out])
run([cxx,'-std=c++17','-I'+str(PROJECT/'native'),PROJECT/'tests/firmwares_test.cpp',PROJECT/'native/session.cpp','-o',out/'firmwares_test'])
run([out/'firmwares_test',out])
run([cxx,'-std=c++17','-I'+str(PROJECT/'native'),PROJECT/'tests/studio_test.cpp',PROJECT/'native/session.cpp','-o',out/'studio_test'])
run([out/'studio_test',out])
run([cxx,'-std=c++17','-I'+str(PROJECT/'native'),PROJECT/'tests/sloopdx_test.cpp',PROJECT/'native/session.cpp','-o',out/'sloopdx_test'])
run([out/'sloopdx_test',out])
import json
for firmware in FIRMWARES:
    snapshot=json.loads((out/('studio-'+firmware.id+'.json')).read_text())
    assert snapshot['version']==1 and snapshot['bpm']==137 and len(snapshot['detail'])==(128 if firmware.id=='sloopdx' else 64)
    assert len(snapshot['keyNotes'])==27 and all(-1<=n<=127 for n in snapshot['keyNotes'])
    assert len(snapshot['lanes'])>=firmware.studio.drum_lanes and len(snapshot['tracks'])>=len(firmware.studio.tracks)
    assert all('params' in group for group in snapshot['groups'])
    chord=json.loads((out/('chord-'+firmware.id+'.json')).read_text())
    undone=json.loads((out/('chord-undo-'+firmware.id+'.json')).read_text())
    assert chord['steps'][2][:4]==[57,60,64,67] and chord['detail'][2]['length']==2 and chord['detail'][3]['time']==1
    assert undone['steps'][2][0]==-1 and undone['detail'][3]['time']!=1 and undone['steps'][0][0]==60
    if firmware.id in ('sloop','move'):
        before=json.loads((out/('drum-before-'+firmware.id+'.json')).read_text())['synthKits']
        after=json.loads((out/('drum-after-'+firmware.id+'.json')).read_text())['synthKits']
        assert after[1]['sounds'][0][7]==17
        before[1]['sounds'][0][7]=17
        assert before==after, 'Drum sound edit leaked into another field, sound or kit'
        print('PASS',firmware.id,'individual drum sound isolation and audition')
    print('PASS',firmware.id,'published editor JSON, chord duration and complete undo')
java(FIRMWARES,out/'Definitions.java');c(FIRMWARES,out/'screens.h')
run(['javac','--release','8','-d',out/'classes',out/'Definitions.java'])
(out/'screens_test.c').write_text('#include "screens.h"\nint main(void){return fm1_sloop_screens[0].count!=21;}\n')
run([cc,'-std=c11','-pedantic-errors',out/'screens_test.c','-o',out/'screens_test']);run([out/'screens_test'])
for bad in (
    Firmware('../bad','Bad','','','', (Screen('X',(Control('invalid',3,0),)),)),
    Firmware('bad','Bad','','','', (Screen('X',(Control('invalid',0,14),)),)),
    Firmware('bad','Bad','','','', (Screen('X',(),((0,0,1),)),)),
):
    try:validate(bad);raise AssertionError('Invalid definition accepted')
    except ValueError:pass
print('PASS shared Java/C descriptors and invalid-mapping rejection')

run(['javac','--release','8','-d',out/'java-classes',*[PROJECT/'android/java/org/fm1/wrapper'/name for name in ('MidiFile.java','MidiParser.java','MidiEncoders.java','Jp1Controller.java','Jp1Pads.java','Jp1Rgb.java','Jp1Feedback.java','Jp1LedQueue.java','Jp1Status.java','ControllerValue.java','SampleCodec.java','TrackGeometry.java','ChordNotes.java','ChordPlacement.java','KnobGesture.java','KnobGeometry.java','PianoRollNotes.java','DrumSound.java')],out/'Definitions.java',*sorted((PROJECT/'tests/java').glob('*.java'))])
for test in sorted((PROJECT/'tests/java').glob('*Test.java')):
    run(['java','-cp',out/'java-classes','org.fm1.wrapper.'+test.stem,*([out/'sample-adpcm.bin'] if test.stem=='SampleCodecTest' else [])])
