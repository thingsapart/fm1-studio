/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;

/** Colors describe the actual pad actions, independent of transport and Android views. */
final class Jp1Feedback {
    static final int ACTIVE=0xffffff,STEP=0x003818,TIE=0x001428,LOCK=0x301800,SELECTED=0x403000,RECORD=0x600008;
    static final class State {
        boolean plays,drum,muted,playing,recording;
        int length,position,bank,octave,selectedLane,lanes=16;
        final boolean[] enabled=new boolean[16],ties=new boolean[16],locks=new boolean[16],live=new boolean[16],mutedPads=new boolean[16];
        final int[] pitches=new int[16];
    }
    static int[] colors(State s){
        int[] result=new int[16];for(int pad=0;pad<16;pad++){
            int color;
            if(!s.plays){
                int step=s.bank*16+pad;if(step>=s.length){result[Jp1Rgb.slot(pad)]=0;continue;}
                int window=Math.floorMod(s.bank,4);
                color=s.enabled[pad]?(s.ties[pad]?TIE:new int[]{STEP,0x003038,0x281038,0x383000}[window]):s.locks[pad]?LOCK:new int[]{0x020504,0x000407,0x040107,0x040400}[window];
                if(s.muted)color=s.enabled[pad]?0x180004:0x030000;
                else if(s.recording)color=s.enabled[pad]?0x280003:0x060000;
                if(s.playing&&step==s.position)color=s.recording?RECORD:ACTIVE;
            }else if(s.drum){
                if(pad>=s.lanes){result[Jp1Rgb.slot(pad)]=0;continue;}
                color=drumColor(s.pitches[pad]);if(pad==s.selectedLane)color=SELECTED;
                if(s.muted||s.mutedPads[pad])color=0x180004;if(s.live[pad])color=s.recording?RECORD:ACTIVE;
            }else{
                int pitch=s.pitches[pad],pc=Math.floorMod(pitch,12);
                color=pc==0?0x003018:pc==1||pc==3||pc==6||pc==8||pc==10?0x030307:0x0c100e;
                if(s.muted)color=0x180004;if(s.live[pad])color=s.recording?RECORD:ACTIVE;
                if(pad==12)color=s.octave<=-3?0x020308:0x001848;
                if(pad==3)color=s.octave>=3?0x080402:0x482400;
            }
            result[Jp1Rgb.slot(pad)]=color;
        }return result;
    }
    private static int drumColor(int note){
        if(note==35||note==36)return 0x201000;
        if(note>=37&&note<=40)return 0x181800;
        if(note==42||note==44||note==46)return 0x001820;
        if(note==41||note==43||note==45||note==47||note==48||note==50)return 0x000e24;
        if(note==49||note==51||note==52||note==55||note==57||note==59)return 0x180020;
        return 0x00180c;
    }
}
