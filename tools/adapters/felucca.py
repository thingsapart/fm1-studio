"""Native port of Felucca's upstream emulator, including its real audio ISR/UI."""
from pathlib import Path
from .common import assets,firmware,includes,replace,SAMPLE_HELPER,api

def verify(root):
    for name in ('web/emu/felucca_web.c','tests/hostsim.c','firmware/src/mod.c'):
        if not (root/name).is_file():raise RuntimeError('Unsupported Felucca source: '+name)

def stage(root,out,definition):
    import shutil
    (out/'source/web').mkdir(parents=True,exist_ok=True)
    shutil.copy2(root/'web/fukiai.ttf',out/'source/web/fukiai.ttf')
    assets(out,[('aa_font','ui_fonts.h','--preset','inter-tight'),('aa_icons','ui_icons.h'),
                ('aa_keycaps','ui_keycaps.h'),('ui_palettes','ui_palettes.h'),('tables','felucca_tables.h'),
                ('fm6_patches','felucca_fm6.h'),('samples','felucca_samples.h')])
    native=firmware(out)
    dsp=(root/'tests/hostsim.c').read_text().split('static void wav_hdr(')[0]
    dsp=replace(dsp,'#define __attribute__(x)','#ifdef __APPLE__\n#define __attribute__(x)\n#endif')
    dsp=includes(dsp)
    dsp=replace(dsp,'#define memset felucca_memset','#undef memset\n#undef memcpy\n#define memset felucca_memset')
    dsp=replace(dsp,'#include "engines.c"','#include "engines.c"\n'+SAMPLE_HELPER)
    dsp=replace(dsp,'#include "core.h"','static uint8_t wrapper_sample_byte(uint32_t off);\n#include "core.h"')
    dsp=dsp.replace('#include <sys/wait.h>','').replace('#include <unistd.h>','')
    (native/'dsp.c').write_text(dsp)
    body=(root/'web/emu/felucca_web.c').read_text()
    body=replace(body,'#include "../../tests/hostsim.c"','#include "dsp.c"')
    body=includes(body)
    first=body.index('EXPORT void web_test_heavy(void)')
    last=body.index('EXPORT uint32_t web_test_user_preset',first)
    body=body[:first]+body[last:]
    body=replace(body,'#define EXPORT __attribute__((used, visibility("default")))','#define EXPORT static')
    body=replace(body,'#include "menu_items.c"','''static void wrapper_cdc_switch(int on){usb_cdc_on=on!=0;}
#define usb_cdc_switch wrapper_cdc_switch
#include "menu_items.c"''')
    import re
    renames='\n'.join('#define '+symbol+' wrapper_felucca'+symbol for symbol in
                      sorted(set(re.findall(r'\b_(?:data|bss|pool|rt)_[a-z]+\b',body))))
    body=body.replace('void isr_alnk0(void)','static void isr_alnk0(void)').replace('void isr_timer5(void)','static void isr_timer5(void)')
    body=renames+'\n#define fm1_cstart wrapper_felucca_cstart\n'+body
    (native/'emulator.c').write_text(body+'\n#undef __attribute__\n')
    editor=(Path(__file__).resolve().parents[2]/'adapters/sloop/native/editor.c').read_text()
    param=editor[editor.index('static void editor_param('):editor.index('void sloop_editor_snapshot(')]
    shared=Path(__file__).resolve().parents[2]/'adapters/felucca'
    (native/'studio.c').write_text((shared/'studio.c').read_text()+"\n"+param+"\n"+(shared/'snapshot.c').read_text()+"\n"+(shared/'files.c').read_text())
    (out/'plugin.c').write_text('''/* SPDX-License-Identifier: GPL-3.0-only */
#include "firmware.h"
#include "native/emulator.c"
static uint32_t wrapper_saved_writes;
#include "native/studio.c"
static void wrapper_init(const uint8_t *image) {
    web_nor_erase();if(image)memcpy(nor,image,sizeof nor);
    web_boot();wrapper_saved_writes=web_flash_writes;
}
static void wrapper_render(float *out,unsigned n) {
    while(n){unsigned count=n>1024?1024:n;web_render(count);
        for(unsigned i=0;i<count;i++){out[2*i]=out_l[i];out[2*i+1]=out_r[i];}
        out+=count*2;n-=count;
    }
}
static void wrapper_event(int kind,int id,int value) {
    if(kind>=72&&kind<=91){studio_event(kind-64,id,value);return;}
    if(kind==FM1_BUTTON && id>=0 && id<NB){
        uint32_t mask=web_btn_labels;if(value)mask|=1u<<id;else mask&=~(1u<<id);web_buttons(mask);ui_input();
    } else if(kind==FM1_KEY && id>=0 && id<27){
        uint32_t mask=web_key_mask;if(value)mask|=1u<<id;else mask&=~(1u<<id);web_keys(mask);keyboard_block();ui_input();
    } else if(kind==FM1_ENCODER && id>=0 && id<NE){web_enc(id,clamp(value,-32,32));ui_input();}
    else if(kind==FM1_MIDI)midi_enqueue((uint32_t)value,1);
    else if(kind==FM1_PANIC){for(unsigned i=0;i<PF_NFX;i++)perf_press(i,0);perf_solo=0;memset(drum_mute,0,sizeof drum_mute);web_keys(0);web_buttons(0);mi_r=mi_w;panic_req=(1u<<NTRK)-1u;keyboard_block();ui_input();}
    else if(kind==FM1_MASTER)web_master((uint32_t)clamp(value,0,4096)*1023/4096);
    else if(kind==FM1_STOP)transport_req=2;
    else if(kind==FM1_CONNECTION)usb.config=usb.up=value!=0;
}
static void wrapper_tick(uint32_t ms){(void)ms;/* Firmware clock follows rendered audio, as upstream. */}
static void wrapper_pixels(uint32_t *out) {
    for(unsigned i=0;i<240*240;i++){uint16_t p=web_fb[i];p=(p>>8)|(p<<8);out[i]=0xff000000u|(((p>>11)*255u/31)<<16)|((((p>>5)&63)*255u/63)<<8)|((p&31)*255u/31);}
}
static int wrapper_playing(void){return song.playing!=0;}
static int wrapper_checkpoint(void){
    settings_save();
    if(!(ui_prefs&PREF_RESTORE_OFF)){
        project_capture(&proj_scratch);proj_wire_gen++;
        if(!proj_pack(&proj_wire,&proj_scratch)||st_save(OBJ_AUTOSAVE,&proj_wire,sizeof proj_wire)!=0)return 0;
    }return 1;
}
static const uint8_t *wrapper_storage(void){return nor;}
static int wrapper_dirty(void){return web_flash_writes!=wrapper_saved_writes;}
static void wrapper_clean(void){wrapper_saved_writes=web_flash_writes;}
static unsigned wrapper_midi(uint32_t *out,unsigned max){unsigned n=0;while(n<max&&mo_r!=mo_w)out[n++]=midi_out_q[mo_r++%MQ];return n;}
'''+api(definition.id))
