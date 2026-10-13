/* SPDX-License-Identifier: GPL-3.0-only */
#include "session.h"
#include "studio_json.h"
#include <cassert>
#include <cstdio>
#include <memory>
#include <string>
#include <vector>
#include <sys/stat.h>

static void warm(const fm1_firmware *f){float pcm[512];for(unsigned i=0;i<40;i++){f->render(pcm,256);f->tick(10+i*6);}}
static void tap(const fm1_firmware *f,int button){f->event(FM1_BUTTON,button,1);f->event(FM1_BUTTON,button,0);}
int main(int argc,char **argv){
    assert(argc==2);std::string dir=argv[1],save=dir+"/sloopdx-specific";mkdir(save.c_str(),0700);
    std::remove((save+"/sloopdx.flash").c_str());Session session;
    session.select(dir+"/sloopdx.so","sloopdx",save);
    auto f=session.engine();auto api=session.studio();warm(f);
    auto s=std::make_unique<sloop_editor>();api->snapshot(s.get());
    assert(s->step_count==128&&std::string(s->engines[0])=="DX7");
    unsigned effectPages=0;
    for(int i=0;i<s->group_count;i++){
        auto &g=s->groups[i];std::string name=g.title;
        if(name=="DIST"||name=="CHORUS"||name=="DELAY"||name=="REVERB"){
            assert(g.count==4);effectPages++;
            if(name!="DIST"){assert(g.param[0].id<FM1_GLOBAL_PARAMETER_BASE);assert(g.param[1].id>=FM1_GLOBAL_PARAMETER_BASE);}
        }
    }
    assert(effectPages==4);
    assert(!api->import_sample(0,(const uint8_t *)"FSMP",4));
    f->event(64+SLOOP_SELECT,0,0);api->snapshot(s.get());
    f->event(64+SLOOP_PARAM,s->length_parameter,128);
    f->event(64+SLOOP_STEP,127*128+60,1);
    f->event(64+SLOOP_DETAIL,127*1024+2,3);api->snapshot(s.get());
    assert(s->length==128&&s->steps[127][0]==60);
    assert(s->steps[63][0]==-1&&s->tracks[1].drum==0);
    f->event(64+SLOOP_SELECT,1,0);api->snapshot(s.get());
    f->event(64+SLOOP_PARAM,s->length_parameter,128);api->snapshot(s.get());assert(s->length==96);
    f->event(64+SLOOP_SELECT,3,0);f->event(64+SLOOP_STEP,3*16384+15*128+4,1);
    api->snapshot(s.get());assert(s->steps[15][0]==(1<<4));
    f->event(64+SLOOP_DETAIL,3*131072+15*1024+4*32+1,3);
    f->event(64+SLOOP_SELECT,0,0);f->event(64+SLOOP_ACTION,17,0);
    std::vector<uint8_t> project(81920);auto n=api->export_object(4,0,project.data(),project.size());
    assert(n>0&&api->import_object(4,1,project.data(),n));
    f->event(64+SLOOP_ACTION,0,0);api->snapshot(s.get());assert(s->steps[127][0]==-1);
    f->event(64+SLOOP_ACTION,16,1);warm(f);api->snapshot(s.get());assert(s->length==128&&s->steps[127][0]==60);
    f->event(64+SLOOP_DETAIL,127*1024+7,0);api->snapshot(s.get());assert(s->steps[127][0]==-1);
    f->event(64+SLOOP_ACTION,3,0);api->snapshot(s.get());assert(s->steps[127][0]==60);

    std::vector<uint8_t> before(163),after(163),bank(4104);
    assert(api->export_object(1,0,before.data(),before.size())==163);
    f->event(64+SLOOP_EDITOR_HOME,0,0);tap(f,4); // Native EDIT: the DX7 voice list.
    f->event(FM1_ENCODER,0,2); // SELECT -> Algorithm.
    f->event(FM1_ENCODER,1,1); // First ALGO turn draws the diagram.
    f->event(FM1_ENCODER,1,1); // Second turn edits, copying into a user voice.
    assert(api->export_object(1,0,after.data(),after.size())==163);
    assert(before[6+134]!=after[6+134]);tap(f,7); // SAVE stores the actual DX7 bank.
    assert(api->export_object(5,0,bank.data(),bank.size())==4104);
    unsigned sum=0;for(unsigned i=6;i<=4102;i++)sum+=bank[i];assert((sum&127)==0);
    assert(bank[0]==0xf0&&bank[4103]==0xf7);
    for(int slot=0;slot<8;slot++){
        assert(api->import_object(5,slot,bank.data()+6,4096));
        std::vector<uint8_t> exported(4104);
        assert(api->export_object(5,slot,exported.data(),exported.size())==4104);
        assert(exported==bank);
    }
    bank[6]|=128;assert(!api->import_object(5,6,bank.data()+6,4096));bank[6]&=127;
    f->event(64+SLOOP_ADVANCED,8192,7);api->snapshot(s.get());
    assert(fm1_studio_json(*s).find("\"dxBank\":7")!=std::string::npos);
    std::vector<uint8_t> copied(4104);assert(api->export_object(5,7,copied.data(),copied.size())==4104);assert(copied==bank);
    assert(f->checkpoint());std::vector<uint8_t> image(f->storage(),f->storage()+f->storage_size);
    assert(api->validate_image(image.data(),image.size()));
    image[0xfc000]^=1;image[0xfd000]^=1;assert(!api->validate_image(image.data(),image.size()));
    session.select(dir+"/sloop.so","sloop",save);
    session.select(dir+"/sloopdx.so","sloopdx",save);f=session.engine();api=session.studio();warm(f);api->snapshot(s.get());
    assert(s->length==128&&s->steps[127][0]==60);
    assert(fm1_studio_json(*s).find("\"dxBank\":7")!=std::string::npos);
    std::puts("PASS sloopDX 128 steps, shared step budget, track isolation, undo, native DX7 editing, eight banks, SysEx, sample rejection and persistence");
}
