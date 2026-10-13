/* SPDX-License-Identifier: GPL-3.0-only */
#include "session.h"
#include <cassert>
#include <cmath>
#include <cstdio>
#include <fstream>
#include <stdexcept>
#include <vector>

int main(int argc,char **argv){
    assert(argc==2);std::string dir=argv[1];
    Session s;
    s.select(dir+"/fixture_a.so","fixture_a",dir);
    s.engine()->event(FM1_MASTER,0,42);s.engine()->event(FM1_KEY,0,1);
    s.select(dir+"/fixture_b.so","fixture_b",dir);
    assert(s.engine()->storage()[0]==0);
    float sound[2];s.engine()->render(sound,1);assert(sound[0]==0);
    s.engine()->event(FM1_MASTER,0,97);
    s.select(dir+"/fixture_a.so","fixture_a",dir);
    assert(s.engine()->storage()[0]==42);
    s.engine()->render(sound,1);assert(sound[0]==0);
    auto previous=s.engine();
    try{s.select(dir+"/fixture_b.so","wrong_id",dir);assert(false);}catch(const std::runtime_error&){}
    assert(s.engine()==previous);
    try{s.select(dir+"/missing.so","missing",dir);assert(false);}catch(const std::runtime_error&){}
    assert(s.engine()==previous);
    std::ofstream(dir+"/fixture_b.flash",std::ios::binary|std::ios::trunc).put('x');
    try{s.select(dir+"/fixture_b.so","fixture_b",dir);assert(false);}catch(const std::runtime_error&){}
    assert(s.engine()==previous && s.engine()->storage()[0]==42);
    try{s.select(dir+"/fixture_b.so","../fixture_b",dir);assert(false);}catch(const std::runtime_error&){}
    puts("PASS plugin isolation, checkpoint/restore, released notes, failed-load rollback, corrupt saves and path validation");
    s.select(dir+"/sloop.so","sloop",dir);
    auto f=s.engine();
    f->event(FM1_MIDI,0,9|(0x90<<8)|(60<<16)|(100<<24));
    float samples[514];double energy=0;
    for(unsigned k=0;k<50;k++){
        f->render(samples,257);
        for(float x:samples){assert(std::isfinite(x)&&std::abs(x)<=1);energy+=std::abs(x);}
    }
    assert(energy>0);
    std::vector<uint32_t> frame(240*240);f->tick(100);f->pixels(frame.data());
    unsigned visible=0;for(auto p:frame){assert(p>>24==255);visible+=p!=0xff000000;}assert(visible);
    f->event(FM1_BUTTON,10,1);f->render(samples,256);assert(f->playing());
    f->event(FM1_BUTTON,10,0);
    s.suspend();assert(!f->playing());
    puts("PASS real SLOOP plugin audio, arbitrary frame count, native display, transport and suspend");
}
