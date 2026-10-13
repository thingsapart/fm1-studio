/* SPDX-License-Identifier: GPL-3.0-only */
/* Android semantic operations. Included after the unmodified firmware. */
#include <stdio.h>
#include <stdarg.h>
static uint32_t android_fx,android_soft_down;
static uint8_t android_throw,android_dynamics;
static uint32_t android_edit_session;
static unsigned android_edit_track;
static struct { uint32_t sess; int8_t micro[NSTEP]; uint8_t fill[NSTEP/4]; plock_t lock[NLOCK]; } android_undo;
static int android_editable(void) {
    if (ft_on || (song.playing && arrangement_enabled)) { ui_message("STOP THE SONG FIRST"); return 0; }
    return 1;
}
static void android_mark(track_t *t) {
    uint32_t session=android_edit_session&&android_edit_track==trk_index(t)?android_edit_session:(undo_sess+=4u)|3u;
    if(undo.valid&&!undo.undone&&undo.trk==trk_index(t)&&undo.sess==session)return;
    undo_mark(t,session);
    android_undo.sess = undo.sess;
    memcpy(android_undo.micro,t->micro,sizeof t->micro);
    memcpy(android_undo.fill,t->fill,sizeof t->fill);
    memcpy(android_undo.lock,t->lock,sizeof t->lock);
}
static void android_clear_step(track_t *t,unsigned i) {
    if(!is_drum(t)){for(unsigned j=i+1;j<trk_len(t)&&t->step[j].time==ST_TIE;j++){step_clear(&t->step[j]);lock_del(t,j,P_COUNT);t->micro[j]=0;step_fill_set(t,j,FC_NORM);}}
    step_clear(&t->step[i]); lock_del(t,i,P_COUNT);t->micro[i]=0;step_fill_set(t,i,FC_NORM);
}
static void android_perform_apply(void) {
    if (android_fx) { punch.hold=1; for(int i=26;i>=0;i--)if(android_fx&(1u<<i)){punch.req=i;break;} }
    if(android_throw) thr_req=android_throw;
}
static void android_perform_reset(void) {
    android_fx=0;android_throw=0;android_dynamics=0;android_soft_down=0;android_edit_session=0;punch.hold=0;punch.req=-1;thr_req=0;fill_held=0;ly_lock=LY_PLAY;
    fm1_in.buttons &= ~(dyn_bit[0]|dyn_bit[1]);er_mute=0;for(unsigned i=0;i<NTRK;i++){sus_set(&trk[i],0);trk[i].bend=0;}
}
static int android_soft_key(int key,int down) {
    if(!down&&(android_soft_down&(1u<<key))){android_soft_down&=~(1u<<key);for(unsigned i=0;i<NROLL;i++)if(roll[i].on&&roll[i].key==key&&roll[i].trk==TRK_DRUM)roll_end(i);return 1;}
    if(android_dynamics!=LV_SOFT||song.sel!=TRK_DRUM||!down)return 0;
    unsigned layer=layer_now();if(layer!=LY_PLAY&&layer!=LY_ROLL)return 0;
    android_soft_down|=1u<<key;
    if(layer==LY_ROLL)roll_start(key,TDRUM,lane_of_key(key),LV_SOFT);
    else drum_input(lane_of_key(key),LV_SOFT,0,1);
    return 1;
}
static void android_action(unsigned id,int value) {
    unsigned op=id%256,t=id/256; if(t>=NTRK)return;
    track_t *tr=&trk[t];
    if(op==52){android_edit_session=(undo_sess+=4u)|3u;android_edit_track=t;return;}
    if(op==53){android_edit_session=0;return;}
    if(op==42){if(ft_on)ft_close();return;}
    if(op==43){transport_req=2;return;}
    if(op==40){if(!song.playing&&!ft_on){rec_tempo=value!=0;settings_later=1;}return;}
    if(op==41){if(!song.playing&&!ft_on){rec_count=value!=0;settings_later=1;}return;}
    if(op==50){if(value>=0&&value<DSU_N&&!ft_on){track_select(TRK_DRUM);TDRUM->p[P_E0]=DRUM_SYN+value;}return;}
    if(op==51){studio_open(SC_SONG);return;}
    if(op==49){if(value>=0&&value<4){if(t==TRK_DRUM)tr->p[P_E0]=DRUM_USR+value;else{for(unsigned e=0;e<NENGINES;e++)if(!strcmp(ENGINES[e]->name,"SAMPLE")){set_engine_of(tr,e);tr->p[P_E0]=SMP_NSETS+value;break;}}}return;}
    if(op==47){midi_pedal(0,tr,value?127:0);unsigned ch=trk_midi_ch(t);if(ch<16)midi_out_event(11u|((0xb0u|ch)<<8)|(64u<<16)|((value?127u:0u)<<24));return;}
    if(op==48){unsigned v=clamp(value,0,16383);midi_bend(0,tr,v&127,v>>7);unsigned ch=trk_midi_ch(t);if(ch<16)midi_out_event(14u|((0xe0u|ch)<<8)|((v&127u)<<16)|((v>>7)<<24));return;}
    if(op==46){song.octave=clamp(value,-3,3);return;}
    if(op==44||op==45){for(unsigned i=0;i<NPART;i++)trk[i].p[op==44?P_ROOT:P_SCALE]=clamp(value,0,op==44?11:NSCALES-1);return;}
    if(op==19){if(arrangement_enabled&&song.playing){ui_message("STOP THE SONG FIRST");return;}if(value>=0&&value<4){if(song.playing){live_req=value;chain_n=0;}else section_load(value);}return;}
    if(op>=16&&op<=18){if(song.playing||transport_req){ui_message("STOP FIRST");return;}if(value<0||value>3)return;
        if(op==16)project_load(value);if(op==17)project_save(value);if(op==18)project_erase(value);return;}
    if(op>=32&&op<=34){if(value<0||value>=UP_SLOTS)return;track_select(t);up_ui(op==32?0:op==33?2:1,value);return;}
    if(op==3||op==4){if(!android_editable())return;unsigned ut=undo.trk;
        int swap=undo.valid&&undo.sess==android_undo.sess;
        int done=undo_swap(op==4);ui_message(done?(op==4?"REDO":"UNDO"):"NOTHING TO UNDO");if(done&&swap){track_t *u=&trk[ut];for(unsigned i=0;i<NSTEP;i++){int8_t x=u->micro[i];u->micro[i]=android_undo.micro[i];android_undo.micro[i]=x;}
          for(unsigned i=0;i<NSTEP/4;i++){uint8_t x=u->fill[i];u->fill[i]=android_undo.fill[i];android_undo.fill[i]=x;}
          for(unsigned i=0;i<NLOCK;i++){plock_t x=u->lock[i];u->lock[i]=android_undo.lock[i];android_undo.lock[i]=x;}}
        return;}
    if(!android_editable())return;
    if(op==2){if(song.playing||transport_req){ui_message("STOP FIRST");return;}project_new();android_perform_reset();ui_message("NEW PROJECT");return;}
    if(op==1){if(t<NPART){set_engine_of(tr,tr->eng_req);apply_preset_to(tr,0);}return;}
    if(op>8)return;
    if(op==6){unsigned len=trk_len(tr),locks=0;for(unsigned i=0;i<NLOCK;i++)if(tr->lock[i].step<len)locks++;if(len*2>NSTEP){ui_message("MAX 64 STEPS");return;}if(locks*2>NLOCK){ui_message("LOCK LIMIT");return;}}
    android_mark(tr);
    if(op==0){track_defaults_steps(tr);ui_message("TRACK CLEARED");}
    if(op==5){step_t st[NSTEP];int8_t m[NSTEP];uint8_t f[NSTEP];unsigned len=trk_len(tr);memcpy(st,tr->step,sizeof st);memcpy(m,tr->micro,sizeof m);
      for(unsigned i=0;i<len;i++)f[i]=step_fill(tr,i);
      for(unsigned i=0;i<len;i++){unsigned j=(i+len+(value>=0?1:-1))%len;tr->step[j]=st[i];tr->micro[j]=m[i];step_fill_set(tr,j,f[i]);}
      for(unsigned i=0;i<NLOCK;i++)if(tr->lock[i].step<len)tr->lock[i].step=(tr->lock[i].step+len+(value>=0?1:-1))%len;
    }
    if(op==6||op==7){unsigned len=trk_len(tr);if(op==6&&len*2<=NSTEP){for(unsigned i=0;i<len;i++){tr->step[len+i]=tr->step[i];tr->micro[len+i]=tr->micro[i];step_fill_set(tr,len+i,step_fill(tr,i));}
       unsigned old=NLOCK;for(unsigned i=0;i<old;i++)if(tr->lock[i].step<len)lock_set(tr,len+tr->lock[i].step,tr->lock[i].param,tr->lock[i].val);
       tr->p[P_SLEN]=len*2;}else if(op==7&&len>=2)tr->p[P_SLEN]=len/2;}
    if(op==8&&!is_drum(tr))for(unsigned i=0;i<NSTEP;i++)for(unsigned j=0;j<tr->step[i].n;j++)tr->step[i].note[j]=clamp(tr->step[i].note[j]+value,0,127);
}
static void android_detail(unsigned id,int value) {
    unsigned t=id/65536,i=(id%65536)/1024,l=(id%1024)/32,f=id%32;
    if(t>=NTRK||i>=NSTEP||l>=16||!android_editable())return;track_t *tr=&trk[t];android_mark(tr);
    step_t *s=&tr->step[i];
    if(f==9&&!is_drum(tr)&&s->time==ST_NOTE&&s->n){unsigned to=(unsigned)value/128;int delta=(value%128)-64;if(to>=trk_len(tr))return;
        if(to==i){for(unsigned n=0;n<s->n;n++)s->note[n]=clamp(s->note[n]+delta,1,127);return;}
        unsigned length=note_len(tr,i);if(tr->step[to].n||(tr->step[to].time==ST_TIE&&(to<i||to>=i+length))){ui_message("STEP OCCUPIED");return;}
        step_t copy=*s;int8_t micro=tr->micro[i];unsigned fill=step_fill(tr,i);plock_t locks[NLOCK];memcpy(locks,tr->lock,sizeof locks);
        android_clear_step(tr,i);tr->step[to]=copy;for(unsigned n=0;n<copy.n;n++)tr->step[to].note[n]=clamp(copy.note[n]+delta,1,127);tr->micro[to]=micro;step_fill_set(tr,to,fill);note_set_len(tr,to,length);for(unsigned n=0;n<NLOCK;n++)if(locks[n].step==i)lock_set(tr,to,locks[n].param,locks[n].val);return;}
    if(f==7){android_clear_step(tr,i);return;}
    if(f==3)tr->micro[i]=clamp(value,MICRO_MIN,MICRO_MAX);
    if(f==4)step_fill_set(tr,i,clamp(value,0,2));
    if(is_drum(tr)){if((f==1||f==2)&&dstep_has(&tr->dstep[i],l))dstep_set(&tr->dstep[i],l,f==1?clamp(value,0,3):dstep_lvl(&tr->dstep[i],l),f==2?clamp(value,0,3):dstep_rat(&tr->dstep[i],l));}
    else{if(f==0)s->vel=clamp(value,1,127);if(f==6)s->flags=value&3;
      if((f==1||f==2)&&l<s->n){uint8_t *v=f==1?&s->lvl:&s->rat;*v=(*v&~(3u<<(2*l)))|(clamp(value,0,3)<<(2*l));}
      if(f==5&&s->time==ST_NOTE){track_t *saved=TSEL;unsigned sel=song.sel;song.sel=t;note_set_len(tr,i,clamp(value,1,NSTEP-i));song.sel=sel;(void)saved;}
      if(f==8&&l<s->n)s->note[l]=clamp(value,0,127);
    }
}
static void android_performance(unsigned id,int value) {
    if(id<27){if(value)android_fx|=1u<<id;else android_fx&=~(1u<<id);if(!android_fx){punch.hold=0;punch.req=-1;}return;}
    if(id==27||id==28){unsigned bit=1u<<(id-27);if(value)android_throw|=bit;else android_throw&=~bit;thr_req=android_throw;return;}
    if(id==29)fill_held=value!=0;
    if(id==30&&value)fill_arm=1;
    if(id==31||id==32){ly_lock=value?(id==31?LY_ROLL:LY_ERASE):LY_PLAY;er_mute=0;}
    if(id==33){android_dynamics=clamp(value,0,3);fm1_in.buttons&=~(dyn_bit[0]|dyn_bit[1]);if(value==1)fm1_in.buttons|=dyn_bit[0];if(value==3)fm1_in.buttons|=dyn_bit[1];}
    if(id>=40&&id<44){unsigned b=1u<<(id-40);song.solo=value?song.solo|b:song.solo&~b;}
    if(id>=50&&id<66){unsigned b=1u<<(id-50);drum_mute=value?drum_mute|b:drum_mute&~b;}
    if(id==70&&value)tap_tempo();
    if(id==71)android_perform_reset();
}
static void android_song(unsigned id,int value) {
    unsigned i=id/16,f=id%16;
    if(f==1){android_action(19,value);return;}
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
static void android_advanced(unsigned id,int value);
static int android_extra_event(int kind,unsigned id,int value) {
    if(kind==SLOOP_ADVANCED)android_advanced(id,value);
    else if(kind==SLOOP_ACTION)android_action(id,value);
    else if(kind==SLOOP_DETAIL)android_detail(id,value);
    else if(kind==SLOOP_LOCK){unsigned t=id/16384,i=(id%16384)/256,p=id%256;if(t<NTRK&&i<NSTEP&&p<P_COUNT&&android_editable()){android_mark(&trk[t]);if(value==-32768)lock_del(&trk[t],i,p);else if(!lock_set(&trk[t],i,p,value))ui_message("LOCK LIMIT");}}
    else if(kind==SLOOP_PERFORM)android_performance(id,value);
    else if(kind==SLOOP_SONG)android_song(id,value);
    else if(kind==SLOOP_CONFIG){if(id==0){settings.palette=clamp(value,0,NPALETTES-1);palette_set(settings.palette);}if(id==1)settings.zoom=value!=0;if(id==2){settings.lowcut=value!=0;fx_lowcut=settings.lowcut;}if(id==3)ml_clear_all();if(id==4){mlu.on=value!=0;mlu.pick=0;}if(id==5&&value>=0&&value<P_COUNT)ml_knob(TSEL,value);if(id==6){for(unsigned i=0;i<ML_N;i++)if((ml_tab[i]&127u)==(unsigned)value)ml_put(i,0);}settings_later=1;}
    else return 0;return 1;
}
static void android_json(char *out,size_t size) {
    size_t n=0;
#define J(...) do { if(n<size){int z=snprintf(out+n,size-n,__VA_ARGS__);if(z>0)n+=(size_t)z;} }while(0)
    J(",\"recordState\":%d,\"freeTrack\":%d,\"recTempo\":%d,\"recCount\":%d,\"solo\":%u,\"drumMute\":%u,\"fillOn\":%d,\"layer\":%u,\"undo\":%d,\"redo\":%d,\"undoTrack\":%d,\"songMode\":%d,\"liveSection\":%d,\"queuedSection\":%d,\"songRecording\":%d,\"songLoop\":%d,\"songIndex\":%d,\"palette\":%u,\"zoom\":%u,\"lowcut\":%u,\"learn\":%d,\"message\":\"",ft_on?3:ci_on?2:rec_wait?1:song.rec?4:0,ft_trk,rec_tempo,rec_count,song.solo,drum_mute,fill_now,ly_lock,undo.valid&&!undo.undone,undo.valid&&undo.undone,undo.trk,arrangement_enabled,live_sec,live_req,srec,arrangement.loop,arrangement_clock.index,settings.palette,settings.zoom,settings.lowcut,mlu.on);
    for(const char *p=ui.msg_t?ui.msg:"";*p;p++)if(*p>=32&&*p!='"'&&*p!='\\')J("%c",*p);J("\",\"sections\":[");for(unsigned i=0;i<4;i++)J("%s%d",i?",":"",project_used(i));
    J("],\"arrangement\":[");for(unsigned i=0;i<arrangement.count;i++)J("%s[%u,%u]",i?",":"",arrangement.entry[i].scene,arrangement.entry[i].bars);
    J("],\"chain\":[");for(unsigned i=0;i<chain_n;i++)J("%s%u",i?",":"",chain_sec[i]);
    J("],\"userSounds\":[");for(unsigned i=0;i<UP_SLOTS;i++){char b[16];up_name(i,b);J("%s{\"used\":%d,\"name\":\"",i?",":"",up_used(i));for(char *p=b;*p;p++)if(*p!='"'&&*p!='\\')J("%c",*p);J("\"}");}
    J("],\"detail\":[");for(unsigned i=0;i<NSTEP;i++){track_t *t=TSEL;step_t *s=&t->step[i];J("%s{\"time\":%u,\"velocity\":%u,\"flags\":%u,\"nudge\":%d,\"fill\":%u,\"length\":%u,\"levels\":[",i?",":"",s->time,s->vel,s->flags,t->micro[i],step_fill(t,i),is_drum(t)?1:note_len(t,i));
      for(unsigned l=0;l<(is_drum(t)?16u:4u);l++)J("%s%u",l?",":"",is_drum(t)?dstep_lvl(&t->dstep[i],l):(s->lvl>>(l*2))&3u);
      J("],\"ratchets\":[");for(unsigned l=0;l<(is_drum(t)?16u:4u);l++)J("%s%u",l?",":"",is_drum(t)?dstep_rat(&t->dstep[i],l):(s->rat>>(l*2))&3u);
      J("],\"locks\":[");int c=0;for(unsigned l=0;l<NLOCK;l++)if(t->lock[l].step==i)J("%s[%u,%d]",c++?",":"",t->lock[l].param,t->lock[l].val);J("]}");}
    J("],\"punchNames\":[");for(unsigned i=0;i<PUNCH_NFX;i++)J("%s\"%s\"",i?",":"",PUNCH_NAME[i]);
    J("],\"samples\":[");for(unsigned i=0;i<4;i++)J("%s%d",i?",":"",usr_nz[i]);
    J("],\"catalogue\":[");int presets=0;for(unsigned e=0;e<NENGINES;e++)for(unsigned i=0;i<ENGINES[e]->npresets;i++){J("%s{\"engine\":%u,\"preset\":%u,\"name\":\"",presets++?",":"",e,i);for(const char *p=ENGINES[e]->presets[i].name;*p;p++)if(*p!='"'&&*p!='\\')J("%c",*p);J("\"}");}
    J("],\"midiLearn\":[");int learned=0;for(unsigned i=0;i<ML_N;i++){unsigned e=ml_tab[i],code=e>>9;if(code){unsigned id=code>=ML_E0?P_E0+code-ML_E0:code-1;J("%s[%u,%u,%u,\"%s\"]",learned++?",":"",e&127u,(e>>7)&3u,id,id<P_COUNT?track_desc(&trk[(e>>7)&3u],id)->label:"PARAMETER");}}
    J("],\"fm6\":[");for(unsigned i=0;i<FP_SIZE;i++)J("%s[%u,%u]",i?",":"",fm6_patch[song.sel<NPART?song.sel:0][i],fm6_max(i));
    J("],\"fm6BankUsed\":%u,\"synthKits\":[",fm6_bank_used);for(unsigned k=0;k<DSU_N;k++){const dsu_kit_t *kit=dsu_kit(k);J("%s{\"name\":\"",k?",":"");for(unsigned i=0;i<8&&kit->name[i];i++)if(kit->name[i]!='"'&&kit->name[i]!='\\')J("%c",kit->name[i]);J("\",\"crush\":%u,\"sounds\":[",kit->crush);for(unsigned l=0;l<16;l++){J("%s[",l?",":"");const uint8_t *d=(const void*)&kit->s[l];for(unsigned i=0;i<22;i++)J("%s%d",i?",":"",i==17?(int8_t)d[i]:d[i]);J("]");}J("]}");}
    J("],\"synthFactory\":[");for(unsigned i=0;i<DS_NKITS;i++)J("%s\"%s\"",i?",":"",DS_KITS[i].name);
    J("],\"userSlot\":%d,\"sustain\":%d,\"bend\":%d,\"division\":%d,\"sounding\":%u,\"dynamics\":%u,\"ids\":{\"root\":%d,\"scale\":%d,\"filter\":%d,\"length\":%d,\"roll\":%d,\"drumChannel\":%d,\"sync\":%d},\"lockParameters\":[",TSEL->user?(int)TSEL->user-1:-1,sus_on[song.sel],TSEL->bend+8192,TSEL->p[P_SDIV],keys_sounding(TSEL),android_dynamics,P_ROOT,P_SCALE,P_TFLT,P_SLEN,G_ROLL,G_DRCH,G_SYNC);
    int c=0;for(unsigned i=0;i<P_COUNT;i++)if(p_lockable(i)){const param_desc_t *d=track_desc(TSEL,i);if(d&&d->max>d->min)J("%s{\"id\":%u,\"label\":\"%s\",\"min\":%d,\"max\":%d,\"value\":%d}",c++?",":"",i,d->label,d->min,d->max,TSEL->p[i]);}J("]");
#undef J
}

static void android_advanced(unsigned id,int value) {
    if(id<NPART*256){unsigned t=id/256,p=id%256;if(p<FP_SIZE){uint8_t patch[FP_SIZE+1];memcpy(patch,fm6_patch[t],sizeof patch);patch[p]=clamp(value,0,fm6_max(p));fm6_set_patch(t,patch);fm6_slot[t]=trk[t].p[P_E7];}return;}
    if(id>=4096&&id<4096+DSU_N*512){unsigned k=(id-4096)/512,l=((id-4096)%512)/32,p=id%32;if(l<16&&p<22){dsu_kit(k);uint8_t *d=(uint8_t *)&dsu.k[k].s[l];d[p]=p==17?(uint8_t)(int8_t)clamp(value,-128,127):clamp(value,0,255);dsu_fix_sound(&dsu.k[k].s[l]);dsu_dirty=1;}return;}
    if(id>=8192&&id<8192+FM6_BANK_N){if(song.playing||transport_req){ui_message("STOP FIRST");return;}uint8_t pk[FM6_PACKED];if(value>=0&&value<NPART){fm6_pack(fm6_patch[value],pk);fm6_bank_put(id-8192,pk);}else if(value==-1)fm6_bank_put(id-8192,NULL);return;}
    if(id>=9000&&id<9000+DSU_N){if(value>=0&&value<DS_NKITS){dsu_from_factory(id-9000,value);dsu_dirty=1;}return;}
    if(id>=9100&&id<9100+DSU_N){if(value>=0&&value<16){dsu_aud_k=id-9100;dsu_aud_lane=value;dsu_aud_vel=100;}return;}
    if(id>=9400&&id<9404){dsu_kit(id-9400);dsu.k[id-9400].crush=clamp(value,0,255);dsu_dirty=1;return;}
    if(id==9200){if(song.playing||transport_req){ui_message("STOP FIRST");return;}settings_save();return;}
    if(id>=9300&&id<9304){if(song.playing||transport_req){ui_message("STOP FIRST");return;}unsigned k=id-9300;memset((void *)SMP_USER_XIP(k),255,SMP_USER_SIZE);smp_user_scan(k);storage_dirty=1;return;}
}
int sloop_object_import(int kind,int id,const uint8_t *data,unsigned size) {
    if(song.playing||transport_req||ft_on)return 0;
    if(kind==5&&size==4096){for(unsigned i=0;i<FM6_BANK_N;i++){uint8_t patch[FP_SIZE+1],packed[FM6_PACKED];fm6_unpack(data+i*FM6_PACKED,patch);fm6_pack(patch,packed);if(fm6_bank_put(i,packed))return 0;}return 1;}
    if(kind==6&&id>=0&&id<NTRK&&size==652){uint32_t magic;memcpy(&magic,data,4);if(magic!=0x3154504du||data[4]<1||data[4]>64||data[5]!=(id==TRK_DRUM)||data[6]>=NDIV_STEP)return 0;step_t steps[NSTEP];memcpy(steps,data+12,sizeof steps);if(id!=TRK_DRUM)for(unsigned i=0;i<NSTEP;i++){if(steps[i].n>4||steps[i].time>ST_REST)return 0;for(unsigned j=0;j<steps[i].n;j++)if(steps[i].note[j]>127)return 0;}
        android_mark(&trk[id]);track_defaults_steps(&trk[id]);memcpy(trk[id].step,steps,sizeof steps);trk[id].p[P_SLEN]=data[4];trk[id].p[P_SDIV]=data[6];song.g[G_BPM]=clamp(data[8]|data[9]<<8,40,240);return 1;}
    if(kind==0&&id>=0&&id<UP_SLOTS&&size==sizeof(up_rec_t)){const up_rec_t *r=(const void*)data;if(!up_valid(r))return 0;return !up_put(id,r);}
    if(kind==1&&id>=0&&id<NPART&&(size==FM6_PACKED||size==FP_SIZE)){uint8_t p[FP_SIZE+1];if(size==FM6_PACKED)fm6_unpack(data,p);else{memcpy(p,data,size);fm6_sanitize(p);}fm6_set_patch(id,p);fm6_slot[id]=trk[id].p[P_E7];return 1;}
    if(kind==2&&id>=0&&id<DSU_N&&size==sizeof(dsu_kit_t)){dsu_kit(id);memcpy(&dsu.k[id],data,size);dsu_fix_kit(&dsu.k[id]);dsu_dirty=1;return 1;}
    if(kind==3&&id>=0&&id<4)return sloop_import_sample(id,data,size);
    if(kind==4&&id>=0&&id<4){return !project_restore(id,data,size);}
    /* Text operations are printable ASCII, checked before changing storage. */
    if(kind>=10&&size<=12){char name[13]={0};for(unsigned i=0;i<size;i++){if(data[i]<32||data[i]>126)return 0;name[i]=data[i];}
        if(kind==10&&id>=0&&id<UP_SLOTS&&up_used(id)){up_rec_t r=*up_rec(id);memset(r.name,0,sizeof r.name);memcpy(r.name,name,size);return !up_put(id,&r);}
        if(kind==11&&id>=0&&id<DSU_N&&size<=8){dsu_kit(id);memset(dsu.k[id].name,0,8);memcpy(dsu.k[id].name,name,size);dsu_dirty=1;return 1;}
        if(kind==12&&id>=0&&id<NPART&&size<=10){uint8_t p[FP_SIZE+1];memcpy(p,fm6_patch[id],sizeof p);memset(p+FP_NAME,' ',10);memcpy(p+FP_NAME,name,size);fm6_set_patch(id,p);return 1;}
    }
    return 0;
}
unsigned sloop_object_export(int kind,int id,uint8_t *out,unsigned capacity) {
    if(kind==5&&capacity>=4096){for(unsigned i=0;i<32;i++){if(i<FM6_BANK_N&&fm6_bank_read&&!fm6_bank_read(i,out+i*128))continue;memcpy(out+i*128,FM6_FACTORY[0],128);}return 4096;}
    if(kind==0&&id>=0&&id<UP_SLOTS&&up_used(id)&&capacity>=sizeof(up_rec_t)){memcpy(out,up_rec(id),sizeof(up_rec_t));return sizeof(up_rec_t);}
    if(kind==1&&id>=0&&id<NPART&&capacity>=FM6_PACKED){fm6_pack(fm6_patch[id],out);return FM6_PACKED;}
    if(kind==2&&id>=0&&id<DSU_N&&capacity>=sizeof(dsu_kit_t)){memcpy(out,dsu_kit(id),sizeof(dsu_kit_t));return sizeof(dsu_kit_t);}
    if(kind==3&&id>=0&&id<4&&usr_nz[id]){const smp_user_hdr_t *h=(const void*)SMP_USER_XIP(id);unsigned n=SMP_USER_DATA+h->data_len;if(n<=capacity){memcpy(out,h,n);return n;}}
    if(kind==4&&id>=0&&id<4&&project_used(id)&&capacity>=sizeof(project_t)){memcpy(out,&proj_slot[id],sizeof(project_t));return sizeof(project_t);}
    return 0;
}
int sloop_validate_image(const uint8_t *data,unsigned size) {
    if(size!=SLOOP_STORAGE_SIZE)return 0;unsigned found=0;
    for(unsigned obj=0;obj<OBJ_COUNT;obj++){int present=0,valid=0;for(unsigned copy=0;copy<2;copy++){unsigned off=st_sector(obj,copy);st_hdr_t h;memcpy(&h,data+off,sizeof h);int blank=1;for(unsigned j=0;j<sizeof h;j++)if(data[off+j]!=255)blank=0;if(!blank)present=1;if(h.magic!=ST_MAGIC)continue;
       if(h.type!=obj||h.slot!=copy||h.len>ST_PAYLOAD_MAX||h.hcrc!=st_crc32(&h,sizeof h-4)||h.crc!=st_crc32(data+off+ST_PAYLOAD_OFF,h.len))continue;
       if(h.len&&(obj==OBJ_AUTOSAVE||(obj>=OBJ_PROJECT0&&obj<OBJ_UPRESET0))){project_t p;if(!proj_import(&p,data+off+ST_PAYLOAD_OFF,h.len))continue;}
       valid=1;}
      if(present&&!valid)return 0;found+=valid;
    }
    for(unsigned k=0;k<4;k++){const smp_user_hdr_t *h=(const void *)(data+SMP_USER_OFF(k));if(h->magic!=SMP_USER_MAGIC)continue;if(h->version!=1||!h->nz||h->nz>16||h->data_len>SMP_USER_SIZE-SMP_USER_DATA||h->crc!=st_crc32((const uint8_t*)h+SMP_USER_DATA,h->data_len))return 0;for(unsigned z=0;z<h->nz;z++)if(!smp_zone_ok(&h->zone[z],h->data_len))return 0;found++;}
    return found!=0;
}
