/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;
import java.util.Arrays;
public class ChordPlacementTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        int[] pitches={57,60,64,67};ChordPlacement chord=new ChordPlacement(1,pitches,2,"Am7");boolean[] occupied=new boolean[64];
        check(chord.error(2,64,occupied)==null);check(chord.error(62,64,occupied)==null);check(chord.error(63,64,occupied)!=null);check(chord.error(-1,64,occupied)!=null);
        occupied[3]=true;check(chord.error(2,64,occupied).contains("Step 4"));occupied[3]=false;
        pitches[0]=0;check(Arrays.equals(chord.notes(),new int[]{57,60,64,67}));int[] copy=chord.notes();copy[0]=127;check(chord.notes()[0]==57);
        check(chord.track==1&&chord.length==2);check(chord.error(2,64,new boolean[16])!=null);
        for(int[] invalid:new int[][]{{},{60,60},{128},{-1},{60,61,62,63,64}}){try{new ChordPlacement(0,invalid,2,"bad");throw new AssertionError();}catch(IllegalArgumentException expected){}}
        System.out.println("PASS fixed chord pitches, immutable stamp selection, duration bounds and occupied-step rejection");
    }
}
