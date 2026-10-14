package org.fm1.studio;
import java.util.Arrays;
public class ChordNotesTest {
    static void eq(int[] want,int[] got){if(!Arrays.equals(want,got))throw new AssertionError(Arrays.toString(got));}
    static void bad(String text){try{ChordNotes.parse(text);throw new AssertionError(text);}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        eq(new int[]{57,60,64,67},ChordNotes.parse("A3 C4 E4 G4"));
        eq(new int[]{57,60,64,65},ChordNotes.parse("A3 C4 E4 F4"));
        eq(new int[]{55,59,60,64},ChordNotes.parse("G3 B3 C4 E4"));
        eq(new int[]{55,59,62,65},ChordNotes.parse("G3 B3 D4 F4"));
        eq(new int[]{58,61,65},ChordNotes.parse("Bb3,Db4,F4"));
        eq(new int[]{0,127},ChordNotes.parse("C-1 G9"));
        eq(new int[]{60,64,67,71},ChordNotes.build(0,4,2));
        eq(new int[]{57,60,64,67},ChordNotes.build(9,3,3));
        eq(new int[]{57,60,64,65},ChordNotes.build(5,3,2,1));
        eq(new int[]{55,59,60,64},ChordNotes.build(0,3,2,2));
        eq(new int[]{55,59,62,65},ChordNotes.build(7,3,4,0));
        eq(new int[]{59,60},ChordNotes.parse("Cb4 B#3"));
        for(int n=0;n<128;n++)eq(new int[]{n},ChordNotes.parse(ChordNotes.name(n)));
        for(String s:new String[]{"","H4","C","60","C10","C-2","G#9","C4 C4","C4 B#3","C4 D4 E4 F4 G4","C99999999999999999999999"})bad(s);
        try{ChordNotes.build(11,9,2);throw new AssertionError();}catch(IllegalArgumentException expected){}
        System.out.println("PASS chord spelling, inversions, MIDI bounds and validation");
    }
}
