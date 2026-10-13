"""MOVE semantic editor overlays. Eight tracks and per-track drums stay native."""
from pathlib import Path
PROJECT=Path(__file__).resolve().parents[2]
def stage_studio(out):
    source=PROJECT/'adapters/sloop/native'
    interactions=(source/'interactions.c').read_text()
    import re
    interactions=re.sub(r'static void android_song\(.*?\n}\n', '', interactions, flags=re.S)
    interactions=interactions.replace('ft_on || (song.playing && arrangement_enabled)','ft_on')
    interactions=interactions.replace('if(android_throw) thr_req=android_throw;', '')
    interactions=interactions.replace('thr_req=0;', '').replace('er_mute=0;', '')
    interactions=interactions.replace('sus_set(&trk[i],0);trk[i].bend=0;', 'trk_note_off(&trk[i],0);')
    interactions=interactions.replace('drum_input(lane_of_key(key),LV_SOFT,0,1)', 'drum_input(TSEL,lane_of_key(key),LV_SOFT,0,1)')
    interactions=re.sub(r'    if\(op==47\).*?\n', '',interactions)
    interactions=re.sub(r'    if\(op==48\).*?\n', '',interactions)
    interactions=re.sub(r'    if\(op==19\).*?\n', '',interactions)
    interactions=interactions.replace('if(op==51){studio_open(SC_SONG);return;}', 'if(op==51){screen_go(SC_CLIPS);return;}')
    interactions=interactions.replace('if(op==50){', 'if(op==54){if(!song.playing&&value>=0&&value<TK_COUNT){ctl.kind_req=value;ctl_do(ACT_KIND,t);}return;}\n    if(op==55){if(value>=0&&value<GT_COUNT){tr->gv.own=1;tr->gv.tmpl=value;}return;}\n    if(op==56){groove_commit(tr);return;}\n    if(op==50){')
    interactions=interactions.replace('track_select(TRK_DRUM);TDRUM->p[P_E0]', 'track_select(t);trk_set_kind(tr,TK_DRUM);tr->p[P_E0]')
    interactions=interactions.replace('t==TRK_DRUM','is_drum(tr)').replace('id==TRK_DRUM','is_drum(&trk[id])').replace('id!=TRK_DRUM','!is_drum(&trk[id])')
    # All native clip tracks have their own mute/step arrays.
    interactions=interactions.replace('drum_mute', 'studio_mutes[song.sel]').replace('id<44','id<48')
    interactions=re.sub(r'    if\(id==27.*?\n','',interactions)
    interactions=interactions.replace('er_mute=value!=0;', '')
    interactions=re.sub(r'if\(id==3\)ml_clear_all\(\);.*?settings_later=1;', 'settings_later=1;',interactions)
    # Keep exact real FM6/DSYN/sample metadata from the original Android editor.
    interactions=interactions.replace('arrangement_enabled,live_sec,live_req,srec,arrangement.loop,arrangement_clock.index,settings.palette,settings.zoom,settings.lowcut,mlu.on', '0,-1,-1,0,0,-1,settings.palette,settings.zoom,settings.lowcut,0')
    interactions=re.sub(r'J\("\],\\"arrangement.*?\n', 'J("],\\"arrangement\\":[]");\n',interactions)
    interactions=re.sub(r'J\("\],\\"chain.*?\n', 'J(",\\"chain\\":[]");\n',interactions)
    interactions=interactions.replace('J("],\\"userSounds', 'J(",\\"userSounds')
    interactions=re.sub(r'J\("\],\\"midiLearn.*?\n','J("],\\"midiLearn\\":[]");\n',interactions)
    interactions=interactions.replace('J("],\\"fm6', 'J(",\\"fm6',1)
    interactions=interactions.replace('sus_on[song.sel],TSEL->bend+8192','0,8192')
    interactions=interactions.replace('id<9304','id<9303').replace('kind==3&&id>=0&&id<4','kind==3&&id>=0&&id<3')
    interactions=interactions.replace('for(unsigned i=0;i<4;i++)J(\"%s%d\",i?\",\":\"\",project_used(i))','for(unsigned i=0;i<FMP_NPROJ;i++)J(\"%s%d\",i?\",\":\"\",project_used(i))')
    interactions=interactions.replace('for(unsigned i=0;i<4;i++)J(\"%s%d\",i?\",\":\"\",usr_nz[i])','for(unsigned i=0;i<3;i++)J(\"%s%d\",i?\",\":\"\",usr_nz[i])')
    # Project objects have eight-track FMP streams, not four-track SLOOP structures.
    interactions=re.sub(r'    if\(kind==4&&id>=0&&id<4.*?\n','',interactions)
    interactions=interactions.replace('    return 0;\n}\nunsigned sloop_object_export', '    if(kind==4)return studio_project_import(id,data,size);\n    return 0;\n}\nunsigned sloop_object_export')
    interactions=interactions.replace('    return 0;\n}\nint sloop_validate_image', '    if(kind==4)return studio_project_export(id,out,capacity);\n    return 0;\n}\nint sloop_validate_image')
    interactions=re.sub(r'    if\(kind==4&&id>=0&&id<4.*?\n','',interactions)
    interactions=interactions.replace('project_load(value)','project_load_fmp(value)').replace('project_save(value)','project_save_fmp(value)')
    interactions=interactions[:interactions.index('int sloop_validate_image')]
    # MOVE uses FMP project storage; its fourth sample slot is reserved for it.
    editor=(source/'editor.c').read_text()
    editor=editor.replace('#include "interactions.c"','#include "studio.h"\n#include "interactions.c"')
    editor=editor.replace('if(mlu.on)ml_knob(track,p);','')
    editor=editor.replace('if(PAGES[t].scope==SC_DRUM){drum_page=1;kb_grid=0;}','')
    editor=editor.replace('(t >= G_TXSRC && t <= G_TXDUCK) || t==G_SYNC || t==G_ROUTE || t==G_CHOTH || t==G_BEND || (t>=G_CH1 && t<=G_CH3) || t==G_DRCH || t==G_MIDI','t==G_SYNC || t==G_ROUTE || t==G_DRCH || t==G_MIDI')
    editor=editor.replace('t < NPART','t < NTRK && is_synth(&trk[t])')
    editor=editor.replace('i == TRK_DRUM ? song.g[G_DRLVL] : trk[i].p[P_LEVEL]','trk[i].p[P_LEVEL]')
    editor=editor.replace('i == TRK_DRUM ? "DRUMS" : ENGINES[trk[i].eng_req % NENGINES]->name','is_drum(&trk[i]) ? "DRUMS" : ENGINES[trk[i].eng_req % NENGINES]->name')
    editor=editor.replace('is_drum(TSEL) ? FM1_GLOBAL_PARAMETER_BASE+G_DRLVL : song.sel*256+P_LEVEL','song.sel*256+P_LEVEL')
    editor=editor.replace('is_drum(TSEL) ? song.g[G_DRLVL] : TSEL->p[P_LEVEL]','TSEL->p[P_LEVEL]')
    editor=editor.replace('if (t == TRK_DRUM) song.g[G_DRLVL] = clamp(value, 0, 127);\n        else ', '')
    end=interactions.index('\n#undef J\n}',interactions.index('static void android_json'))
    interactions=interactions[:end]+"\n"+(PROJECT/'adapters/move/json.c').read_text()+interactions[end:]
    (out/'native/interactions.c').write_text(interactions)
    editor=editor.replace('#include \"studio.h\"\n#include \"interactions.c\"','#include \"studio.h\"\n'+(PROJECT/'adapters/move/prefix.c').read_text()+'\n#include \"interactions.c\"')
    (out/'native/studio.c').write_text(editor+"\n"+(PROJECT/'adapters/move/files.c').read_text())
