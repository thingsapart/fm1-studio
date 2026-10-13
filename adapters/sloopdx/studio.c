/* SPDX-License-Identifier: GPL-3.0-only */
/* Semantic controls for sloopDX; native voice/drum editors remain authoritative. */
#include "studio.h"
#include <stdio.h>
#include "parameter.c"
static uint32_t dx_host_fx,dx_edit_session;
static unsigned dx_edit_track;

static int dx_editable(void){if(ft_on||(song.playing&&arrangement_enabled)){ui_message("STOP FIRST");return 0;}return 1;}
static void dx_mark(track_t *t){uint32_t session=dx_edit_session&&dx_edit_track==trk_index(t)?dx_edit_session:(undo_sess+=4u)|3u;if(undo.valid&&!undo.undone&&undo.trk==trk_index(t)&&undo.sess==session)return;undo_mark(t,session);}
static void dx_clear(track_t *t,unsigned i){
    if(!is_drum(t))for(unsigned j=i+1;j<trk_len(t)&&t->step[j].time==ST_TIE;j++)step_clear(&t->step[j]);
    step_clear(&t->step[i]);if(is_drum(t))dext.lock[i]=0;
}
static unsigned dx_note_len(track_t *t,unsigned i){unsigned n=1;while(i+n<trk_len(t)&&t->step[i+n].time==ST_TIE)n++;return n;}
static void dx_note_length(track_t *t,unsigned i,unsigned n){
    unsigned old=dx_note_len(t,i);for(unsigned j=1;j<old;j++)step_clear(&t->step[i+j]);
    for(unsigned j=1;j<n&&i+j<trk_len(t);j++){if(t->step[i+j].time==ST_NOTE&&t->step[i+j].n)break;t->step[i+j].time=ST_TIE;}
}
static void dx_perform_apply(void){
    if(dx_host_fx){punch.hold=1;for(int i=PUNCH_NFX-1;i>=0;i--)if(dx_host_fx&(1u<<i)){punch.req=i;break;}}
}
static void dx_perform_reset(void){dx_edit_session=0;dx_host_fx=0;punch.hold=0;punch.req=-1;ly_lock=LY_PLAY;}
static void dx_action(unsigned id,int value){
    unsigned t=id/256,op=id%256;if(t>=NTRK)return;track_t *tr=&trk[t];
    if(op==52){dx_edit_track=t;dx_edit_session=(undo_sess+=4u)|3u;return;}
    if(op==53){dx_edit_session=0;return;}
    if(op==40){if(!song.playing&&!ft_on){rec_tempo=value!=0;settings_later=1;}return;}
    if(op==41){if(!song.playing&&!ft_on){rec_count=value!=0;settings_later=1;}return;}
    if(op==42){if(ft_on)ft_close();return;}if(op==43){transport_req=2;return;}
    if(op==46){song.octave=clamp(value,-3,3);return;}
    if(op==44||op==45){for(unsigned i=0;i<NPART;i++)trk[i].p[op==44?P_ROOT:P_SCALE]=clamp(value,0,op==44?11:(sizeof N_SCALE/sizeof N_SCALE[0])-1);return;}
    if(op==19){if(arrangement_enabled&&song.playing)return;if(value>=0&&value<4){if(song.playing){live_req=value;chain_n=0;}else section_load(value);}return;}
    if(op>=16&&op<=18){if(song.playing||transport_req||value<0||value>3){ui_message("STOP FIRST");return;}
        if(op==16)project_load(value);if(op==17)project_save(value);if(op==18&&!st_save(OBJ_PROJECT0+value,0,0))memset(&proj_slot[value],0,sizeof proj_slot[value]);return;}
    if(op>=32&&op<=34){if(value>=0&&value<UP_SLOTS&&!song.playing&&!transport_req){track_select(t);up_ui(op==32?0:op==33?2:1,value);}return;}
    if(!dx_editable())return;
    if(op==3||op==4){undo_swap(op==4);return;}
    if(op==2){if(song.playing||transport_req)return;project_new();dx_perform_reset();go_home();return;}
    if(op==1){if(t<NPART)apply_preset_to(tr,0);return;}
    if(op>8)return;dx_mark(tr);
    if(op==0){track_defaults_steps(tr);if(is_drum(tr))memset(dext.lock,0,sizeof dext.lock);}
    if(op==5){pattern_rotate(tr,value>=0?1:-1);}
    if(op==6||op==7)pattern_length(tr,op==6?1:-1);
    if(op==8&&!is_drum(tr))pattern_transpose(tr,value);
}
static void dx_detail(unsigned id,int value){
    unsigned t=id/131072,i=(id%131072)/1024,l=(id%1024)/32,f=id%32;
    if(t>=NTRK||i>=NSTEP||l>=16||!dx_editable())return;track_t *tr=&trk[t];step_t *s=&tr->step[i];dx_mark(tr);
    if(f==7){dx_clear(tr,i);return;}
    if(is_drum(tr)){if((f==1||f==2)&&dstep_has(&tr->dstep[i],l))dstep_set(&tr->dstep[i],l,f==1?clamp(value,0,3):dstep_lvl(&tr->dstep[i],l),f==2?clamp(value,0,3):dstep_rat(&tr->dstep[i],l));}
    else{
        if(f==0)s->vel=clamp(value,1,127);if(f==6)s->flags=value&3;
        if((f==1||f==2)&&l<s->n){uint8_t *v=f==1?&s->lvl:&s->rat;*v=(*v&~(3u<<(2*l)))|(clamp(value,0,3)<<(2*l));}
        if(f==5&&s->time==ST_NOTE)dx_note_length(tr,i,clamp(value,1,NSTEP-i));
        if(f==8&&l<s->n)s->note[l]=clamp(value,0,127);
        if(f==9&&s->time==ST_NOTE&&s->n){unsigned to=(unsigned)value/128;int delta=value%128-64;if(to>=trk_len(tr))return;
            if(to==i){for(unsigned j=0;j<s->n;j++)s->note[j]=clamp(s->note[j]+delta,0,127);}
            else if(!tr->step[to].n&&tr->step[to].time!=ST_TIE){step_t copy=*s;unsigned len=dx_note_len(tr,i);dx_clear(tr,i);tr->step[to]=copy;for(unsigned j=0;j<copy.n;j++)tr->step[to].note[j]=clamp(copy.note[j]+delta,0,127);dx_note_length(tr,to,len);}
        }
    }
}
static void dx_song(unsigned id,int value) {
    unsigned i=id/16,f=id%16;
    if(f==1){dx_action(19,value);return;}
    if(f==3){if(value&&!srec){if(live_sec<0){ui_message("PICK A SECTION FIRST");return;}srec=1;}else if(!value&&srec)srec_stop();return;}
    if((f==5||f==6)&&arrangement_enabled){ui_message("USE LOOP MODE");return;}
    if(f==5){if(value>=0&&value<4&&chain_n<CHAIN_MAX&&project_used(value)){chain_sec[chain_n++]=value;}return;}
    if(f==6){if(chain_n){chain_i=0;chain_bars=section_bars(chain_sec[0]);live_req=chain_sec[0];if(!song.playing)transport_req=1;}return;}
    if(f==7){chain_n=0;return;}
    if(song.playing||transport_req||ft_on){ui_message("STOP FIRST");return;}
    if(f==0)arrangement_enabled=value!=0;
    if(f==2&&value>=0&&value<4)section_store(value);
    if(f==4)arrangement.loop=value!=0;
    if(i<ARR_STEPS){if(f==8&&i<arrangement.count)arrangement.entry[i].scene=clamp(value,0,3);
       if(f==9&&i<arrangement.count)arrangement.entry[i].bars=clamp(value,1,64);
       if(f==10&&i<arrangement.count){int j=(int)i+value;if(j>=0&&j<arrangement.count){arr_entry_t x=arrangement.entry[i];arrangement.entry[i]=arrangement.entry[j];arrangement.entry[j]=x;}}
       if(f==11&&arrangement.count>1&&i<arrangement.count){for(unsigned j=i;j+1<arrangement.count;j++)arrangement.entry[j]=arrangement.entry[j+1];arrangement.count--;}
       if(f==12&&arrangement.count<ARR_STEPS){arrangement.entry[arrangement.count++]=(arr_entry_t){clamp(value,0,3),1};}
    }
    arrangement_save();
}
static void editor_event(int kind,int id,int value){
    if(id<0)return;unsigned t=id;
    if(kind==SLOOP_ACTION)dx_action(t,value);
    else if(kind==SLOOP_DETAIL)dx_detail(t,value);
    else if(kind==SLOOP_SONG)dx_song(t,value);
    else if(kind==SLOOP_SELECT&&t<NTRK&&!ft_on)track_select(t);
    else if(kind==SLOOP_PARAM&&t/256<NTRK&&t%256<P_COUNT){track_t *tr=&trk[t/256];unsigned p=t%256;const param_desc_t *d=track_desc(tr,p);
        if(d&&d->max>d->min){if(p==P_SLEN){if(!dx_editable())return;dx_mark(tr);slen_set(tr,value);}else tr->p[p]=clamp(value,d->min,d->max);}}
    else if(kind==SLOOP_GLOBAL&&t<G_COUNT&&GP[t].max>GP[t].min&&GP[t].names!=N_GO){song.g[t]=clamp(value,GP[t].min,GP[t].max);lights_sync=song.g[G_SYNC];lights_min=song.g[G_ROUTE];lights_mout=song.g[G_MIDI];settings_later=1;}
    else if(kind==SLOOP_PRESET&&t<NPART&&value>=0&&value<DX_NVOICES){apply_preset_to(&trk[t],value);}
    else if(kind==SLOOP_MIX&&t<NTRK){if(t==TRK_DRUM)song.g[G_DRLVL]=clamp(value,0,127);else trk[t].p[P_LEVEL]=clamp(value,0,127);}
    else if(kind==SLOOP_MUTE&&t<NTRK)trk[t].p[P_MUTE]=value!=0;
    else if(kind==SLOOP_RECORD&&t<NTRK){if(ft_on){if(!value)ft_close();}else{track_select(t);if((value!=0)!=(song.rec!=0||rec_wait!=0))rec_toggle();}}
    else if(kind==SLOOP_TRANSPORT)transport_req=ft_on?2:value?1:2;
    else if(kind==SLOOP_NOTE&&t/128<NTRK){if(value>0)input_on(&trk[t/128],t%128,clamp(value,1,127));else input_off(&trk[t/128],t%128);}
    else if(kind==SLOOP_EDITOR_HOME){go_home();song.seq_mode=0;}
    else if(kind==SLOOP_PAGE&&t<NPAGES&&(!is_drum(TSEL)||page_for_drum(&PAGES[t]))){ui.home=ui.menu=0;ui.page=t;page_entered();song.seq_mode=0;}
    else if(kind==SLOOP_STEP&&t/16384<NTRK&&dx_editable()){
        track_t *tr=&trk[t/16384];unsigned i=(t%16384)/128,note=t%128;dx_mark(tr);step_t *s=&tr->step[i];
        if(is_drum(tr)){if(note<DRUM_LANES){if(value)dstep_set(&tr->dstep[i],note,LV_NORM,0);else dstep_clr(&tr->dstep[i],note);}}
        else{unsigned k=0;while(k<s->n&&k<4&&s->note[k]!=note)k++;
            if(value&&k==s->n&&s->n<4){if(!s->n){memset(s,0,sizeof *s);s->vel=100;}s->note[s->n++]=note;s->time=ST_NOTE;}
            else if(!value&&k<s->n&&k<4){for(unsigned j=k;j+1<s->n;j++){s->note[j]=s->note[j+1];s->lvl=(s->lvl&~(3u<<(2*j)))|(((s->lvl>>(2*(j+1)))&3u)<<(2*j));s->rat=(s->rat&~(3u<<(2*j)))|(((s->rat>>(2*(j+1)))&3u)<<(2*j));}s->n--;s->note[s->n]=0;s->lvl&=~(3u<<(2*s->n));s->rat&=~(3u<<(2*s->n));if(!s->n)dx_clear(tr,i);}}
    }
    else if(kind==SLOOP_PERFORM){if(t<PUNCH_NFX){if(value)dx_host_fx|=1u<<t;else dx_host_fx&=~(1u<<t);if(!dx_host_fx){punch.hold=0;punch.req=-1;}}
        if(t>=40&&t<44){unsigned b=1u<<(t-40);song.solo=value?song.solo|b:song.solo&~b;}
        if(t==70&&value)tap_tempo();if(t==71)dx_perform_reset();}
    else if(kind==SLOOP_CONFIG){if(t==0){settings.palette=clamp(value,0,NPALETTES-1);palette_set(settings.palette);}if(t==1)settings.zoom=value!=0;if(t==2)settings.lowcut=fx_lowcut=value!=0;settings_later=1;}
    else if(kind==SLOOP_ADVANCED&&t==8192&&!song.playing&&!transport_req){if(value>=0&&value<DX_NBANKS)dx_bank_select(value);}
    ui_input_ms=fm1_ms;ui.force=1;
}
void sloop_editor_snapshot(struct sloop_editor *out){
    memset(out,0,sizeof *out);out->version=SLOOP_EDITOR_VERSION;out->step_count=NSTEP;out->selected=song.sel;out->playing=song.playing;
    out->recording=song.rec|(rec_wait?1u<<song.sel:0u)|(ft_on?1u<<ft_trk:0u);out->bpm=song.g[G_BPM];out->length=trk_len(TSEL);out->length_parameter=song.sel*256+P_SLEN;out->position=song.playing?TSEL->seq_idx:-1;
    out->engine=0;out->preset=TSEL->p[P_E0];str_cpy(out->engines[0],"DX7",16);out->preset_count=DX_NVOICES;
    for(unsigned i=0;i<DX_NVOICES;i++)str_cpy(out->presets[i],dx_names[i],24);
    for(unsigned i=0;i<NTRK;i++){out->tracks[i].level=i==TRK_DRUM?song.g[G_DRLVL]:trk[i].p[P_LEVEL];out->tracks[i].mute=trk[i].p[P_MUTE];out->tracks[i].drum=i==TRK_DRUM;out->tracks[i].channel=trk_midi_ch(i);str_cpy(out->tracks[i].name,i==TRK_DRUM?"FM DRUMS":"DX7",32);}
    for(unsigned i=0;i<NPAGES&&out->group_count<SLOOP_EDITOR_GROUPS;i++){
        const page_t *pg=&PAGES[i];if(pg->scope!=SC_TRACK&&pg->scope!=SC_ENGINE&&pg->scope!=SC_GLOBAL&&pg->scope!=SC_MIX)continue;
        if(pg->fam==FAM_SAVE||(is_drum(TSEL)&&!page_for_drum(pg)))continue;
        struct sloop_group *g=&out->groups[out->group_count];str_cpy(g->title,pg->title,sizeof g->title);g->page=i;
        for(unsigned k=0;k<4;k++){unsigned id=pg->id[k];if(id==255)continue;
            int global=pg->scope==SC_GLOBAL||(pg->scope==SC_MIX&&(id&PG_G));id&=~PG_G;
            int16_t *value=NULL;const param_desc_t *d=page_desc(pg,k,&value);
            if(d&&value&&d->max>d->min){param_desc_t labelled=*d;if(pg->lab)labelled.label=pg->lab[k];
                if(!global&&id==P_SLEN)labelled.max=slen_room(TSEL);
                editor_param(&g->param[g->count++],global?FM1_GLOBAL_PARAMETER_BASE+id:song.sel*256+id,&labelled,*value);}}
        if(g->count)out->group_count++;
    }
    if(is_drum(TSEL)){struct sloop_group *g=&out->groups[out->group_count++];str_cpy(g->title,"KIT",24);g->page=-1;g->count=1;
        param_desc_t d={"KIT",F_ENUM,0,DRUM_KITS-1,0,DRUM_KIT_NAMES,0};editor_param(&g->param[0],song.sel*256+P_E0,&d,TSEL->p[P_E0]);}
    out->octave=song.octave;out->current.page=ui.home?-1:ui.page;str_cpy(out->current.title,ui.home?"HOME":cur_page()->title,24);
    for(int i=0;i<out->group_count;i++)if(out->groups[i].page==out->current.page&&out->current.page>=0)out->current=out->groups[i];
    if(ui.home||cur_page()->scope==SC_TRK){out->current.count=4;
        editor_param(&out->current.param[0],FM1_GLOBAL_PARAMETER_BASE+G_SWING,&GP[G_SWING],song.g[G_SWING]);
        editor_param(&out->current.param[1],is_drum(TSEL)?FM1_GLOBAL_PARAMETER_BASE+G_DRLVL:song.sel*256+P_LEVEL,is_drum(TSEL)?&GP[G_DRLVL]:&TP[P_LEVEL],is_drum(TSEL)?song.g[G_DRLVL]:TSEL->p[P_LEVEL]);
        param_desc_t len=TP[P_SLEN];len.max=slen_room(TSEL);editor_param(&out->current.param[2],song.sel*256+P_SLEN,&len,TSEL->p[P_SLEN]);editor_param(&out->current.param[3],song.sel*256+P_PAN,&TP[P_PAN],TSEL->p[P_PAN]);}
    for(unsigned i=0;i<NSTEP;i++){for(unsigned k=0;k<4;k++)out->steps[i][k]=-1;if(is_drum(TSEL))out->steps[i][0]=dstep_mask(&TSEL->dstep[i]);else if(TSEL->step[i].time==ST_NOTE)for(unsigned k=0;k<TSEL->step[i].n&&k<4;k++)out->steps[i][k]=TSEL->step[i].note[k];}
    for(unsigned i=0;i<16;i++)str_cpy(out->lanes[i],LANE_NAME[i],16);
    size_t n=0,size=sizeof out->extra;char *buf=out->extra;
#define J(...) do{if(n<size){int z=snprintf(buf+n,size-n,__VA_ARGS__);if(z>0)n+=(size_t)z;}}while(0)
    J(",\"maxSteps\":%u,\"stepStride\":16384,\"detailStride\":131072,\"dxBank\":%u,\"recordState\":%u,\"recTempo\":%u,\"recCount\":%u,\"freeTrack\":%u,\"solo\":%u,\"undo\":%u,\"redo\":%u,\"undoTrack\":%u,\"palette\":%u,\"paletteMax\":%u,\"zoom\":%u,\"lowcut\":%u,\"division\":%u,\"songMode\":%u,\"songLoop\":%u,\"songRecording\":%u,\"message\":\"",slen_room(TSEL),dx_bank_cur,ft_on?3:ci_on?2:rec_wait?1:song.rec?4:0,rec_tempo,rec_count,ft_trk,song.solo,undo.valid&&!undo.undone,undo.valid&&undo.undone,undo.trk,settings.palette,(unsigned)NPALETTES-1,settings.zoom,settings.lowcut,TSEL->p[P_SDIV],arrangement_enabled,arrangement.loop,srec);
    for(const char *p=ui.msg_t?ui.msg:"";*p;p++)if(*p>=32&&*p!='"'&&*p!='\\')J("%c",*p);
    J("\",\"sections\":[");for(unsigned i=0;i<4;i++)J("%s%d",i?",":"",project_used(i));
    J("],\"arrangement\":[");for(unsigned i=0;i<arrangement.count;i++)J("%s[%u,%u]",i?",":"",arrangement.entry[i].scene,arrangement.entry[i].bars);
    J("],\"chain\":[");for(unsigned i=0;i<chain_n;i++)J("%s%u",i?",":"",chain_sec[i]);
    J("],\"userSounds\":[");for(unsigned i=0;i<UP_SLOTS;i++){char name[16];up_name(i,name);J("%s{\"used\":%d,\"name\":\"",i?",":"",up_used(i));for(char *p=name;*p;p++)if(*p>=32&&*p!='"'&&*p!='\\')J("%c",*p);J("\"}");}
    J("],\"detail\":[");for(unsigned i=0;i<NSTEP;i++){step_t *s=&TSEL->step[i];J("%s{\"time\":%u,\"velocity\":%u,\"flags\":%u,\"nudge\":0,\"fill\":0,\"length\":%u,\"levels\":[",i?",":"",is_drum(TSEL)?ST_NOTE:s->time,is_drum(TSEL)?100:s->vel,is_drum(TSEL)?0:s->flags,is_drum(TSEL)?1:dx_note_len(TSEL,i));
        for(unsigned l=0;l<(is_drum(TSEL)?16u:4u);l++)J("%s%u",l?",":"",is_drum(TSEL)?dstep_lvl(&TSEL->dstep[i],l):(s->lvl>>(l*2))&3u);
        J("],\"ratchets\":[");for(unsigned l=0;l<(is_drum(TSEL)?16u:4u);l++)J("%s%u",l?",":"",is_drum(TSEL)?dstep_rat(&TSEL->dstep[i],l):(s->rat>>(l*2))&3u);J("],\"locks\":[]}");}
    J("],\"punchNames\":[");for(unsigned i=0;i<PUNCH_NFX;i++)J("%s\"%s\"",i?",":"",PUNCH_NAME[i]);
    J("],\"catalogue\":[");for(unsigned i=0;i<DX_NVOICES;i++){J("%s{\"engine\":0,\"preset\":%u,\"name\":\"",i?",":"",i);for(const char *p=dx_names[i];*p;p++)if(*p!='"'&&*p!='\\')J("%c",*p);J("\"}");}
    J("],\"ids\":{\"root\":%u,\"scale\":%u,\"length\":%u,\"roll\":%u,\"drumChannel\":%u,\"sync\":%u},\"samples\":[],\"midiLearn\":[],\"lockParameters\":[]",P_ROOT,P_SCALE,P_SLEN,G_ROLL,G_DRCH,G_SYNC);
#undef J
}
#include "files.c"
