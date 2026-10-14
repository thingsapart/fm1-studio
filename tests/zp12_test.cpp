/* SPDX-License-Identifier: GPL-3.0-only */
#include "session.h"
#include "studio_json.h"
#include <cassert>
#include <cmath>
#include <cstdio>
#include <fstream>
#include <memory>
#include <vector>
static double render(const fm1_firmware *f,unsigned frames){
    float pcm[514];double energy=0;
    while(frames){unsigned n=frames>257?257:frames;f->render(pcm,n);frames-=n;
        for(unsigned i=0;i<n*2;i++){assert(std::isfinite(pcm[i])&&std::abs(pcm[i])<=1);energy+=std::abs(pcm[i]);}}
    return energy;
}
int main(int argc,char **argv){
    assert(argc==2);std::string dir=argv[1];std::remove((dir+"/zp12.flash").c_str());Session session;
    session.select(dir+"/zp12.so","zp12",dir);auto f=session.engine();render(f,4410);
    auto state=std::make_unique<sloop_editor>();auto editor=session.studio();assert(editor);
    editor->snapshot(state.get());assert(state->version==1&&state->preset_count>=24);
    f->event(FM1_KEY,0,1);f->event(FM1_KEY,0,0);assert(render(f,11025)>1);
    f->event(FM1_MIDI,0,0xC|(0xC0<<8)|(7<<16));render(f,441);
    editor->snapshot(state.get());assert(std::string(state->extra).find("\"liveSection\":7")!=std::string::npos);
    assert(std::string(state->extra).find("\"A1\"")!=std::string::npos);
    // The seven printed encoder roles must reach the correct native controls.
    f->event(FM1_ENCODER,0,4);render(f,441);editor->snapshot(state.get());assert(state->bpm==92);
    f->event(FM1_ENCODER,1,1);render(f,441);editor->snapshot(state.get());
    assert(std::string(state->extra).find("\"92.0 BPM\",\"A2\"")!=std::string::npos);
    int preset=state->preset;f->event(FM1_ENCODER,2,1);render(f,441);editor->snapshot(state.get());
    assert(state->preset==(preset+1)%state->preset_count);
    f->event(FM1_ENCODER,1,-1);render(f,441);
    for(int knob=0;knob<4;knob++){
        int before[8];editor->snapshot(state.get());for(int channel=0;channel<8;channel++)before[channel]=state->tracks[channel].level;
        f->event(FM1_ENCODER,3+knob,-1);render(f,441);editor->snapshot(state.get());
        for(int channel=0;channel<8;channel++){
            if(channel==knob)assert(state->tracks[channel].level<before[channel]);
            else assert(state->tracks[channel].level==before[channel]);
        }
    }
    assert(std::string(state->extra).find("\"TEMPO\",\"PAD\",\"SAMPLE\",\"CH1\",\"CH2\",\"CH3\",\"CH4\"")!=std::string::npos);
    f->event(FM1_BUTTON,4,1);f->event(FM1_BUTTON,4,0);render(f,441);editor->snapshot(state.get());
    assert(std::string(state->extra).find("\"WAVE\",\"COPY>\",\"COPY\"")!=std::string::npos);
    f->event(FM1_BUTTON,4,1);f->event(FM1_BUTTON,4,0);render(f,441);editor->snapshot(state.get());
    assert(std::string(state->extra).find("\"TUNE\",\"FINE\",\"DECAY\",\"LEVEL\"")!=std::string::npos);
    f->event(64+SLOOP_EDITOR_HOME,0,0);
    // Common logical PLAY maps to zp12's different physical button order.
    f->event(FM1_BUTTON,10,1);f->event(FM1_BUTTON,10,0);render(f,441);assert(f->playing());
    f->event(FM1_STOP,0,0);render(f,441);assert(!f->playing());
    // Common knob role K1 drives the real zp12 channel fader.
    f->event(64+SLOOP_EDITOR_HOME,0,0);f->event(FM1_ENCODER,3,-10);render(f,441);
    editor->snapshot(state.get());assert(state->tracks[0].level<100);
    f->event(64+SLOOP_SELECT,6,0);f->event(64+SLOOP_MIX,6,87);f->event(64+SLOOP_GLOBAL,0,137);
    editor->snapshot(state.get());assert(state->selected==6&&state->tracks[6].level==87&&state->bpm==137);
    std::ofstream(dir+"/studio-zp12.json")<<fm1_studio_json(*state);
    // Full flash includes both store copies and every possible own-sample room.
    assert(f->storage_size==0x100000);assert(f->checkpoint());
    std::vector<uint8_t> invalid(f->storage_size,0);assert(!editor->validate_image(invalid.data(),invalid.size()));
    std::vector<uint8_t> image(f->storage(),f->storage()+f->storage_size);
    f->event(64+SLOOP_GLOBAL,0,99);assert(session.restore(image.data(),image.size()));
    editor->snapshot(state.get());assert(state->bpm==137&&state->tracks[6].level==87);
    std::vector<uint32_t> pixels(240*240);f->pixels(pixels.data());unsigned visible=0;
    for(auto p:pixels){assert(p>>24==255);visible+=p!=0xff000000;}assert(visible>100);
    session.suspend();assert(!f->playing());
    session.select(dir+"/ghoulbox.so","ghoulbox",dir);
    session.select(dir+"/zp12.so","zp12",dir);session.studio()->snapshot(state.get());
    assert(state->bpm==137&&state->tracks[6].level==87);
    puts("PASS zp12 native keys, audio, button/encoder mapping, transport, mixer, pixels, backup and independent saves");
}
