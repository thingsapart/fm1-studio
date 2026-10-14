/* SPDX-License-Identifier: GPL-3.0-only */
#include "session.h"
#include <cassert>
#include <cstdio>
#include <memory>
#include <string>
#include <dlfcn.h>
#include <sys/stat.h>
static void warm(const fm1_firmware *f){float pcm[882];for(int i=0;i<5;i++)f->render(pcm,441);}
static void midi(const fm1_firmware *f,int status,int d1=0,int d2=0){f->event(FM1_MIDI,0,(status>=248?15:status>>4)|(status<<8)|(d1<<16)|(d2<<24));warm(f);}
static const sloop_group *group(const sloop_editor &s,const char *name){for(int i=0;i<s.group_count;i++)if(std::string(s.groups[i].title)==name)return &s.groups[i];return nullptr;}
int main(int argc,char **argv){
    assert(argc==2);std::string dir=argv[1];
    for(auto id:{"ghoulbox","zp12"}){
        std::string save=dir+"/midi-audit";mkdir(save.c_str(),0700);std::remove((save+"/"+id+".flash").c_str());Session session;session.select(dir+"/"+id+".so",id,save);
        auto f=session.engine();auto ui=session.studio();auto s=std::make_unique<sloop_editor>();warm(f);ui->snapshot(s.get());
        if(std::string(id)=="ghoulbox"){
            void *h=dlopen((dir+"/ghoulbox.so").c_str(),RTLD_NOW|RTLD_LOCAL);assert(h);
            auto owned=(int(*)(int,int))dlsym(h,"fm1_test_midi_owned");auto held=(int(*)(int))dlsym(h,"fm1_test_held");auto route=(int(*)())dlsym(h,"fm1_test_route_parameter");assert(owned&&held&&route&&route()==14);
            f->event(64+SLOOP_GLOBAL,route(),0);warm(f);midi(f,0x91,60,100);assert(owned(1,1)==1);midi(f,0x81,60);assert(owned(1,1)==0);
            midi(f,0x99,60,100);for(int t=0;t<4;t++)assert(owned(9,t)==0);
            f->event(64+SLOOP_SELECT,2,0);f->event(64+SLOOP_GLOBAL,route(),1);warm(f);midi(f,0x9f,62,100);assert(owned(15,2)==1);
            f->event(64+SLOOP_SELECT,0,0);midi(f,0x8f,62);assert(owned(15,2)==0);
            midi(f,0x9f,64,100);midi(f,0xbf,64,127);midi(f,0x8f,64);assert(owned(15,0)==1);midi(f,0xbf,64,0);assert(owned(15,0)==0);
            midi(f,0xbf,7,51);ui->snapshot(s.get());assert(s->tracks[0].level==51);
            f->event(64+SLOOP_NOTE,2*128+67,100);warm(f);assert(held(2)>0);f->event(64+SLOOP_NOTE,2*128+67,0);warm(f);assert(held(2)==0);
            midi(f,0x9f,65,100);f->event(FM1_PANIC,0,0);warm(f);assert(owned(15,0)==0);
            ui->snapshot(s.get());auto pattern=group(*s,"PATTERN");assert(pattern&&pattern->count==4&&pattern->param[0].id%256==s->length_parameter%256);
            f->event(64+SLOOP_GLOBAL,2,1);warm(f);midi(f,0xfa);assert(f->playing());midi(f,0xfc);assert(!f->playing());
            dlclose(h);puts("PASS Ghoulbox original/selected MIDI routes, track-owned note-offs, sustain, CC7, JP notes, panic, external transport and Bank C metadata");
        }else{
            for(int pad=0;pad<32;pad++){midi(f,0x99,36+pad,80);ui->snapshot(s.get());assert(std::string(s->extra).find("\"pad\":"+std::to_string(pad)+",")!=std::string::npos);}
            midi(f,0xc0,7);ui->snapshot(s.get());assert(std::string(s->extra).find("\"liveSection\":7")!=std::string::npos);
            f->event(FM1_PANIC,0,0);warm(f);f->event(64+SLOOP_RECORD,0,1);warm(f);ui->snapshot(s.get());assert(s->recording==255);
            f->event(64+SLOOP_RECORD,0,0);warm(f);ui->snapshot(s.get());assert(s->recording==0);
            midi(f,0xfa);assert(f->playing());midi(f,0xfc);assert(!f->playing());
            f->event(64+SLOOP_ADVANCED,10000,31);warm(f);ui->snapshot(s.get());auto sound=group(*s,"SOUND");auto pattern=group(*s,"PATTERN");assert(sound&&sound->count==4&&pattern&&pattern->count==4);
            int parameter=sound->param[0].id;f->event(64+SLOOP_PARAM,parameter,-12);warm(f);ui->snapshot(s.get());assert(group(*s,"SOUND")->param[0].value==-12);
            // Delayed absolute edits retain the original pad target after selection changes.
            f->event(64+SLOOP_ADVANCED,10000,0);f->event(64+SLOOP_PARAM,parameter,-9);warm(f);ui->snapshot(s.get());assert(group(*s,"SOUND")->param[0].value!=-9);
            f->event(64+SLOOP_ADVANCED,10000,31);warm(f);ui->snapshot(s.get());assert(group(*s,"SOUND")->param[0].value==-9);
            f->event(64+SLOOP_ADVANCED,10001,3);warm(f);ui->snapshot(s.get());assert(s->steps[3][0]&(1<<15));
            f->event(64+SLOOP_ADVANCED,10001,3);warm(f);ui->snapshot(s.get());assert(!(s->steps[3][0]&(1<<15)));
            f->event(64+SLOOP_MUTE,7,1);f->event(64+SLOOP_PERFORM,47,1);ui->snapshot(s.get());assert(s->tracks[7].mute==1&&std::string(s->extra).find("\"solo\":128")!=std::string::npos);
            for(int g=0;g<s->group_count;g++)for(int k=0;k<s->groups[g].count;k++){auto &p=s->groups[g].param[k];assert(p.min<=p.value&&p.value<=p.max);}
            // Restored own samples may have empty slots before them: presets use
            // contiguous ordinals while parameter choices retain native wave IDs.
            void *h=dlopen((dir+"/zp12.so").c_str(),RTLD_NOW|RTLD_LOCAL);assert(h);
            auto userWave=(int(*)())dlsym(h,"fm1_test_user_wave");assert(userWave);
            int count=s->preset_count,wave=userWave();ui->snapshot(s.get());assert(s->preset_count==count+1);
            f->event(64+SLOOP_PRESET,0,count);warm(f);ui->snapshot(s.get());assert(s->preset==count&&std::string(s->presets[count])=="USERTEST");
            auto waves=group(*s,"WAVE");assert(waves&&waves->param[0].value==wave&&waves->param[0].choice_values[count]==wave);
            dlclose(h);
            puts("PASS zp12 all 32 MIDI pads, loop PC, armed REC cancellation, realtime transport, targeted absolute parameters, step toggle, mute/solo and all JP knob banks");
        }
    }
}
