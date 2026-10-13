/* SPDX-License-Identifier: GPL-3.0-only */
#pragma once
#include "storage_format.h"
#include <cstdio>
#include <unistd.h>
namespace host {
// The engine only supplies a snapshot; all filesystem work belongs to the storage task.
class ImageFiles {
public:
    ImageFiles(const char* a,const char* b,const char* temporary):paths_{a,b},temp_(temporary){}
    bool load(uint8_t* copy){
        image::Header headers[2]{};bool found[2]{};
        for(unsigned i=0;i<2;++i){FILE* f=fopen(paths_[i],"rb");if(!f)continue;uint8_t raw[20];
            if(fread(raw,1,20,f)==20){headers[i]=image::decode(raw);found[i]=image::valid(headers[i]);}fclose(f);}
        unsigned first=found[1] && (!found[0] || image::newer(headers[1].generation,headers[0].generation))?1:0;
        for(unsigned n=0;n<2;++n){unsigned i=first^n;if(!found[i])continue;
            FILE* f=fopen(paths_[i],"rb");if(!f)continue;bool ok=fseek(f,20,SEEK_SET)==0 &&
                fread(copy,1,image::size,f)==image::size && fgetc(f)==EOF;fclose(f);
            if(ok && image::crc(copy,image::size)==headers[i].crc){active_=i;generation_=headers[i].generation;return true;}}
        return false;
    }
    bool commit(const uint8_t* copy){
        FILE* f=fopen(temp_,"wb");bool ok=f!=nullptr;
        image::Header header;header.generation=generation_+1;header.crc=image::crc(copy,image::size);
        uint8_t raw[20];image::encode(header,raw);
        if(f){ok=fwrite(raw,1,20,f)==20 && fwrite(copy,1,image::size,f)==image::size;
            if(ok)ok=fflush(f)==0 && fsync(fileno(f))==0;if(fclose(f))ok=false;}
        // FatFs cannot rename over an existing file. Only retire the inactive copy;
        // the last committed image survives a failed write or interrupted rename.
        unsigned next=active_^1;if(ok){unlink(paths_[next]);ok=rename(temp_,paths_[next])==0;}
        if(ok){active_=next;generation_=header.generation;}
        if(!ok)unlink(temp_);return ok;
    }
private:
    const char* paths_[2];const char* temp_;unsigned active_=1;uint32_t generation_=0;
};
}
