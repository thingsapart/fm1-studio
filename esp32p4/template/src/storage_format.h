/* SPDX-License-Identifier: GPL-3.0-only */
#pragma once
#include <cstdint>
#include <cstddef>
namespace host::image {
constexpr uint32_t magic=0x34504c53,version=1,size=1048576;
struct Header{uint32_t tag=magic,format=version,bytes=size,generation=0,crc=0;};
inline void encode(const Header& h,uint8_t* p){const uint32_t fields[]={h.tag,h.format,h.bytes,h.generation,h.crc};
    for(unsigned i=0;i<5;++i)for(unsigned j=0;j<4;++j)p[i*4+j]=fields[i]>>(j*8);}
inline Header decode(const uint8_t* p){uint32_t v[5]{};for(unsigned i=0;i<5;++i)for(unsigned j=0;j<4;++j)v[i]|=uint32_t(p[i*4+j])<<(j*8);
    return {v[0],v[1],v[2],v[3],v[4]};}
inline bool valid(const Header& h){return h.tag==magic && h.format==version && h.bytes==size;}
inline uint32_t crc(const uint8_t* p,size_t n){uint32_t c=0xffffffff;
    while(n--){c^=*p++;for(unsigned b=0;b<8;++b)c=(c>>1)^(0xedb88320u&(0u-(c&1)));}return c^0xffffffff;}
inline bool newer(uint32_t a,uint32_t b){return int32_t(a-b)>0;}
}
