/* SPDX-License-Identifier: GPL-3.0-only */
#pragma once
#include "studio.h"
#include <string>
#include <sstream>
// Serialize a published copy on the UI caller, never on the audio worker.
static std::string quoted(const char *p) {
    std::string out = "\"";
    for (; *p; ++p) {
        unsigned char c = (unsigned char)*p;
        if (c == '"' || c == '\\') out += '\\';
        if (c >= 32) out += (char)c;
    }
    return out + "\"";
}

static std::string fm1_studio_json(const sloop_editor &s){
    std::ostringstream o;
    o << "{\"version\":" << s.version << ",\"selected\":" << s.selected
      << ",\"playing\":" << s.playing << ",\"recording\":" << s.recording
      << ",\"bpm\":" << s.bpm << ",\"length\":" << s.length
      << ",\"lengthParameter\":" << s.length_parameter
      << ",\"position\":" << s.position << ",\"engine\":" << s.engine
      << ",\"preset\":" << s.preset << ",\"engines\":[";
    for (int i=0;i<16;i++) { if(i)o<<','; o<<quoted(s.engines[i]); }
    o << "],\"presets\":[";
    for (int i=0;i<s.preset_count;i++) { if(i)o<<','; o<<quoted(s.presets[i]); }
    o << "],\"tracks\":[";
    for (int i=0;i<8;i++) { if(i)o<<','; o<<"{\"level\":"<<s.tracks[i].level<<",\"mute\":"<<s.tracks[i].mute<<",\"name\":"<<quoted(s.tracks[i].name)<<",\"drum\":"<<s.tracks[i].drum<<",\"channel\":"<<s.tracks[i].channel<<'}'; }
    auto group = [&](const sloop_group &g) {
        o<<"{\"title\":"<<quoted(g.title)<<",\"page\":"<<g.page<<",\"params\":[";
        for(int k=0;k<g.count;k++) { if(k)o<<','; auto &p=g.param[k];
            o<<"{\"id\":"<<p.id<<",\"value\":"<<p.value<<",\"min\":"<<p.min<<",\"max\":"<<p.max
             <<",\"label\":"<<quoted(p.label)<<",\"text\":"<<quoted(p.text)<<",\"choices\":[";
            std::istringstream choices(p.choices); std::string choice; bool comma=false;
            while(std::getline(choices,choice,'\t')) { if(comma)o<<','; o<<quoted(choice.c_str()); comma=true; }
            o<<"],\"choiceValues\":[";for(int i=0;i<p.choice_count&&i<168;i++){if(i)o<<',';o<<p.choice_values[i];}o<<"]}"; }
        o<<"]}";
    };
    o << "],\"octave\":" << s.octave << ",\"current\":"; group(s.current);
    o << ",\"groups\":[";
    for (int i=0;i<s.group_count;i++) { if(i)o<<','; group(s.groups[i]); }
    o<<"],\"steps\":[";
    for(int i=0;i<(s.step_count==128?128:64);i++) { if(i)o<<','; o<<'['; for(int k=0;k<4;k++){if(k)o<<',';o<<s.steps[i][k];}o<<']'; }
    o<<"],\"lanes\":[";
    for(int i=0;i<16;i++){if(i)o<<',';o<<quoted(s.lanes[i]);}o<<"]"<<s.extra<<"}";
    return o.str();
}
