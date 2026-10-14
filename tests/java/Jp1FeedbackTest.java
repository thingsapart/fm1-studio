/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;
public final class Jp1FeedbackTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    private static int at(int[] colors,int pad){return colors[Jp1Rgb.slot(pad)];}
    public static void main(String[] args){
        Jp1Feedback.State s=new Jp1Feedback.State();s.length=20;s.bank=1;s.position=18;s.playing=true;s.enabled[0]=true;s.enabled[1]=true;s.ties[1]=true;s.locks[3]=true;
        int[] c=Jp1Feedback.colors(s);check(at(c,0)==0x003038&&at(c,1)==Jp1Feedback.TIE&&at(c,2)==Jp1Feedback.ACTIVE&&at(c,3)==Jp1Feedback.LOCK&&at(c,4)==0);
        s.recording=true;c=Jp1Feedback.colors(s);check(at(c,2)==Jp1Feedback.RECORD);s.bank=2;c=Jp1Feedback.colors(s);for(int color:c)check(color==0);
        s=new Jp1Feedback.State();s.plays=true;s.drum=true;s.lanes=8;s.selectedLane=1;s.pitches[0]=36;s.pitches[1]=38;s.pitches[2]=42;s.live[0]=true;s.mutedPads[2]=true;
        c=Jp1Feedback.colors(s);check(at(c,0)==Jp1Feedback.ACTIVE&&at(c,1)==Jp1Feedback.SELECTED&&at(c,2)==0x180004&&at(c,8)==0);s.recording=true;check(at(Jp1Feedback.colors(s),0)==Jp1Feedback.RECORD);
        s=new Jp1Feedback.State();s.plays=true;s.pitches[0]=60;s.pitches[1]=61;s.pitches[2]=62;s.live[2]=true;
        c=Jp1Feedback.colors(s);check(at(c,0)!=at(c,1)&&at(c,2)==Jp1Feedback.ACTIVE&&at(c,12)==0x001848&&at(c,3)==0x482400);
        s.octave=-3;check(at(Jp1Feedback.colors(s),12)==0x020308);s.octave=3;check(at(Jp1Feedback.colors(s),3)==0x080402);
        s.muted=true;check(at(Jp1Feedback.colors(s),0)==0x180004);
        System.out.println("PASS JP feedback step bank/length/ties/locks/playhead, recording, drum lane selection/mutes, synth pitch colors and octave limits");
    }
}
