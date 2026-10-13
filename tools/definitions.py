#!/usr/bin/env python3
"""Generate Java and plain C descriptors from the same firmware source files."""
import json
from dataclasses import asdict
from pathlib import Path
import sys
PROJECT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT))
from firmwares import FIRMWARES

def java(firmwares, destination):
    q = lambda s: json.dumps(s, ensure_ascii=True)
    strings=lambda values:'new String[]{'+','.join(q(v) for v in values)+'}'
    ints=lambda values:'new int[]{'+','.join(str(v) for v in values)+'}'
    rows=[]
    for f in firmwares:
        screens=[]
        for screen in f.screens:
            controls=','.join(f'new Control({q(c.label)},{c.kind},{c.target})' for c in screen.controls)
            enter=','.join('new int[]{%d,%d,%d}'%event for event in screen.enter)
            screens.append(f'new Screen({q(screen.title)},new Control[]{{{controls}}},new int[][]{{{enter}}})')
        ui=f.studio
        tabs=','.join(f'new Tab({q(label)},{q(component)})' for label,component in ui.tabs)
        menu=','.join(f'new MenuEntry({q(entry.label)},{q(entry.action)})' for entry in ui.menu)
        sheets=','.join(f'new Sheet({q(name)},{strings(labels)})' for name,labels in ui.sheets)
        labels=','.join(f'new String[]{{{q(key)},{q(value)}}}' for key,value in ui.labels)
        pages=','.join(f'new ParameterPage({q(page.native)},{q(page.title)},{strings(page.parameters)},new int[][]{{'+','.join(ints(event) for event in page.enter)+'})' for page in ui.pages)
        bindings=','.join(ints(binding) for binding in ui.bindings)
        studio=f'new Studio({strings(ui.tracks)},{q(ui.track_axis)},{ints(ui.track_bounds)},{str(ui.track_buttons_even).lower()},new int[][]{{{','.join(ints(row) for row in ui.track_screen_bounds)}}},{ui.sample_slots},{ui.project_slots},{ui.drum_lanes},{ui.fixed_drum},{str(ui.drum_keys_physical).lower()},{ui.midi_route_parameter},{ui.nudge_min},{ui.nudge_max},{ints(ui.drum_notes)},{strings(ui.layout)},{strings(ui.features)},{strings(ui.midi_groups)},{strings(ui.sound_pages)},{ints(ui.events)},new Tab[]{{{tabs}}},new MenuEntry[]{{{menu}}},new Sheet[]{{{sheets}}},new ParameterPage[]{{{pages}}},new String[][]{{{labels}}},new int[][]{{{bindings}}})'
        rows.append(f'new Firmware({q(f.id)},{q(f.name)},new Screen[]{{{",".join(screens)}}},{studio})')
    destination.parent.mkdir(parents=True,exist_ok=True)
    destination.write_text("""// Generated from firmwares/*.py. Do not edit.
package org.fm1.wrapper;
final class Definitions {
 static final class Control {final String label;final int kind,target;Control(String l,int k,int t){label=l;kind=k;target=t;}}
 static final class Screen {final String title;final Control[] controls;final int[][] enter;Screen(String t,Control[] c,int[][] e){title=t;controls=c;enter=e;}}
 static final class Firmware {final String id,name;final Screen[] screens;final Studio studio;Firmware(String i,String n,Screen[] s,Studio ui){id=i;name=n;screens=s;studio=ui;}}
 static final class Tab {final String title,component;Tab(String t,String c){title=t;component=c;}}
 static final class MenuEntry {final String label,action;MenuEntry(String l,String a){label=l;action=a;}}
 static final class Sheet {final String name;final String[] labels;Sheet(String n,String[] l){name=n;labels=l;}}
 static final class ParameterPage {final String nativeTitle,title;final String[] parameters;final int[][] enter;ParameterPage(String n,String t,String[] p,int[][] e){nativeTitle=n;title=t;parameters=p;enter=e;}}
 static final class Studio {
  final String trackAxis;final int[] trackBounds;final int[][] trackScreenBounds;final boolean trackButtonsEven;
  final String[] tracks,layout,features,midiGroups,soundPages;final int sampleSlots,projectSlots,drumLanes,fixedDrum,midiRouteParameter,nudgeMin,nudgeMax;final boolean drumKeysPhysical;final int[] events,drumNotes;
  final Tab[] tabs;final MenuEntry[] menu;final Sheet[] sheets;final ParameterPage[] pages;final String[][] labels;final int[][] bindings;
  Studio(String[] t,String axis,int[] bounds,boolean even,int[][] screenBounds,int s,int projects,int d,int f,boolean physical,int route,int lo,int hi,int[] notes,String[] l,String[] caps,String[] midi,String[] sound,int[] e,Tab[] pages,MenuEntry[] m,Sheet[] sh,ParameterPage[] pg,String[][] text,int[][] b){tracks=t;trackAxis=axis;trackBounds=bounds;trackButtonsEven=even;trackScreenBounds=screenBounds;sampleSlots=s;projectSlots=projects;drumLanes=d;fixedDrum=f;drumKeysPhysical=physical;midiRouteParameter=route;nudgeMin=lo;nudgeMax=hi;drumNotes=notes;layout=l;features=caps;midiGroups=midi;soundPages=sound;events=e;tabs=pages;menu=m;sheets=sh;this.pages=pg;labels=text;bindings=b;}
  int[] trackBounds(int screen){for(int[] b:trackScreenBounds)if(b[0]==screen)return new int[]{b[1],b[2],b[3],b[4]};return trackBounds;}
  boolean has(String feature){for(String f:features)if(f.equals(feature))return true;return false;}
  String[] sheet(String name){for(Sheet s:sheets)if(s.name.equals(name))return s.labels;throw new IllegalArgumentException(name);}
  String[] order(String name){for(Sheet s:sheets)if(s.name.equals(name))return s.labels;return new String[0];}
  int rank(String name,String text){String[] labels=order(name);for(int i=0;i<labels.length;i++)if(text.equals(labels[i])||text.startsWith(labels[i]+":")||text.startsWith(labels[i]+" "))return i;return labels.length;}
  String label(String text){for(String[] pair:labels)if(pair[0].equals(text))return pair[1];return text;}
  int operation(int kind,int op){for(int[] b:bindings)if(b[0]==kind&&b[1]==op)return b[2];return op;}
 }
 static Firmware active;
"""+''.join(' static Firmware create_'+f.id+'(){return '+row+';}\n' for f,row in zip(firmwares,rows))+""" static final Firmware[] ALL={"""+','.join('create_'+f.id+'()' for f in firmwares)+"""};
}
""")

def c(firmwares, destination):
    q = lambda s: json.dumps(s, ensure_ascii=False)
    lines = ['/* Generated from firmware definitions. UTF-8 labels. */', '#pragma once',
             'typedef struct { const char *label; int kind, target; } fm1_control;',
             'typedef struct { int kind, target, value; } fm1_binding;',
             'typedef struct { const char *title; const fm1_control *controls; unsigned count; const fm1_binding *enter; unsigned enter_count; } fm1_screen;']
    for f in firmwares:
        for i, s in enumerate(f.screens):
            prefix = f'fm1_{f.id}_{i}'
            entries = ','.join('{%s,%d,%d}' % (q(c.label), c.kind, c.target) for c in s.controls) or '{0,0,0}'
            lines.append(f'static const fm1_control {prefix}_controls[] = {{{entries}}};')
            bindings = ','.join('{%d,%d,%d}' % e for e in s.enter) or '{0,0,0}'
            lines.append(f'static const fm1_binding {prefix}_enter[] = {{{bindings}}};')
        entries = ','.join('{%s,fm1_%s_%d_controls,%d,fm1_%s_%d_enter,%d}' %
                           (q(s.title), f.id, i, len(s.controls), f.id, i, len(s.enter)) for i, s in enumerate(f.screens))
        lines.append(f'static const fm1_screen fm1_{f.id}_screens[] = {{{entries}}};')
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text('\n'.join(lines) + '\n')

if __name__ == '__main__':
    java(FIRMWARES, PROJECT / 'build/generated/org/fm1/wrapper/Definitions.java')
    c(FIRMWARES, PROJECT / 'build/generated/screens.h')
