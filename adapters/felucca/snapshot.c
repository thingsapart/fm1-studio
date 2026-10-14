/* SPDX-License-Identifier: GPL-3.0-only */
#include <stdarg.h>
static void studio_append(char *out,size_t cap,size_t *n,const char *format,...){if(*n>=cap)return;va_list args;va_start(args,format);int count=vsnprintf(out+*n,cap-*n,format,args);va_end(args);if(count>0)*n+=(unsigned)count;}
static void studio_quote(char *out,size_t cap,size_t *n,const char *text){studio_append(out,cap,n,"\"");for(const unsigned char *p=(const void *)text;*p;p++){if(*p=='"'||*p=='\\')studio_append(out,cap,n,"\\%c",*p);else if(*p>=32)studio_append(out,cap,n,"%c",*p);}studio_append(out,cap,n,"\"");}
static void studio_json(struct sloop_editor *out){
    size_t n=0;char *b=out->extra;size_t cap=sizeof out->extra;
#define J(...) studio_append(b,cap,&n,__VA_ARGS__)
#define Q(s) studio_quote(b,cap,&n,s)
    J(",\"laneCount\":%u,\"recordState\":%d,\"recTempo\":%u,\"recCount\":%u,\"solo\":%u,\"drumMute\":%u,\"fillOn\":0,\"layer\":0,\"undo\":%u,\"redo\":%u,\"undoTrack\":%d,\"songMode\":%u,\"liveSection\":%d,\"queuedSection\":-1,\"songRecording\":0,\"songLoop\":1,\"songIndex\":%u,\"palette\":%u,\"zoom\":%u,\"lowcut\":%u,\"learn\":%u,\"message\":",NLANE,seq_counting()?2:song.rec?4:0,rp_get(RP_CLICK),rp_get(RP_COUNTIN),perf_solo,drum_mute[song.sel],undo.keep,undo.keep,(int)undo.trk-1,chain_busy(),proj_cur<4?(int)proj_cur:-1,chain.row,settings.palette,(ui_prefs&PREF_LARGE)!=0,settings.lowcut,ui.ml.on);Q(ui.msg_t?ui.msg:"");
    J(",\"sections\":[");for(unsigned i=0;i<4;i++)J("%s%d",i?",":"",project_used(i));
    J("],\"arrangement\":[");for(unsigned i=0;i<chain_config.count;i++)J("%s[%u,%u]",i?",":"",chain_config.row[i].slot[0],chain_config.row[i].repeat);
    J("],\"songLanes\":[");for(unsigned i=0;i<chain_config.count;i++){J("%s[",i?",":"");for(unsigned t=0;t<NTRK;t++)J("%s%u",t?",":"",chain_config.row[i].slot[t]);J("]");}
    J("],\"chain\":[],\"userSounds\":[");for(unsigned i=0;i<UP_SLOTS;i++){char name[13];up_name(i,name);J("%s{\"used\":%d,\"name\":",i?",":"",up_used(i));Q(name);J("}");}
    J("],\"detail\":[");for(unsigned i=0;i<NSTEP;i++){const step_t *s=&TSEL->step[i];J("%s{\"time\":%u,\"velocity\":%u,\"flags\":%u,\"nudge\":%d,\"fill\":0,\"length\":%u,\"probability\":%u,\"levels\":[",i?",":"",s->time,s->vel,s->flags&3,step_nudge(s),note_len(TSEL,i),step_chance(s));for(unsigned l=0;l<(studio_drum(TSEL)?NLANE:4);l++)J("%s%u",l?",":"",studio_drum(TSEL)?((s->acc&(1u<<l))?3:0):0);J("],\"ratchets\":[");for(unsigned l=0;l<(studio_drum(TSEL)?NLANE:4);l++)J("%s%u",l?",":"",step_ratchet(s)-1);J("],\"locks\":[");int count=0;for(unsigned l=0;l<motion.count;l++){const motion_event_t *e=&motion.event[l];if(e->place==(song.sel<<6|i)&&(e->param&MOTION_LOCK))J("%s[%u,%d]",count++?",":"",MOTION_ID(e),e->value);}J("]}");}
    J("],\"punchNames\":[");for(unsigned i=0;i<PF_NFX;i++){if(i)J(",");Q(PF_NAME[i]);}
    J("],\"samples\":[");for(unsigned i=0;i<SMP_USER_SLOTS;i++)J("%s%u",i?",":"",usr_nz[i]);
    J("],\"catalogue\":[");int count=0;for(unsigned e=0;e<NENGINES;e++)if(eng_ok(e))for(unsigned i=0;i<ENGINES[e]->npresets;i++){J("%s{\"engine\":%u,\"preset\":%u,\"name\":",count++?",":"",e,i);Q(ENGINES[e]->presets[i].name);J("}");}
    J("],\"midiLearn\":[");count=0;for(unsigned i=0;i<ML_N;i++){unsigned e=ml_get(i),id=ml_id(e>>9);if((e>>9)&&id<P_COUNT){J("%s[%u,%u,%u,",count++?",":"",e&127,(e>>7)&3,id);Q(track_desc(&trk[(e>>7)&3],id)->label);J("]");}}
    J("],\"fm6\":[");for(unsigned i=0;i<FP_SIZE;i++)J("%s[%u,%u]",i?",":"",fm6_patch[song.sel][i],fm6_max(i));
    J("],\"fm6BankUsed\":0,\"synthKits\":[],\"synthFactory\":[],\"userSlot\":%d,\"sustain\":%u,\"bend\":%d,\"division\":%d,\"sounding\":%u,\"dynamics\":0,\"ids\":{\"root\":%u,\"scale\":%u,\"filter\":%u,\"length\":%u,\"roll\":-1,\"drumChannel\":-1,\"sync\":%u},\"lockParameters\":[",TSEL->user?(int)TSEL->user-1:-1,midi_ch[trk_midi_ch(song.sel)].pedal,midi_ch[trk_midi_ch(song.sel)].bend+8192,TSEL->p[P_SDIV],web_key_mask,P_ROOT,P_SCALE,P_ED_FLT,P_SLEN,G_SYNC);
    count=0;for(unsigned i=0;i<P_COUNT;i++)if(motion_param(i)){const param_desc_t *d=track_desc(TSEL,i);if(d&&d->max>d->min){J("%s{\"id\":%u,\"label\":",count++?",":"",i);Q(d->label);J(",\"min\":%d,\"max\":%d,\"value\":%d}",d->min,d->max,TSEL->p[i]);}}J("]");
    J(",\"paletteMax\":%u",NPALETTES-1);
    if(n>=cap){out->version=0;b[0]=0;}
    J(",\"keyNotes\":[");for(unsigned k=0;k<27;k++){unsigned note=kb_map(TSEL,k);J("%s%d",k?",":"",note<128?(int)note:-1);}J("]");
#undef J
#undef Q
}
static void studio_snapshot(struct sloop_editor *out){
    memset(out,0,sizeof *out);out->version=1;out->selected=song.sel;out->playing=song.playing;out->recording=song.rec;out->bpm=song.g[G_BPM];out->length_parameter=song.sel*256+P_SLEN;out->length=studio_len(TSEL);out->position=song.playing?TSEL->seq_idx:-1;out->engine=TSEL->eng_req%NENGINES;out->preset=TSEL->preset;out->octave=song.octave;
    for(unsigned i=0;i<NENGINES;i++)if(eng_ok(i))str_cpy(out->engines[i],ENGINES[i]->name,sizeof out->engines[i]);
    const engine_t *engine=ENGINES[out->engine];out->preset_count=engine->npresets<SLOOP_EDITOR_PRESETS?engine->npresets:SLOOP_EDITOR_PRESETS;for(unsigned i=0;i<out->preset_count;i++)str_cpy(out->presets[i],engine->presets[i].name,sizeof out->presets[i]);
    for(unsigned i=0;i<NTRK;i++){out->tracks[i].level=trk[i].p[P_LEVEL];out->tracks[i].mute=trk[i].p[P_MUTE];out->tracks[i].drum=studio_drum(&trk[i]);out->tracks[i].channel=trk_midi_ch(i);str_cpy(out->tracks[i].name,ENGINES[trk[i].eng_req%NENGINES]->name,sizeof out->tracks[i].name);}
    for(unsigned i=0;i<NPAGES&&out->group_count<SLOOP_EDITOR_GROUPS;i++){const page_t *pg=&PAGES[i];if(!page_visible(i)||pg->fam==FAM_SAVE||(pg->scope!=SC_TRACK&&pg->scope!=SC_ENGINE&&pg->scope!=SC_GLOBAL))continue;struct sloop_group *g=&out->groups[out->group_count];str_cpy(g->title,pg->title,sizeof g->title);g->page=i;
        for(unsigned k=0;k<4;k++){unsigned id=pg->id[k];int global=pg->scope==SC_GLOBAL;if(id==255||id>=(global?G_COUNT:P_COUNT))continue;const param_desc_t *d=global?&GP[id]:track_desc(TSEL,id);if(d&&d->max>d->min)editor_param(&g->param[g->count++],global?FM1_GLOBAL_PARAMETER_BASE+id:song.sel*256+id,d,global?song.g[id]:TSEL->p[id]);}if(g->count)out->group_count++;
    }
    out->current.page=ui.home?-1:ui.page;str_cpy(out->current.title,ui.home?"HOME":cur_page()->title,sizeof out->current.title);for(unsigned i=0;i<out->group_count;i++)if(out->groups[i].page==out->current.page)out->current=out->groups[i];
    if(ui.home){for(unsigned k=0;k<4;k++){unsigned id=ENGINES[out->engine]->knob[k];const param_desc_t *d=id<P_COUNT?track_desc(TSEL,id):0;if(d&&d->max>d->min)editor_param(&out->current.param[out->current.count++],song.sel*256+id,d,TSEL->p[id]);}}
    for(unsigned i=0;i<NSTEP;i++){for(unsigned k=0;k<4;k++)out->steps[i][k]=-1;if(studio_drum(TSEL))out->steps[i][0]=TSEL->step[i].hit;else if(TSEL->step[i].time==ST_NOTE)for(unsigned k=0;k<TSEL->step[i].n&&k<4;k++)out->steps[i][k]=TSEL->step[i].note[k];}
    static const char *const lanes[NLANE]={"KICK","SNARE","CLAP","HAT","OPEN HAT","TOM","RIM","BELL"};for(unsigned i=0;i<NLANE;i++)str_cpy(out->lanes[i],lanes[i],sizeof out->lanes[i]);studio_json(out);
}
