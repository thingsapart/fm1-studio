/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;
final class Native {
    // Keep event numbers aligned with native/core.h and native/editor.h.
    static final int KEY=2, SELECT=8, PARAM=9, GLOBAL=10, ENGINE=11, PRESET=12,
        MIX=13, MUTE=14, STEP=15, NOTE=16, RECORD=17, TRANSPORT=18, EDITOR_HOME=19, PAGE=20, ACTION=21, DETAIL=22, LOCK=23, PERFORM=24, SONG=25, CONFIG=26, ADVANCED=27;
    static { System.loadLibrary("fm1host"); }
    static native String select(String library,String id,String directory);
    static native void start();
    static native void stop();
    static native boolean restore(byte[] image);
    static native void emit(int kind,int id,int value);
    static void event(int kind,int id,int value){Definitions.Studio ui=Definitions.active.studio;
        if(kind==ACTION)id=id/256*256+ui.operation(kind,id%256);
        else if(kind==DETAIL)id=id/32*32+ui.operation(kind,id%32);
        else if(kind==SONG)id=id/16*16+ui.operation(kind,id%16);
        else if(kind==PERFORM||kind==CONFIG)id=ui.operation(kind,id);
        emit(kind>=8?ui.events[kind-8]:kind,id,value);}
    static volatile int selected,drumTrack=-1,drumChannel=-1;
    static final int GLOBAL_BASE=8192;
    static native void frame(int[] argb);
    static native String status();
    static native String editor();
    static native int[] midi();
    static native void data(int kind,int id,byte[] data);
    static native byte[] object(int kind,int id);
    static native void sample(int slot, byte[] image);
}
