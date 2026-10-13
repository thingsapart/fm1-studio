/* SPDX-License-Identifier: GPL-3.0-only */
static int studio_sample(unsigned slot,const uint8_t *data,unsigned size){
    if(song.playing||transport_req||slot>=3||size<512||size>SMP_USER_SIZE)return 0;
    const smp_user_hdr_t *h=(const void *)data;
    if(h->magic!=SMP_USER_MAGIC||h->version!=1||!h->nz||h->nz>16||h->data_len>SMP_USER_SIZE-SMP_USER_DATA||size!=SMP_USER_DATA+h->data_len||h->crc!=st_crc32(data+SMP_USER_DATA,h->data_len))return 0;
    for(unsigned i=0;i<h->nz;i++)if(!smp_zone_ok(&h->zone[i],h->data_len))return 0;
    memset((void *)SMP_USER_XIP(slot),255,SMP_USER_SIZE);memcpy((void *)SMP_USER_XIP(slot),data,size);smp_user_scan(slot);sim_dirty=1;return 1;
}
static int studio_validate(const uint8_t *data,unsigned size){if(size!=sizeof nor)return 0;unsigned found=0;
    for(unsigned obj=0;obj<OBJ_COUNT;obj++){int present=0,valid=0;for(unsigned copy=0;copy<2;copy++){unsigned off=st_sector(obj,copy);st_hdr_t h;memcpy(&h,data+off,sizeof h);int blank=1;for(unsigned j=0;j<sizeof h;j++)if(data[off+j]!=255)blank=0;if(!blank)present=1;if(h.magic!=ST_MAGIC)continue;
        if(h.type==obj&&h.slot==copy&&h.len<=ST_PAYLOAD_MAX&&h.hcrc==st_crc32(&h,sizeof h-4)&&h.crc==st_crc32(data+off+ST_PAYLOAD_OFF,h.len))valid=1;}
        if(present&&!valid)return 0;found+=valid;}
    return found!=0;}
static unsigned studio_project_export(int slot,uint8_t *out,unsigned capacity){
    if(slot<0||slot>=FMP_NPROJ)return 0;uint32_t crc,n=fmp_stored(slot,&crc);
    return n&&n<=capacity&&!fmp_read(slot,0,out,n)?n:0;
}
static int studio_project_import(int slot,const uint8_t *data,unsigned size){
    if(slot<0||slot>=FMP_NPROJ||!size||size>FMP_MAX)return 0;
    uint8_t *previous=malloc(sizeof nor);if(!previous)return 0;memcpy(previous,nor,sizeof nor);
    unsigned chunks=(size+FMP_DATA-1)/FMP_DATA,gen=fmp_next_gen(slot);int ok=1;
    for(unsigned k=chunks;k-->0;){unsigned lo=k*FMP_DATA,n=size-lo;if(n>FMP_DATA)n=FMP_DATA;memcpy(st_buf+FMP_CHDR,data+lo,n);if(fmp_put_chunk(slot,k,chunks,gen,size,n)){ok=0;break;}}
    if(ok&&fmp_parse(slot,0,0))ok=0;
    if(!ok)memcpy(nor,previous,sizeof nor);else sim_dirty=1;
    free(previous);return ok;
}
