/* SPDX-License-Identifier: GPL-3.0-only */
/* Absolute host/JP-1 controls. IDs retain pad, page and knob across delayed edits.
 * Action knobs (COPY, CLEAR, RESET) stay on the native confirmation workflow. */
static int studio_preset_wave(unsigned index){for(unsigned i=0;i<64;i++)if(sp_wave[i].n){if(!index--)return i;}return -1;}
static int studio_parameter(unsigned pad,unsigned pg,unsigned k,int write,int value,int *lo,int *hi){
    *lo=*hi=0;if(pad>=32||pg>=PG_N)return 0;sp_sound_t *s=&sp_sound[pad];
#define V(field,l,h) do{*lo=(l);*hi=(h);if(write)(field)=sp_clamp(value,*lo,*hi);return (field);}while(0)
#define FLAG(mask) do{*hi=1;if(write)s->flags=value?s->flags|(mask):s->flags&~(mask);return !!(s->flags&(mask));}while(0)
    switch(pg){
    case PG_HOME:if(k<8){*hi=127;if(write)ui.mix[k]=sp_mix[k]=sp_clamp(value,0,127);return ui.mix[k];}break;
    case PG_WAVE:
        if(k==0){*hi=63;if(write&&value>=0&&value<64&&sp_wave[value].n)wave_set(pad,value);return s->wave;}
        if(k==1)V(ui.copy_pad,0,31);break;
    case PG_SOUND:
        if(k==0)V(s->tune,-24,12);if(k==1)V(s->fine,-50,50);if(k==2)V(s->decay,0,127);if(k==3)V(s->level,0,127);break;
    case PG_TRUNC:
        if(k==0)V(s->start,0,s->end-10);if(k==1)V(s->end,s->start+10,1000);
        if(k==2)FLAG(SPF_REVERSE);if(k==3)FLAG(SPF_33);break;
    case PG_OUT:
        if(k==0){*hi=8;if(write)s->flags=SPF_CHOP_SET(s->flags,sp_clamp(value,0,8));return SPF_CHOP(s->flags);}
        if(k==1)V(s->pan,-64,63);if(SP_CH_OF(pad)>=2)break;
        if(k==2)V(s->cut,0,127);if(k==3)V(s->reso,0,127);break;
    case PG_SFX:if(k==0)V(s->drive,0,127);if(k<4)V(s->send[k-1],0,127);break;
    case PG_FILT:if(k==0)V(djf.v,-64,63);if(k==1)V(djf.res,0,127);break;
    case PG_CHO:if(k==0)V(fxp.crate,0,127);if(k==1)V(fxp.cdepth,0,127);if(k==2)V(fxp.cmix,0,127);break;
    case PG_DLY:if(k==0)V(fxp.dtime,0,5);if(k==1)V(fxp.fdbk,0,120);if(k==2)V(fxp.colr,0,127);if(k==3)V(fxp.dmix,0,127);break;
    case PG_REV:if(k==0)V(fxp.size,0,127);if(k==1)V(fxp.damp,0,127);if(k==2)V(fxp.pre,0,90);break;
    case PG_SEG:
        if(k==0){*hi=15;if(write)ui_loop(sp_clamp(value,0,15));return sq.seg;}
        if(k==1){*hi=32;if(write){sq_seg[sq.seg].bars=sp_clamp(value,0,32);sq.gen++;}return sq_seg[sq.seg].bars;}
        if(k==2)V(sq.quant,0,6);if(k==3)V(sq.swing,0,5);break;
    case PG_SEG2:if(k==1)V(ui.copy_to,0,15);break;
    case PG_SETUP:if(k==0)V(sq.bpm10,400,2400);if(k==1)V(ui.back,0,4);break;
    case PG_CLICK:if(k==0)V(sq.click,0,2);if(k==1)V(sq.cin_bars,0,2);if(k==2)V(sq.dub_bar,0,1);break;
    case PG_OUTPUT:if(k==0)V(sp_smooth,0,1);break;
    }
#undef FLAG
#undef V
    return 0;
}
static void studio_groups(struct sloop_editor *out){
    unsigned saved=ui.page;char labels[4][8],values[4][12];
    for(unsigned pg=0;pg<PG_N;pg++){
        ui.page=pg;page_cols(labels,values);struct sloop_group *g=&out->groups[out->group_count];
        snprintf(g->title,sizeof g->title,"%s",PG_NAME[pg]);g->page=pg;
        for(unsigned k=0;k<4;k++){
            int lo,hi;unsigned field=pg==PG_HOME&&ui.shift?k+4:k;
            int value=studio_parameter(ui.sel,pg,field,0,0,&lo,&hi);if(hi<=lo||value<lo||value>hi||!labels[k][0])continue;
            struct sloop_parameter *p=&g->param[g->count++];p->id=ui.sel*256+pg*8+field;p->min=lo;p->max=hi;p->value=value;
            snprintf(p->label,sizeof p->label,"%s",labels[k]);snprintf(p->text,sizeof p->text,"%s",values[k]);
            if(pg==PG_WAVE&&k==0){size_t n=0;for(unsigned wave=0;wave<64;wave++)if(sp_wave[wave].n){
                n+=snprintf(p->choices+n,sizeof p->choices-n,"%s%s",p->choice_count?"\t":"",wave_name(wave));p->choice_values[p->choice_count++]=wave;
            }}
        }
        if(g->count){if(pg==saved)out->current=*g;out->group_count++;}
    }
    ui.page=saved;
    /* Bank C has an explicit native loop/quantize/swing page. */
    for(unsigned i=0;i<out->group_count;i++)if(out->groups[i].page==PG_SEG){
        out->groups[out->group_count]=out->groups[i];out->groups[out->group_count].page=-2;snprintf(out->groups[out->group_count++].title,24,"PATTERN");break;
    }
}
