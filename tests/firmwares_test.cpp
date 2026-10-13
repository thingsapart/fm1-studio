/* SPDX-License-Identifier: GPL-3.0-only */
#include "session.h"
#include <dlfcn.h>
#include <cassert>
#include <cmath>
#include <cstdio>
#include <vector>
#include <string>
#include <fstream>

static void warm(const fm1_firmware *f) {
    float samples[512];for(unsigned i=0;i<100;i++){f->render(samples,256);f->tick((i+1)*6);}
}
static uint64_t hash(const fm1_firmware *f,bool irregular) {
    f->event(FM1_MIDI,0,9|(0x91<<8)|(60<<16)|(100<<24));
    float out[1024];unsigned left=4096,index=0;const unsigned sizes[]={1,17,31,33,192,240,511};
    uint64_t h=1469598103934665603ull;
    while(left){unsigned n=irregular?sizes[index++%7]:256;if(n>left)n=left;f->render(out,n);
        for(unsigned i=0;i<2*n;i++){assert(std::isfinite(out[i]));int16_t v=out[i]*32767;h^=uint16_t(v);h*=1099511628211ull;}left-=n;}
    return h;
}
static void image(const fm1_firmware *f,const std::string &path) {
    std::vector<uint32_t> pixels(240*240);f->pixels(pixels.data());
    FILE *out=fopen(path.c_str(),"wb");assert(out);fprintf(out,"P6\n240 240\n255\n");
    unsigned visible=0;for(auto p:pixels){assert(p>>24==255);visible+=p!=0xff000000;
        unsigned char rgb[]={static_cast<unsigned char>(p>>16),static_cast<unsigned char>(p>>8),static_cast<unsigned char>(p)};fwrite(rgb,1,3,out);}
    fclose(out);assert(visible>100);
}
int main(int argc,char **argv) {
    assert(argc==2);std::string dir=argv[1];Session session;
    const char *ids[]={"sloop","move","felucca","sloopdx"};int tracks[]={4,8,4,4};
    for(unsigned k=0;k<4;k++) {
        std::string id=ids[k],lib=dir+"/"+id+".so";
        session.select(lib,id,dir);auto f=session.engine();warm(f);
        void *handle=dlopen(lib.c_str(),RTLD_NOW|RTLD_LOCAL);assert(handle);
        auto get=(int(*)(void))dlsym(handle,"fm1_test_tracks");assert(get&&get()==tracks[k]);
        auto set=(void(*)(int))dlsym(handle,"fm1_test_set_bpm");assert(set);set(121+k*7);
        if(id!="sloopdx") { auto sample=(int(*)(void))dlsym(handle,"fm1_test_user_sample");assert(sample&&sample()); }
        if(id=="move") {
            auto view=(int(*)(void))dlsym(handle,"fm1_test_screen");assert(view);
            for(unsigned n=0;n<10;n++){f->event(FM1_SCREEN,n,0);f->tick(650+n*16);assert(view()==int(n));image(f,dir+"/move-screen-"+std::to_string(n)+".ppm");}
            f->event(FM1_SCREEN,0,0);
        }
        dlclose(handle);
        f->event(FM1_MIDI,0,9|(0x91<<8)|(60<<16)|(100<<24));
        double energy=0;float sound[514];for(unsigned i=0;i<40;i++){f->render(sound,257);for(float x:sound){assert(std::isfinite(x)&&std::abs(x)<=1);energy+=std::abs(x);}}
        assert(energy>1);f->event(FM1_PANIC,0,0);warm(f);image(f,dir+"/"+id+".ppm");
        // More than one firmware-owned CTL/audio half may be pending.
        f->event(FM1_BUTTON,10,1);warm(f);f->event(FM1_BUTTON,10,0);warm(f);assert(f->playing());
        session.suspend();assert(!f->playing());
        printf("PASS %s: %d real tracks, audio, pixels, transport%s\n",ids[k],tracks[k],id=="sloopdx"?"":" and 64-bit user samples");
    }
    for(unsigned k=0;k<4;k++) {
        std::string id=ids[k],lib=dir+"/"+id+".so";
        session.select(lib,id,dir);warm(session.engine());
        void *handle=dlopen(lib.c_str(),RTLD_NOW|RTLD_LOCAL);assert(handle);
        auto get=(int(*)(void))dlsym(handle,"fm1_test_bpm");assert(get);
        printf("RESTORE %s bpm=%d expected=%u\n",ids[k],get(),121+k*7);
        assert(get()==int(121+k*7));dlclose(handle);
    }
    puts("PASS switching all four real firmwares restores independent native autosaves");
    for(auto id:ids){
        std::string lib=dir+"/"+id+".so";
        // Fresh save namespaces and separate Session objects ensure cold state.
        uint64_t hashes[2];
        for(unsigned mode=0;mode<2;mode++){
            std::remove((dir+"/"+id+".flash").c_str());
            session.select(dir+"/fixture_a.so","fixture_a",dir);
            {Session cold;cold.select(lib,id,dir);warm(cold.engine());hashes[mode]=hash(cold.engine(),mode);}
        }
        assert(hashes[0]==hashes[1]);printf("PASS %s chunk-independent PCM\n",id);
    }
}
