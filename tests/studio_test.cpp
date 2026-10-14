/* SPDX-License-Identifier: GPL-3.0-only */
#include "session.h"
#include "studio_json.h"
#include <cassert>
#include <cmath>
#include <cstdio>
#include <fstream>
#include <memory>
#include <vector>
#include <sys/stat.h>
static void warm(const fm1_firmware *f){float pcm[512];for(unsigned i=0;i<100;i++){f->render(pcm,256);f->tick(i*6+1);}}
int main(int argc,char **argv){
    assert(argc==2);std::string dir=argv[1];
    const char *ids[]={"sloop","move","felucca","sloopdx"};int counts[]={4,8,4,4};
    for(unsigned k=0;k<4;k++){
        std::string id=ids[k],save=dir+"/studio-"+id;mkdir(save.c_str(),0700);
        Session session;session.select(dir+"/"+id+".so",id,save);auto f=session.engine();auto editor=session.studio();assert(editor&&editor->snapshot&&editor->import_object&&editor->export_object&&editor->validate_image);
        warm(f);auto state=std::make_unique<sloop_editor>();editor->snapshot(state.get());assert(state->version==1&&state->group_count>0&&state->preset_count>0);
        int track=counts[k]-1;f->event(64+SLOOP_SELECT,track,0);f->event(64+SLOOP_MIX,track,87);f->event(64+SLOOP_MUTE,track,1);f->event(64+SLOOP_GLOBAL,0,137);editor->snapshot(state.get());assert(state->selected==track&&state->bpm==137&&state->tracks[track].level==87&&state->tracks[track].mute==1);
        f->event(64+SLOOP_MUTE,track,0);f->event(64+SLOOP_PARAM,state->length_parameter,32);editor->snapshot(state.get());assert(state->length==32);
        int synth=0;for(int i=0;i<counts[k];i++)if(!state->tracks[i].drum){synth=i;break;}
        if(id!="felucca"){
            // The snapshot marks armed recording active, so a second REC requests off.
            f->event(64+SLOOP_RECORD,synth,1);warm(f);editor->snapshot(state.get());
            std::ofstream(dir+"/jp1-armed-"+id+".json")<<fm1_studio_json(*state);
            assert((state->recording&(1<<synth))!=0&&std::string(state->extra).find("\"recordState\":1")!=std::string::npos);
            int toggle=(state->recording&(1<<synth))==0?1:0;
            f->event(64+SLOOP_RECORD,synth,toggle);warm(f);editor->snapshot(state.get());
            assert(state->recording==0&&std::string(state->extra).find("\"recordState\":0")!=std::string::npos);
        }
        f->event(64+SLOOP_SELECT,synth,0);f->event(64+SLOOP_STEP,synth*8192+60,1);editor->snapshot(state.get());assert(state->steps[0][0]==60);
        f->event(64+SLOOP_DETAIL,synth*65536+10,72); // Felucca probability; ignored elsewhere.
        for(int g=0;g<state->group_count;g++)for(int i=0;i<state->groups[g].count;i++){
            auto &p=state->groups[g].param[i];assert(p.min<=p.value&&p.value<=p.max);assert(p.choice_count<=128);assert(p.id>=FM1_GLOBAL_PARAMETER_BASE||p.id/256<counts[k]);
        }
        // A chord gesture inserts four pitches and ties; one undo restores the preceding pattern.
        unsigned stepStride=k==3?16384:8192,detailStride=k==3?131072:65536;
        f->event(64+SLOOP_ACTION,synth*256+52,0);
        for(int note : {57,60,64,67})f->event(64+SLOOP_STEP,synth*stepStride+2*128+note,1);
        f->event(64+SLOOP_DETAIL,synth*detailStride+2*1024+5,2);
        f->event(64+SLOOP_ACTION,synth*256+53,0);editor->snapshot(state.get());
        const int chord[]={57,60,64,67};for(unsigned i=0;i<4;i++)assert(state->steps[2][i]==chord[i]);
        std::ofstream(dir+"/chord-"+id+".json")<<fm1_studio_json(*state);
        f->event(64+SLOOP_ACTION,synth*256+3,0);editor->snapshot(state.get());
        assert(state->steps[2][0]==-1&&state->steps[0][0]==60);
        std::ofstream(dir+"/chord-undo-"+id+".json")<<fm1_studio_json(*state);
        if(k<2){
            // Editing a kick's decay must not change another sound or kit.
            std::ofstream(dir+"/drum-before-"+id+".json")<<fm1_studio_json(*state);
            f->event(64+SLOOP_ADVANCED,4096+512+7,17);
            editor->snapshot(state.get());std::ofstream(dir+"/drum-after-"+id+".json")<<fm1_studio_json(*state);
            f->event(64+SLOOP_ADVANCED,9101,0);float pcm[512];f->render(pcm,256);
            for(float sample:pcm)assert(std::isfinite(sample));
        }
        // Native project formats round-trip through the shared file picker interface.
        f->event(64+SLOOP_ACTION,synth*256+17,0);
        std::vector<uint8_t> object(81920);unsigned n=editor->export_object(4,0,object.data(),object.size());assert(n>0);assert(editor->import_object(4,1,object.data(),n));
        assert(!editor->import_sample(0,object.data(),1));
        assert(f->checkpoint());std::vector<uint8_t> image(f->storage(),f->storage()+f->storage_size);assert(editor->validate_image(image.data(),image.size()));
        assert(session.restore(image.data(),image.size()));warm(f);editor->snapshot(state.get());assert(state->bpm==137);
        std::ofstream(dir+"/studio-"+id+".json")<<fm1_studio_json(*state);
        f->event(64+SLOOP_SELECT,track,0);f->event(64+SLOOP_MIX,track,83);editor->snapshot(state.get());assert(state->tracks[track].level==83); // catches global/track id collisions on MOVE.
        std::printf("PASS %s semantic tracks, mixer, parameters, chord duration/undo, project import/export and backup restore\n",ids[k]);
    }
}
