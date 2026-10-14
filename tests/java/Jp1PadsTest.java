/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;
public final class Jp1PadsTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        for(int bank=0;bank<3;bank++)for(int pad=0;pad<16;pad++){
            int note=Jp1Pads.sourceNote(pad,bank,52);
            check(Jp1Pads.bank(note,52)==bank);check(Jp1Pads.lane(note,52)==pad);check(Jp1Pads.step(note,52)==bank*16+pad);
        }
        check(Jp1Pads.lane(35,52)==-1&&Jp1Pads.lane(84,52)==-1);
        check(Jp1Pads.plays(Jp1Pads.AUTO,true)&&!Jp1Pads.plays(Jp1Pads.AUTO,false));
        check(Jp1Pads.plays(Jp1Pads.PLAY,false)&&!Jp1Pads.plays(Jp1Pads.STEPS,true));
        check(Jp1Pads.octaveControl(12,true,false)==-1&&Jp1Pads.octaveControl(3,true,false)==1);
        check(Jp1Pads.octaveControl(12,true,true)==0&&Jp1Pads.octaveControl(3,false,false)==0);
        check(Jp1Pads.pitch(52,-1)==40&&Jp1Pads.pitch(68,-1)==56&&Jp1Pads.pitch(36,-1)==24);
        check(Jp1Pads.pitch(1,-3)==0&&Jp1Pads.pitch(120,3)==127);
        System.out.println("PASS JP-1 physical pad positions, three step banks, Auto/Play/Steps, synth-only octave controls and whole-controller transposition");
    }
}
