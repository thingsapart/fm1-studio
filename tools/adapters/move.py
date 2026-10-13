"""Native port of MOVE's upstream simulator, using its real eight-track firmware."""
from .common import assets,firmware,includes,replace,SAMPLE_HELPER,api

def verify(root):
    for name in ('web/sim/fm1sim.c','firmware/src/ui_move_ctl.c','firmware/src/projfmt.c'):
        if not (root/name).is_file():raise RuntimeError('MOVE requires the fm1-move/dev branch: '+name)

def stage(root,out,definition):
    assets(out,[('tables','felucca_tables.h'),('samples','felucca_samples.h'),('drumkits','felucca_drumkits.h'),
                ('fm6_patches','felucca_fm6.h'),('dsfont','ds_fonts.h')])
    native=firmware(out)
    body=(root/'web/sim/fm1sim.c').read_text()
    body=replace(body,'#define __attribute__(x)','#ifdef __APPLE__\n#define __attribute__(x)\n#endif')
    body=replace(body,'#include "../../firmware/src/libc.c"','''#undef memcpy
#undef memset
#define memcpy wrapper_move_memcpy
#define memset wrapper_move_memset
#define memcmp wrapper_move_memcmp
#include "libc.c"
#undef memcpy
#undef memset
#undef memcmp
static uint8_t wrapper_sample_byte(uint32_t off);''')
    body=includes(body)
    body=replace(body,'#include \"core.h\"','static uint32_t studio_mutes[8];\n#include \"core.h\"')
    body=replace(body,'#include "engines.c"','#include "engines.c"\n'+SAMPLE_HELPER)
    (native/'emulator.c').write_text(body+'\n#undef __attribute__\n')
    from .studio_move import stage_studio
    stage_studio(out)
    seq=native.parent/'build/firmware/src/seq.c'
    text=seq.read_text()
    text=replace(text,'uint32_t l, m = dstep_mask(s) & ~skip & ~roll_lanes(t);','uint32_t l, m = dstep_mask(s) & ~skip & ~roll_lanes(t) & ~studio_mutes[trk_index(t)];')
    text=replace(text,'uint32_t m = dstep_mask(s) & ~roll_lanes(t);','uint32_t m = dstep_mask(s) & ~roll_lanes(t) & ~studio_mutes[trk_index(t)];')
    text=replace(text,'static void drum_input(track_t *t, uint32_t lane, uint32_t lvl, uint32_t rat, int rec)\n{','static void drum_input(track_t *t, uint32_t lane, uint32_t lvl, uint32_t rat, int rec)\n{\n    if(lane<DRUM_LANES&&(studio_mutes[trk_index(t)]&(1u<<lane)))return;')
    seq.write_text(text)
    (out/'plugin.c').write_text('''/* SPDX-License-Identifier: GPL-3.0-only */
#include "firmware.h"
#include <string.h>
#include <stdio.h>
#include "native/emulator.c"
static unsigned wrapper_pos=CTL;
static float wrapper_pcm[CTL*2];
static uint32_t wrapper_buttons,wrapper_keys;
#include "native/studio.c"
static void wrapper_init(const uint8_t *image) {
    sim_init(); if(image)memcpy(nor,image,sizeof nor);
    sim_boot(); panel_init(); sim_ui(0); sim_dirty=0;
    wrapper_pos=CTL;wrapper_buttons=wrapper_keys=0;
}
static void wrapper_render(float *out,unsigned n) {
    for(unsigned f=0;f<n;f++) {
        if(wrapper_pos==CTL) {
            android_perform_apply();sim_audio(CTL);for(unsigned i=0;i<CTL*2;i++)wrapper_pcm[i]=sim_pcm[i]/32768.0f;
            wrapper_pos=0;
        }
        out[2*f]=wrapper_pcm[2*wrapper_pos];out[2*f+1]=wrapper_pcm[2*wrapper_pos+1];wrapper_pos++;
    }
}
static void wrapper_event(int kind,int id,int value) {
    if(kind>=72&&kind<=91){editor_event(kind-64,id,value);return;}
    if(kind==FM1_BUTTON && id>=0 && id<NB) {
        if(value)wrapper_buttons|=1u<<id;else wrapper_buttons&=~(1u<<id);
        sim_set(wrapper_buttons,wrapper_keys);ui_input();
    } else if(kind==FM1_KEY && id>=0 && id<27) {
        if(value)wrapper_keys|=1u<<id;else wrapper_keys&=~(1u<<id);
        sim_set(wrapper_buttons,wrapper_keys);keyboard_block();ui_input();
    } else if(kind==FM1_ENCODER && id>=0 && id<NE) {sim_turn(id,clamp(value,-32,32));ui_input();}
    else if(kind==FM1_MIDI) {if(mi_w-mi_r<MQ)midi_in_q[mi_w++%MQ]=(uint32_t)value;else panic_req=(1u<<NTRK)-1u;}
    else if(kind==FM1_PANIC) {android_perform_reset();memset(studio_mutes,0,sizeof studio_mutes);wrapper_buttons=wrapper_keys=0;sim_set(0,0);mi_r=mi_w;panic_req=(1u<<NTRK)-1u;keyboard_block();ui_input();}
    else if(kind==FM1_MASTER)song.master_q12=clamp(value,0,4096);
    else if(kind==FM1_STOP)transport_req=2;
    else if(kind==FM1_CONNECTION)usb.config=usb.up=value!=0;
    else if(kind==FM1_SCREEN && id>=0 && id<SC_COUNT){
        ctl.screen=id;ctl.menu=id>=SC_PROJECT;ctl.hot=-1;ctl.arm=ACT_NONE;ctl.force=1;
        if(value>=256){unsigned page=value-256;ctl.page[id]=clamp(page,0,id==SC_SOUND?3:id==SC_SEQ||id==SC_GROOVE?1:0);song.seq_mode=0;}else song.seq_mode=id==SC_SEQ;ui.force=1;
    }
}
static void wrapper_tick(uint32_t ms){sim_ui(ms);}
static void wrapper_pixels(uint32_t *out) {
    for(unsigned i=0;i<240*240;i++){uint16_t p=sim_screen[i];out[i]=0xff000000u|(((p>>11)*255u/31)<<16)|((((p>>5)&63)*255u/63)<<8)|((p&31)*255u/31);}
}
static int wrapper_playing(void){return song.playing!=0;}
static int wrapper_checkpoint(void){settings_save();return fmp_save(FMP_AUTOSAVE)==0;}
static const uint8_t *wrapper_storage(void){return nor;}
static int wrapper_dirty(void){return sim_dirty!=0;}
static void wrapper_clean(void){sim_dirty=0;}
static unsigned wrapper_midi(uint32_t *out,unsigned max){unsigned n=0;while(n<max&&mo_r!=mo_w)out[n++]=midi_out_q[mo_r++%MQ];return n;}
'''+api(definition.id)+'''
static const struct fm1_studio_api studio_api={FM1_STUDIO_ABI,sizeof(struct fm1_studio_api),sloop_editor_snapshot,sloop_object_import,sloop_object_export,studio_sample,studio_validate};
__attribute__((visibility("default"))) const struct fm1_studio_api *fm1_get_studio_api(void){return &studio_api;}
''')
