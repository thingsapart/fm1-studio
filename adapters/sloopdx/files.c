/* SPDX-License-Identifier: GPL-3.0-only */
/* Native project and DX7 bank files; sloopDX has no user-sample engine. */
int sloop_import_sample(unsigned slot,const uint8_t *data,unsigned size){(void)slot;(void)data;(void)size;return 0;}
static void dx_syx(uint8_t *out,const uint8_t *data,unsigned n,int bulk){
    out[0]=0xf0;out[1]=0x43;out[2]=0;out[3]=bulk?9:0;out[4]=bulk?32:1;out[5]=bulk?0:27;
    unsigned sum=0;for(unsigned i=0;i<n;i++){out[6+i]=data[i]&127;sum+=out[6+i];}out[6+n]=(128-(sum&127))&127;out[7+n]=0xf7;
}
int sloop_object_import(int kind,int id,const uint8_t *data,unsigned size){
    if(id<0||song.playing||transport_req||ft_on)return 0;
    if(kind==4&&id<4){project_t project;if(!proj_import(&project,data,size)||st_save(OBJ_PROJECT0+id,&project,sizeof project))return 0;proj_slot[id]=project;return 1;}
    if(kind==0&&id<UP_SLOTS&&size==sizeof(up_rec_t)){up_rec_t r;memcpy(&r,data,size);if(!up_valid(&r))return 0;return up_put(id,&r)==0;}
    if(kind==10&&id<UP_SLOTS&&up_used(id)&&size>0&&size<=12){up_rec_t r=*up_rec(id);memset(r.name,0,sizeof r.name);for(unsigned i=0;i<size;i++){if(data[i]<32||data[i]>126)return 0;r.name[i]=data[i];}return up_put(id,&r)==0;}
    if(kind==1&&id<NPART&&(size==128||size==155)){
        for(unsigned i=0;i<size;i++)if(data[i]>127)return 0;
        unsigned sel=song.sel;song.sel=id;int slot=ve_edit_slot();song.sel=sel;if(slot<0)return 0;
        uint8_t u[156]={0},b[128];if(size==128)dx_unpack(data,u);else memcpy(u,data,size);dx_sanitize(u);dx_pack(u,b);dx_user_put(slot,b);
        trk[id].p[P_E0]=DX_NSYNTH+slot;return dx_bank_store()==0;
    }
    if(kind==5&&id<DX_NBANKS&&size==4096){
        uint8_t bank[4096],u[156];for(unsigned i=0;i<size;i++)if(data[i]>127)return 0;
        for(unsigned i=0;i<DX_NUSER;i++){dx_unpack(data+i*128,u);dx_sanitize(u);dx_pack(u,bank+i*128);}
        unsigned obj=OBJ_DXBANK0+id*2;if(st_save(obj,bank,2048)||st_save(obj+1,bank+2048,2048))return 0;
        if(id==dx_bank_cur)dx_bank_read(id);return 1;
    }
    if(kind==6&&id<NTRK&&size>=22&&size<=1292&&(size-12)%10==0){
        uint32_t magic;memcpy(&magic,data,4);if(magic!=0x3154504du||data[4]<1||data[4]>(size-12)/10||data[5]!=is_drum(&trk[id])||data[6]>=(sizeof N_SDIV/sizeof N_SDIV[0]))return 0;
        step_t steps[NSTEP];memset(steps,0,sizeof steps);for(unsigned i=0;i<NSTEP;i++)steps[i].time=ST_REST;
        for(unsigned i=0;i<(size-12)/10;i++){const uint8_t *s=data+12+i*10;
            if(is_drum(&trk[id]))memcpy(&steps[i],s,10);
            else{if(s[4]>4||s[5]>ST_REST||s[7]>127)return 0;for(unsigned j=0;j<s[4];j++)if(s[j]>127)return 0;memcpy(&steps[i],s,10);}}
        if(!dx_editable())return 0;dx_mark(&trk[id]);memcpy(trk[id].step,steps,sizeof steps);slen_set(&trk[id],data[4]);trk[id].p[P_SDIV]=data[6];song.g[G_BPM]=clamp(data[8]|data[9]<<8,40,240);ui.force=1;return 1;
    }
    return 0;
}
unsigned sloop_object_export(int kind,int id,uint8_t *out,unsigned capacity){
    if(id<0)return 0;
    if(kind==4&&id<4&&project_used(id)&&capacity>=sizeof(project_t)){memcpy(out,&proj_slot[id],sizeof(project_t));return sizeof(project_t);}
    if(kind==0&&id<UP_SLOTS&&up_used(id)&&capacity>=sizeof(up_rec_t)){memcpy(out,up_rec(id),sizeof(up_rec_t));return sizeof(up_rec_t);}
    if(kind==1&&id<NPART&&capacity>=163){uint8_t u[156];dx_voice_for(&trk[id],u);dx_syx(out,u,155,0);return 163;}
    if(kind==5&&id<DX_NBANKS&&capacity>=4104){uint8_t bank[4096];
        if(id==dx_bank_cur){if(!dx_user_ok)return 0;memcpy(bank,dx_user,sizeof bank);}
        else{unsigned obj=OBJ_DXBANK0+id*2;if(st_load(obj,bank,2048)!=2048||st_load(obj+1,bank+2048,2048)!=2048)return 0;}
        dx_syx(out,bank,4096,1);return 4104;
    }return 0;
}
int sloop_validate_image(const uint8_t *data,unsigned size){
    if(size!=SLOOP_STORAGE_SIZE)return 0;unsigned found=0;
    for(unsigned obj=0;obj<OBJ_COUNT;obj++){int present=0,valid=0;
        for(unsigned copy=0;copy<2;copy++){unsigned off=st_sector(obj,copy);st_hdr_t h;memcpy(&h,data+off,sizeof h);int blank=1;for(unsigned j=0;j<sizeof h;j++)if(data[off+j]!=255)blank=0;if(!blank)present=1;
            if(h.magic!=ST_MAGIC||h.type!=obj||h.slot!=copy||h.len>ST_PAYLOAD_MAX||h.hcrc!=st_crc32(&h,sizeof h-4)||h.crc!=st_crc32(data+off+ST_PAYLOAD_OFF,h.len))continue;
            if(h.len&&(obj==OBJ_AUTOSAVE||(obj>=OBJ_PROJECT0&&obj<OBJ_PROJECT0+4))){project_t p;if(!proj_import(&p,data+off+ST_PAYLOAD_OFF,h.len))continue;}
            if(h.len&&obj>=OBJ_DXBANK0&&obj<OBJ_DXMETA&&h.len!=2048)continue;
            valid=1;
        }if(present&&!valid)return 0;found+=valid;
    }return found!=0;
}
