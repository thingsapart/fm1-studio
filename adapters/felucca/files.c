static int smp_zone_ok(const smp_zone_t *z,unsigned bytes){return z->n&&z->off<=bytes&&z->n<=2u*SMP_USER_SIZE&&(z->n+1u)/2u<=bytes-z->off&&z->idx<=88u&&z->rate&&z->rate<=(4u<<16)&&z->ls<=z->le&&z->le<z->n&&z->lo<=z->hi&&z->hi<=127u;}
/* SPDX-License-Identifier: GPL-3.0-only */
static int studio_sample(unsigned slot,const uint8_t *data,unsigned size){
    if(song.playing||transport_req||slot>=SMP_USER_SLOTS||size<512||size>SMP_USER_SIZE)return 0;const smp_user_hdr_t *h=(const void *)data;
    if(h->magic!=SMP_USER_MAGIC||h->version!=1||!h->nz||h->nz>16||h->data_len>SMP_USER_SIZE-SMP_USER_DATA||size!=SMP_USER_DATA+h->data_len||h->crc!=st_crc32(data+SMP_USER_DATA,h->data_len))return 0;
    for(unsigned i=0;i<h->nz;i++)if(!smp_zone_ok(&h->zone[i],h->data_len))return 0;
    memset((void *)smp_user_xip(slot),255,SMP_USER_SIZE);memcpy((void *)smp_user_xip(slot),data,size);smp_user_scan(slot);web_flash_writes++;return 1;
}
static int studio_import(int kind,int id,const uint8_t *data,unsigned size){
    if(song.playing||transport_req||id<0)return 0;
    if(kind==3)return studio_sample(id,data,size);
    if(kind==0&&id<UP_SLOTS&&size==sizeof(up_rec_t)){const up_rec_t *record=(const void *)data;if(!up_valid(record))return 0;return !up_put(id,record);}
    if(kind==1&&id<NTRK&&(size==FM6_PACKED||size==FP_SIZE)){uint8_t patch[FP_SIZE+1];if(size==FM6_PACKED)fm6_unpack(data,patch);else{memcpy(patch,data,size);fm6_sanitize(patch);}fm6_set_patch(id,patch);fm6_adopt(id);return 1;}
    if(kind==4&&id<4){if(!proj_import(&proj_scratch,data,size)||!proj_pack(&proj_wire,&proj_scratch)||st_save(OBJ_PROJECT0+id,&proj_wire,sizeof proj_wire))return 0;memcpy(&proj_slot[id],&proj_wire,sizeof proj_wire);return 1;}
    if(kind==6&&id<NTRK&&size==652){uint32_t magic;memcpy(&magic,data,4);if(magic!=0x3154504du||data[4]<1||data[4]>64||data[5]!=studio_drum(&trk[id])||data[6]>=NELEM(N_DIV))return 0;
        step_t steps[NSTEP];memset(steps,0,sizeof steps);for(unsigned i=0;i<NSTEP;i++){const uint8_t *s=data+12+i*10;step_t *d=&steps[i];if(studio_drum(&trk[id])){uint32_t hit=s[0];for(unsigned l=0;l<NLANE;l++)if(hit&(1u<<l)){d->hit|=1u<<l;if(((s[2+l/4]>>((l%4)*2))&3)==3)d->acc|=1u<<l;}d->time=d->hit?ST_NOTE:ST_REST;d->vel=100;}else{if(s[4]>4||s[5]>ST_REST)return 0;for(unsigned j=0;j<s[4];j++)if(s[j]>127)return 0;memcpy(d->note,s,4);d->n=s[4];d->time=s[5];d->flags=s[6]&3;d->vel=s[7];}}
        load_begin(&trk[id],UNDO_PAT);memcpy(trk[id].step,steps,sizeof steps);motion_clear(&trk[id]);trk[id].p[P_SLEN]=data[4];trk[id].p[P_SDIV]=data[6];song.g[G_BPM]=clamp(data[8]|data[9]<<8,40,240);load_end(&trk[id]);return 1;}
    if(kind>=10&&size<=12){char name[13]={0};for(unsigned i=0;i<size;i++){if(data[i]<32||data[i]>126)return 0;name[i]=data[i];}
        if(kind==10&&id<UP_SLOTS&&up_used(id)){up_rec_t record=*up_rec(id);memset(record.name,0,sizeof record.name);memcpy(record.name,name,size);return !up_put(id,&record);}
        if(kind==12&&id<NTRK&&size<=10){uint8_t patch[FP_SIZE+1];memcpy(patch,fm6_patch[id],sizeof patch);memset(patch+FP_NAME,' ',10);memcpy(patch+FP_NAME,name,size);fm6_set_patch(id,patch);fm6_adopt(id);return 1;}
    }return 0;
}
static unsigned studio_export(int kind,int id,uint8_t *out,unsigned capacity){
    if(id<0)return 0;
    if(kind==0&&id<UP_SLOTS&&up_used(id)&&capacity>=sizeof(up_rec_t)){memcpy(out,up_rec(id),sizeof(up_rec_t));return sizeof(up_rec_t);}
    if(kind==1&&id<NTRK&&capacity>=FM6_PACKED){fm6_pack(fm6_patch[id],out);return FM6_PACKED;}
    if(kind==3&&id<SMP_USER_SLOTS&&usr_nz[id]){const smp_user_hdr_t *h=(const void *)smp_user_xip(id);unsigned n=SMP_USER_DATA+h->data_len;if(n<=capacity){memcpy(out,h,n);return n;}}
    if(kind==4&&id<4&&project_used(id)&&capacity>=sizeof(project_store_t)){memcpy(out,&proj_slot[id],sizeof(project_store_t));return sizeof(project_store_t);}
    return 0;
}
static int studio_validate(const uint8_t *data,unsigned size){
    if(size!=sizeof nor)return 0;unsigned found=0;
    for(unsigned obj=0;obj<OBJ_COUNT;obj++){if(obj==OBJ_FM6BANK)continue;int present=0,valid=0;for(unsigned copy=0;copy<2;copy++){unsigned off=st_sector(obj,copy);st_hdr_t h;memcpy(&h,data+off,sizeof h);int blank=1;for(unsigned j=0;j<sizeof h;j++)if(data[off+j]!=255)blank=0;if(!blank)present=1;if(h.magic!=ST_MAGIC)continue;if(h.type!=obj||h.slot!=copy||h.len>ST_PAYLOAD_MAX||h.hcrc!=st_crc32(&h,sizeof h-4)||h.crc!=st_crc32(data+off+ST_PAYLOAD_OFF,h.len))continue;
        if(h.len&&(obj==OBJ_AUTOSAVE||(obj>=OBJ_PROJECT0&&obj<OBJ_PROJECT0+4))&&!proj_import(&proj_scratch,data+off+ST_PAYLOAD_OFF,h.len))continue;valid=1;}
        if(present&&!valid)return 0;found+=valid;}
    for(unsigned k=0;k<SMP_USER_SLOTS;k++){const smp_user_hdr_t *h=(const void *)(data+SMP_USER_BASE+k*SMP_USER_SIZE);if(h->magic!=SMP_USER_MAGIC)continue;if(h->version!=1||!h->nz||h->nz>16||h->data_len>SMP_USER_SIZE-SMP_USER_DATA||h->crc!=st_crc32((const uint8_t *)h+SMP_USER_DATA,h->data_len))return 0;for(unsigned z=0;z<h->nz;z++)if(!smp_zone_ok(&h->zone[z],h->data_len))return 0;found++;}
    return found!=0;
}
static const struct fm1_studio_api studio_api={FM1_STUDIO_ABI,sizeof(struct fm1_studio_api),studio_snapshot,studio_import,studio_export,studio_sample,studio_validate};
__attribute__((visibility("default"))) const struct fm1_studio_api *fm1_get_studio_api(void){return &studio_api;}
