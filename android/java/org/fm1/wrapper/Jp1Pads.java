/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;

/** Physical reading order is shared by all three note banks. */
final class Jp1Pads {
    static final int AUTO=0,PLAY=1,STEPS=2;
    static boolean plays(int mode,boolean keyboardVisible){return mode==PLAY||(mode==AUTO&&keyboardVisible);}
    static int bank(int note,int middleBase){int delta=note-middleBase;return delta>=0&&delta<16?0:delta>=16&&delta<32?1:delta>=-16&&delta<0?2:-1;}
    static int lane(int note,int middleBase){if(bank(note,middleBase)<0)return -1;int pad=Math.floorMod(note-middleBase,16);return (3-pad/4)*4+pad%4;}
    static int step(int note,int middleBase){int bank=bank(note,middleBase);return bank<0?-1:bank*16+lane(note,middleBase);}
    static int sourceNote(int lane,int bank,int middleBase){return middleBase+(bank==1?16:bank==2?-16:0)+(3-lane/4)*4+lane%4;}
    static int octaveControl(int lane,boolean plays,boolean drum){return !plays||drum?0:lane==12?-1:lane==3?1:0;}
    static int pitch(int note,int octaveOffset){return Math.max(0,Math.min(127,note+12*octaveOffset));}
}
