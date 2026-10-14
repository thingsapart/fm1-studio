/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;

import java.util.List;
public class PianoRollNotesTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static int[][] empty(int size){int[][] notes=new int[size][0];return notes;}
    public static void main(String[] args){
        int[][] notes=empty(32);boolean[] ties=new boolean[32];
        for(int i=0;i<4;i++)notes[i]=new int[]{60};
        List<PianoRollNotes.Span> repeated=PianoRollNotes.spans(notes,ties,60,0,16);check(repeated.size()==4);
        for(int i=0;i<4;i++)check(repeated.get(i).start==i&&repeated.get(i).end==i+1);
        notes=empty(32);notes[0]=new int[]{60};ties[1]=ties[2]=ties[3]=true;
        List<PianoRollNotes.Span> held=PianoRollNotes.spans(notes,ties,60,0,16);check(held.size()==1&&held.get(0).start==0&&held.get(0).end==4);
        notes[4]=new int[]{60};held=PianoRollNotes.spans(notes,ties,60,0,16);check(held.size()==2&&held.get(1).start==4);
        // Each pitch in a held chord shares the span, with no phantom pitches.
        notes[0]=new int[]{57,60,64,67};for(int pitch:new int[]{57,60,64,67})check(PianoRollNotes.spans(notes,ties,pitch,0,4).get(0).end==4);
        check(PianoRollNotes.spans(notes,ties,61,0,4).isEmpty());
        // A hold crosses the bank edge while retaining its original onset and duration.
        notes=empty(32);ties=new boolean[32];notes[14]=new int[]{60};for(int i=15;i<=18;i++)ties[i]=true;
        held=PianoRollNotes.spans(notes,ties,60,16,32);check(held.size()==1&&held.get(0).start==14&&held.get(0).end==19);
        held=PianoRollNotes.spans(notes,ties,60,0,16);check(held.size()==1&&held.get(0).end==19);
        // Ties without an onset do not manufacture a note; pattern-end notes stay bounded.
        notes=empty(32);ties=new boolean[32];ties[0]=ties[1]=true;check(PianoRollNotes.spans(notes,ties,60,0,16).isEmpty());
        notes[31]=new int[]{60};held=PianoRollNotes.spans(notes,ties,60,16,32);check(held.get(0).start==31&&held.get(0).end==32);
        System.out.println("PASS distinct retriggers, continuous held notes/chords, bank crossings and pattern boundaries");
    }
}
