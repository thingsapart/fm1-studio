/* SPDX-License-Identifier: GPL-3.0-only */
/* Semantic host controls for zp12; native panel retains its complete workflow. */
/* The host mixer selection stays fixed while native pads select their sounds.
 * This keeps a pad hit from releasing held panel buttons through host navigation. */
static unsigned studio_channel;
static void wrapper_event(int kind,int id,int value){
    static const unsigned buttons[]={B_FX,B_SEL,B_ENV,B_LFO,B_EDIT,B_GLO,B_HOME,B_SAVE,B_ARP,B_SEQ,B_PLAY,B_REC,B_OCTDN,B_OCTUP};
    static const unsigned encoders[]={0,1,6,2,3,4,5};
    if(kind==FM1_BUTTON&&id>=0&&id<14)sim_button(buttons[id],value!=0);
    else if(kind==FM1_ENCODER&&id>=0&&id<7){sim_enc(encoders[id],sp_clamp(value,-32,32));ui.force=1;}
    else if(kind==FM1_KEY&&id>=0&&id<27)sim_key(id,value!=0);
    else if(kind==FM1_MIDI){unsigned p=(unsigned)value;unsigned st=p>>8&255;
        if(st==0xfa||st==0xfb)sq_post(RQ_PLAY,0,0);else if(st==0xfc)sq_post(RQ_STOP,0,0);
        else if((st&240)==0xb0&&((p>>16&127)==120||(p>>16&127)==123))memset(sp_ch,0,sizeof sp_ch);
        else sim_midi(st,p>>16&127,p>>24&127);}
    else if(kind==FM1_MASTER)sim_master((unsigned)sp_clamp(value,0,4096)*1023/4096);
    else if(kind==FM1_STOP)sq_post(RQ_STOP,0,0);
    else if(kind==FM1_PANIC){sq_rq_r=sq_rq_w;sq_post(RQ_STOP,0,0);memset(sp_ch,0,sizeof sp_ch);memset((void *)&fm1_in,0,sizeof fm1_in);key_new=key_rel_late=btn_rel_late=0;pn.req=-1;sp_thr_req=0;}
    else if(kind>=72&&kind<=91){unsigned t=(unsigned)id;
        switch(kind-64){
        case SLOOP_SELECT:if(t<8){studio_channel=t;ui.sel=(ui.sel/8)*8+t;}break;
        case SLOOP_PARAM:{int lo,hi;unsigned pad=t/256,pg=t%256/8,k=t%8;
            studio_parameter(pad,pg,k,1,value,&lo,&hi);if(hi>lo){ui.turn_pad=pad;ui.turn_ms=fm1_ms;ui.touch_ms=fm1_ms;}break;}
        case SLOOP_ADVANCED:
            if(t==10000&&value>=0&&value<32){ui.sel=value;studio_channel=value%8;}
            if(t==10001&&value>=0&&value<16){sq_post(RQ_STEP,ui.sel,ui.step_bar*SQ_BAR+value*24);}
            break;
        case SLOOP_ACTION:if(t%256==3||t%256==4)sq_post(RQ_UNDO,0,0);break;
        case SLOOP_PERFORM:
            if(t>=40&&t<48){unsigned bit=1u<<(t-40);sp_solo=value?sp_solo|bit:sp_solo&~bit;}
            if(t>=50&&t<66){unsigned bit=1u<<((ui.sel/16*16+t-50)%8);sp_mute=value?sp_mute|bit:sp_mute&~bit;}
            break;
        case SLOOP_GLOBAL:if(id==0)sq.bpm10=(uint16_t)sp_clamp(value*10,400,2400);break;
        case SLOOP_MIX:if(t<8)ui.mix[t]=sp_mix[t]=sp_clamp(value,0,127);break;
        case SLOOP_MUTE:if(t<8)sp_mute=value?sp_mute|(1u<<t):sp_mute&~(1u<<t);break;
        case SLOOP_PRESET:if(value>=0){int wave=studio_preset_wave(value);if(wave>=0)wave_set(ui.sel,wave);}break;
        case SLOOP_NOTE:if(value&&t%128>=36&&t%128<68)pad_hit(t%128-36,sp_clamp(value,1,127));break;
        case SLOOP_TRANSPORT:sq_post(value?RQ_PLAY:RQ_STOP,0,0);break;
        case SLOOP_RECORD:if((value!=0)!=((sq.recording||sq.rec_arm)!=0))sq_post(RQ_REC,0,0);break;
        case SLOOP_EDITOR_HOME:page(PG_HOME);ui.steps=0;break;
        case SLOOP_PAGE:if(t<PG_N){page(t);ui.steps=0;}break;
        }
        ui.force=1;
    }
}
static void studio_encoder_text(char *out,size_t cap,size_t *n,const char *text){
    if(*n+2>=cap)return;out[(*n)++]='"';
    for(const unsigned char *p=(const void *)text;*p&&*n+3<cap;p++){
        if(*p=='"'||*p=='\\')out[(*n)++]='\\';
        if(*p>=32)out[(*n)++]=*p;
    }
    out[(*n)++]='"';out[*n]=0;
}
static void studio_snapshot(struct sloop_editor *out){
    memset(out,0,sizeof *out);out->version=1;out->selected=studio_channel;
    out->playing=sq.playing;out->recording=(sq.recording||sq.rec_arm||sq.dub_wait)?255:0;out->bpm=sq.bpm10/10;
    out->length=16;int position=(int)((sq.pos>>16)/24)-(int)ui.step_bar*16;out->position=sq.playing&&position>=0&&position<16?position:-1;
    out->preset=0;out->preset_count=0;
    snprintf(out->engines[0],sizeof out->engines[0],"12-BIT SAMPLER");
    for(unsigned i=0;i<64;i++)if(sp_wave[i].n){if(i==sp_sound[ui.sel].wave)out->preset=out->preset_count;snprintf(out->presets[out->preset_count++],sizeof out->presets[0],"%s",wave_name(i));}
    for(unsigned i=0;i<8;i++){out->tracks[i].level=ui.mix[i];out->tracks[i].mute=sp_mute>>i&1;out->tracks[i].drum=1;snprintf(out->tracks[i].name,sizeof out->tracks[i].name,"CHANNEL %u",i+1);}
    for(unsigned i=0;i<64;i++){out->steps[i][0]=0;for(unsigned j=1;j<4;j++)out->steps[i][j]=-1;}
    for(unsigned i=0;i<16;i++){unsigned pad=ui.sel/16*16+i;char label[3];pad_label(label,pad);snprintf(out->lanes[i],sizeof out->lanes[i],"%s %s",label,wave_name(sp_sound[pad].wave));}
    for(unsigned i=0;i<sq_seg[sq.seg].n;i++){const sq_ev_t *e=&sq_seg[sq.seg].ev[i];unsigned tick=e->t;
        if(tick>=ui.step_bar*SQ_BAR&&tick<(ui.step_bar+1)*SQ_BAR&&e->pad/16==ui.sel/16)out->steps[(tick-ui.step_bar*SQ_BAR)/24][0]|=1u<<(e->pad%16);}

    snprintf(out->current.title,sizeof out->current.title,"%s",PG_NAME[ui.page]);out->current.page=ui.page;
    studio_groups(out);
    snprintf(out->extra,sizeof out->extra,",\"nativeScreen\":%u,\"recordState\":%u,\"recTempo\":0,\"recCount\":0,\"division\":4,\"drumMute\":%u,\"solo\":%u,\"keyNotes\":[-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1],\"detail\":[],\"arrangement\":[],\"songLanes\":[],\"samples\":[],\"sections\":[],\"userSounds\":[],\"catalogue\":[],\"punchNames\":[],\"ids\":{},\"palette\":0,\"paletteMax\":0,\"message\":\"zp12: use PANEL for pads, loops and sound editing\"",ui.page,sq.countin>0?2:sq.rec_arm||sq.dub_wait?1:sq.recording?4:0,sp_mute,sp_solo);
    size_t n=strlen(out->extra);unsigned active=0;for(unsigned i=0;i<SP_NCH;i++)if(sp_ch[i].on)active|=1u<<sp_ch[i].pad;
    n+=snprintf(out->extra+n,sizeof out->extra-n,",\"padActivity\":%u,\"stepBar\":%u",active,ui.step_bar);
    n+=snprintf(out->extra+n,sizeof out->extra-n,",\"pad\":%u,\"padNames\":[",ui.sel);
    for(unsigned i=0;i<32;i++){char name[24],label[3];pad_label(label,i);snprintf(name,sizeof name,"%s %s",label,wave_name(sp_sound[i].wave));if(i)out->extra[n++]=',';studio_encoder_text(out->extra,sizeof out->extra,&n,name);}
    n+=snprintf(out->extra+n,sizeof out->extra-n,"]");
    n+=snprintf(out->extra+n,sizeof out->extra-n,",\"liveSection\":%u,\"keyLabels\":[",sq.seg);
    for(unsigned k=0;k<27;k++){
        unsigned pad=key_pad(k);char name[3]="";if(pad<32)pad_label(name,pad);
        n+=snprintf(out->extra+n,sizeof out->extra-n,"%s\"%s\"",k?",":"",name);
    }
    n+=snprintf(out->extra+n,sizeof out->extra-n,"]");
    char labels[4][8],values[4][12],tempo[16],pad[3];page_cols(labels,values);
    snprintf(tempo,sizeof tempo,"%u.%u BPM",sq.bpm10/10,sq.bpm10%10);pad_label(pad,ui.sel);
    const char *names[7]={"TEMPO","PAD","SAMPLE",labels[0],labels[1],labels[2],labels[3]};
    const char *texts[7]={tempo,pad,wave_name(sp_sound[ui.sel].wave),values[0],values[1],values[2],values[3]};
    for(unsigned row=0;row<2;row++){
        n+=snprintf(out->extra+n,sizeof out->extra-n,",\"%s\":[",row?"encoderValues":"encoderLabels");
        for(unsigned i=0;i<7;i++){
            if(i)out->extra[n++]=',';
            studio_encoder_text(out->extra,sizeof out->extra,&n,row?texts[i]:names[i]);
        }
        n+=snprintf(out->extra+n,sizeof out->extra-n,"]");
    }
}
static int studio_import(int kind,int id,const uint8_t *data,unsigned n){(void)kind;(void)id;(void)data;(void)n;return 0;}
static unsigned studio_export(int kind,int id,uint8_t *data,unsigned n){(void)kind;(void)id;(void)data;(void)n;return 0;}
static int studio_sample(unsigned slot,const uint8_t *data,unsigned n){(void)slot;(void)data;(void)n;return 0;}
static int studio_validate(const uint8_t *data,unsigned n){
    if(!data||n!=sizeof sim_flash)return 0;
    for(unsigned i=0;i<2;i++){
        zs_head_t h;unsigned off=ZS_BASE+i*ZS_SLOT;memcpy(&h,data+off,sizeof h);
        if(h.magic==ZS_MAGIC&&h.ver>=1&&h.ver<=ZS_VER&&h.len<=ZS_SLOT-sizeof h&&
           zs_crc(data+off+sizeof h,h.len)==h.crc)return 1;
    }
    return 0;
}
static const struct fm1_studio_api studio_api={FM1_STUDIO_ABI,sizeof(struct fm1_studio_api),studio_snapshot,studio_import,studio_export,studio_sample,studio_validate};
__attribute__((visibility("default"))) const struct fm1_studio_api *fm1_get_studio_api(void){return &studio_api;}
