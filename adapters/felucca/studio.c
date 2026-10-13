/* SPDX-License-Identifier: GPL-3.0-only */
/* Felucca's semantic Android bindings: direct native operations, no device MMIO. */
#include "studio.h"
#include <stdio.h>
#include <stdlib.h>
static unsigned studio_edit_track;
static int studio_edit_fresh;
static void studio_pattern_begin(track_t *t){if(studio_edit_fresh&&studio_edit_track==trk_index(t)){undo.keep=0;studio_edit_fresh=0;}load_begin(t,UNDO_PAT);}
static int studio_drum(const track_t *t){return t->eng_req%NENGINES==ENGI_DRUM;}
static unsigned studio_len(const track_t *t){return clamp(t->p[P_SLEN],1,NSTEP);}
static void studio_changed(void){ui.force=1;}
static int studio_editable(void){if(chain_busy()){ui_message("STOP THE SONG FIRST");return 0;}return 1;}
static void studio_clear_step(track_t *t,unsigned i){unsigned n=note_len(t,i);for(unsigned j=i;j<i+n&&j<NSTEP;j++){step_clear(&t->step[j]);motion_clear_locks(t,j);}}
static void studio_action(unsigned id,int value){
    unsigned t=id/256,op=id%256;if(t>=NTRK)return;track_t *tr=&trk[t];
    if(op==44||op==45){for(unsigned i=0;i<NTRK;i++)trk[i].p[op==44?P_ROOT:P_SCALE]=clamp(value,0,op==44?11:NELEM(N_SCALE)-1);return;}
    if(op==46){song.octave=clamp(value,-3,3);return;}
    if(op==47){midi_event(0xb0,trk_midi_ch(t),64,value?127:0);return;}
    if(op==48){unsigned v=clamp(value,0,16383);midi_event(0xe0,trk_midi_ch(t),v&127,v>>7);return;}
    if(op==52){studio_edit_track=t;studio_edit_fresh=1;return;}
    if(op==53){studio_edit_fresh=0;return;}
    if(op==51)return;
    if(op==40||op==41){rp_put(op==40?RP_CLICK:RP_COUNTIN,clamp(value,0,2));return;}
    if(op==43){transport_req=2;song.rec=0;return;}
    if(op==3||op==4){if(!song.playing&&undo.keep)undo_swap();return;}
    if(op>=16&&op<=18){if(song.playing||transport_req||value<0||value>3){ui_message("STOP FIRST");return;}
        if(op==16)project_load(value);if(op==17)project_save(value);if(op==18){if(!st_save(OBJ_PROJECT0+value,0,0))memset(&proj_slot[value],255,sizeof proj_slot[value]);}return;}
    if(op>=32&&op<=34){if(value>=0&&value<UP_SLOTS&&!song.playing){track_select(t);up_ui(op==32?0:op==33?2:1,value);}return;}
    if(op==49){if(value>=0&&value<SMP_USER_SLOTS){set_engine_of(tr,ENGI_SAMPLE);tr->p[P_E0]=SMP_NSETS+value;}return;}
    if(!studio_editable())return;
    if(op==2){if(song.playing||transport_req){ui_message("STOP FIRST");return;}for(unsigned i=0;i<NTRK;i++){track_defaults(&trk[i]);track_defaults_steps(&trk[i]);set_engine_of(&trk[i],trk_def_engine(i));motion_clear(&trk[i]);}for(unsigned i=0;i<G_COUNT;i++)song.g[i]=GP[i].def;chain_defaults(&chain_config);go_home();return;}
    if(op==1){set_engine_of(tr,tr->eng_req);apply_preset_to(tr,0);return;}
    if(op>8)return;
    studio_pattern_begin(tr);
    if(op==0){track_defaults_steps(tr);motion_clear(tr);}
    if(op==5){step_t steps[NSTEP];unsigned len=studio_len(tr);memcpy(steps,tr->step,sizeof steps);for(unsigned i=0;i<len;i++)tr->step[(i+len+(value<0?-1:1))%len]=steps[i];for(unsigned i=0;i<motion.count;i++)if((motion.event[i].place>>6)==t){unsigned step=motion.event[i].place&63;motion.event[i].place=(t<<6)|((step+len+(value<0?-1:1))%len);}}
    if(op==6){unsigned len=studio_len(tr);if(len*2<=NSTEP){unsigned count=motion.count,needed=0;for(unsigned i=0;i<count;i++)if((motion.event[i].place>>6)==t&&(motion.event[i].place&63)<len)needed++;if(count+needed>MOTION_MAX){ui_message("AUTOMATION FULL");load_end(tr);return;}for(unsigned i=0;i<count;i++)if((motion.event[i].place>>6)==t&&(motion.event[i].place&63)<len){motion_event_t e=motion.event[i];e.place+=len;motion.event[motion.count++]=e;}memcpy(tr->step+len,tr->step,len*sizeof(step_t));tr->p[P_SLEN]=len*2;}}
    if(op==7)tr->p[P_SLEN]=studio_len(tr)/2?studio_len(tr)/2:1;
    if(op==8)for(unsigned i=0;i<NSTEP;i++)for(unsigned j=0;j<tr->step[i].n&&j<4;j++)tr->step[i].note[j]=clamp(tr->step[i].note[j]+value,0,127);
    load_end(tr);
}
static void studio_detail(unsigned id,int value){
    unsigned t=id/65536,i=(id%65536)/1024,l=(id%1024)/32,f=id%32;if(t>=NTRK||i>=NSTEP||l>=16||!studio_editable())return;
    track_t *tr=&trk[t];step_t *s=&tr->step[i];studio_pattern_begin(tr);
    if(f==0)s->vel=clamp(value,1,127);
    if(f==1){if(studio_drum(tr)&&l<NLANE){if(value==3)s->acc|=1u<<l;else s->acc&=~(1u<<l);}else s->vel=value==1?45:value==2?75:value==3?127:100;}
    if(f==2)step_set_ratchet(s,clamp(value,0,3)+1);
    if(f==3)step_set_nudge(s,clamp(value,-8,7));
    if(f==5&&s->time==ST_NOTE)note_set_len(tr,i,clamp(value,1,NSTEP-i));
    if(f==6)s->flags=(s->flags&~(SF_ACCENT|SF_SLIDE))|(value&3);
    if(f==7)studio_clear_step(tr,i);
    if(f==8&&l<s->n)s->note[l]=clamp(value,0,127);
    if(f==9&&!studio_drum(tr)&&s->n){unsigned to=(unsigned)value/128;int delta=(value%128)-64;if(to<NSTEP){if(to==i){for(unsigned j=0;j<s->n;j++)s->note[j]=clamp(s->note[j]+delta,0,127);}else if(!step_on(&tr->step[to])){step_t copy=*s;unsigned length=note_len(tr,i);motion_store_t backup;motion_snapshot_track(tr,&backup);studio_clear_step(tr,i);tr->step[to]=copy;for(unsigned j=0;j<copy.n;j++)tr->step[to].note[j]=clamp(copy.note[j]+delta,0,127);note_set_len(tr,to,length);for(unsigned j=0;j<backup.count;j++){motion_event_t *e=&backup.event[j];if((e->place&63)==i){if(e->param&MOTION_LOCK)motion_set_lock(tr,to,MOTION_ID(e),e->value);else motion_set_event(tr,to,MOTION_ID(e),e->value);}}}}}
    if(f==10)step_set_chance(s,clamp(value,0,100));
    load_end(tr);
}
static void studio_song(unsigned id,int value){
    unsigned i=id/16,f=id%16;if(song.playing||chain_busy()){ui_message("STOP FIRST");return;}
    if(f==0){if(value)chain_play_ui();return;}
    if(f==1&&value>=0&&value<4)project_load(value);
    if(f==2&&value>=0&&value<4)project_save(value);
    if(f==7)chain_defaults(&chain_config);
    if(f==8&&i<chain_config.count&&value>=0&&value<4)for(unsigned t=0;t<NTRK;t++)chain_config.row[i].slot[t]=value;
    if(f==9&&i<chain_config.count)chain_config.row[i].repeat=clamp(value,1,16);
    if(f==10&&i<chain_config.count){int j=i+value;if(j>=0&&j<chain_config.count){chain_row_t row=chain_config.row[i];chain_config.row[i]=chain_config.row[j];chain_config.row[j]=row;}}
    if(f==11&&i<chain_config.count){for(unsigned j=i;j+1<chain_config.count;j++)chain_config.row[j]=chain_config.row[j+1];chain_config.count--;}
    if(f==12&&value>=0&&value<4&&chain_config.count<CHAIN_ROWS)chain_config.row[chain_config.count++]=chain_row_of(value,1);
    if(f==14&&i<CHAIN_ROWS&&value>=0){unsigned track=value/8,slot=value%8;if(track<NTRK&&slot<=CHAIN_SILENT&&i<chain_config.count)chain_config.row[i].slot[track]=slot;}
    if(f==13)project_save(proj_cur<4?proj_cur:0);
}
static void studio_event(int kind,int id,int value){
    unsigned t=(unsigned)id;
    if(kind==SLOOP_ACTION)studio_action(t,value);
    else if(kind==SLOOP_DETAIL)studio_detail(t,value);
    else if(kind==SLOOP_SELECT&&t<NTRK)track_select(t);
    else if(kind==SLOOP_PARAM&&id>=0&&t/256<NTRK&&t%256<P_COUNT){track_t *tr=&trk[t/256];unsigned p=t%256;const param_desc_t *d=track_desc(tr,p);if(d&&d->max>d->min){tr->p[p]=clamp(value,d->min,d->max);if(ui.ml.on)ml_knob(tr,p);}}
    else if(kind==SLOOP_GLOBAL&&t<G_COUNT&&GP[t].max>GP[t].min){song.g[t]=clamp(value,GP[t].min,GP[t].max);settings_later();}
    else if(kind==SLOOP_ENGINE&&t<NTRK&&value>=0&&value<NENGINES&&eng_ok(value))set_engine_of(&trk[t],value);
    else if(kind==SLOOP_PRESET&&t<NTRK&&value>=0&&value<ENGINES[trk[t].eng_req%NENGINES]->npresets)apply_preset_to(&trk[t],value);
    else if(kind==SLOOP_MIX&&t<NTRK)trk[t].p[P_LEVEL]=clamp(value,0,127);
    else if(kind==SLOOP_MUTE&&t<NTRK)trk[t].p[P_MUTE]=value!=0;
    else if(kind==SLOOP_TRANSPORT){if(value&&chain_config.count)chain_play_ui();else transport_req=value?1:2;}
    else if(kind==SLOOP_RECORD&&t<NTRK){track_select(t);if((value!=0)!=((song.rec&(1u<<t))!=0))rec_tap();}
    else if(kind==SLOOP_NOTE&&t/128<NTRK){if(value)input_on(&trk[t/128],t%128,clamp(value,1,127));else input_off(&trk[t/128],t%128);}
    else if(kind==SLOOP_EDITOR_HOME){go_home();song.seq_mode=0;}
    else if(kind==SLOOP_PAGE&&t<NPAGES&&page_visible(t)){ui.home=ui.menu=ui.layer=0;ui.page=t;page_entered();song.seq_mode=0;}
    else if(kind==SLOOP_STEP&&t/8192<NTRK){unsigned step=(t%8192)/128,note=t%128;track_t *tr=&trk[t/8192];if(!studio_editable())return;studio_pattern_begin(tr);step_t *s=&tr->step[step];
        if(studio_drum(tr)&&note<NLANE){if(value){s->hit|=1u<<note;s->time=ST_NOTE;s->vel=100;}else{s->hit&=~(1u<<note);s->acc&=~(1u<<note);if(!s->hit&&!s->n)studio_clear_step(tr,step);}}
        else{unsigned n=0;while(n<s->n&&n<4&&s->note[n]!=note)n++;if(value&&n==s->n&&n<4){if(!s->n&&!s->hit){step_clear(s);s->vel=100;}s->note[s->n++]=note;s->time=ST_NOTE;}else if(!value&&n<s->n&&n<4){for(unsigned j=n;j+1<s->n;j++)s->note[j]=s->note[j+1];s->n--;if(!s->n&&!s->hit)studio_clear_step(tr,step);}}
        load_end(tr);
    }
    else if(kind==SLOOP_LOCK&&t/16384<NTRK&&t%256<P_COUNT){track_t *tr=&trk[t/16384];unsigned step=(t%16384)/256,p=t%256;if(value==-32768)motion_delete_event(tr,step,p);else if(motion_set_lock(tr,step,p,value))ui_message("AUTOMATION FULL");}
    else if(kind==SLOOP_PERFORM){if(t<PF_NFX)perf_press(t,value!=0);if(t==27)perf_press(PF_RTHR,value!=0);if(t==28)perf_press(PF_ETHR,value!=0);if(t>=40&&t<40+NTRK){unsigned b=1u<<(t-40);perf_solo=value?perf_solo|b:perf_solo&~b;}if(t>=50&&t<50+NLANE){unsigned b=1u<<(t-50);drum_mute[song.sel]=value?drum_mute[song.sel]|b:drum_mute[song.sel]&~b;}if(t==70&&value)glo_tap();if(t==71){for(unsigned i=0;i<PF_NFX;i++)perf_press(i,0);perf_solo=0;}}
    else if(kind==SLOOP_SONG)studio_song(t,value);
    else if(kind==SLOOP_CONFIG){if(t==0){settings.palette=clamp(value,0,NPALETTES-1);palette_set(settings.palette);}if(t==1){if(value)ui_prefs|=PREF_LARGE;else ui_prefs&=~PREF_LARGE;}if(t==2)settings.lowcut=fx_lowcut=clamp(value,0,2);if(t==3)ml_clear_all();if(t==4){ui.ml.on=value!=0;ui.ml.pick=0;}if(t==5&&value>=0&&value<P_COUNT)ml_knob(TSEL,value);if(t==6)ml_drop(value,NTRK,P_COUNT);settings_later();}
    else if(kind==SLOOP_ADVANCED){if(t<NTRK*256&&t%256<FP_SIZE){unsigned tr=t/256,p=t%256;uint8_t patch[FP_SIZE+1];memcpy(patch,fm6_patch[tr],sizeof patch);patch[p]=clamp(value,0,fm6_max(p));fm6_set_patch(tr,patch);fm6_adopt(tr);}if(t==9200&&!song.playing)settings_save();if(t>=9300&&t<9300+SMP_USER_SLOTS&&!song.playing){memset((void *)smp_user_xip(t-9300),255,SMP_USER_SIZE);smp_user_scan(t-9300);web_flash_writes++;}}
    studio_changed();
}
