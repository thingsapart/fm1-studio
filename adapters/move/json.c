    J(",\"nativeScreen\":%u,\"laneCount\":16,\"paletteMax\":%u,\"trackKind\":%u,\"clipCurrent\":%u,\"clips\":[",(unsigned)ctl.screen,(unsigned)NPALETTES-1,trk_kind(TSEL),clip_playing(song.sel));
    for(unsigned i=0;i<NCLIP;i++)J("%s%u",i?",":"",clip_state(song.sel,i));
    J("],\"grooveTemplate\":%u,\"grooves\":[",TSEL->gv.own?TSEL->gv.tmpl:song_groove.tmpl);for(unsigned i=0;i<GT_COUNT;i++)J("%s\"%s\"",i?",":"",GROOVES[i].name);J("]");
