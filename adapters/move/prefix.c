/* SPDX-License-Identifier: GPL-3.0-only */
#include <stdlib.h>
static unsigned studio_project_export(int,uint8_t *,unsigned);
static int studio_project_import(int,const uint8_t *,unsigned);
#define SLOOP_STORAGE_SIZE 1048576u
static int studio_sample(unsigned,const uint8_t *,unsigned);
#define sloop_import_sample studio_sample
#define storage_dirty sim_dirty

static unsigned note_len(const track_t *t,unsigned i){unsigned n=1;while(i+n<trk_len(t)&&t->step[i+n].time==ST_TIE)n++;return n;}
static void note_set_len(track_t *t,unsigned i,unsigned length){unsigned old=note_len(t,i),want=clamp(length,1,trk_len(t)-i),n;for(n=1;n<want;n++){if(t->step[i+n].time==ST_NOTE&&t->step[i+n].n)break;step_clear(&t->step[i+n]);t->step[i+n].time=ST_TIE;}for(unsigned j=n;j<old;j++)step_clear(&t->step[i+j]);}
static int page_shown(const page_t *p){(void)p;return 1;}
static void rec_toggle(void){if(song.rec||rec_wait){song.rec=rec_wait=rec_pend=0;}else if(song.playing)rec_begin();else rec_wait=1;}
static void studio_open(int page){(void)page;screen_go(SC_PLAY);}
static void project_erase(unsigned slot){project_delete_fmp(slot);}
static void project_new(void){project_default_tracks();for(unsigned i=0;i<G_COUNT;i++)song.g[i]=GP[i].def;clip_init();}
static unsigned keys_sounding(const track_t *t){(void)t;return wrapper_keys;}
static void android_song(unsigned id,int value){
    unsigned track=id/16,op=id%16;
    if(track>=NTRK)return;
    if(op==1&&value>=0&&value<NCLIP)clip_launch(track,value);
    if(op==2&&value>=0&&value<NCLIP){if(clip_copy(track,clip_playing(track),value))ui_message("CLIP MEMORY FULL");}
    if(op==3&&value>=0&&value<NCLIP)clip_erase(track,value);
    if(op==4&&value>=0&&value<NCLIP)clip_scene(value);
}
