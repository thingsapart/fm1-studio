/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;

import java.util.*;
import java.util.regex.*;

/** Note-name entry uses scientific pitch: middle C is C4 (MIDI 60). */
final class ChordNotes {
    static final String[] ROOTS={"C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B"};
    static final String[] QUALITIES={"Major","Minor","Major 7","Minor 7","Dominant 7","Suspended 2","Suspended 4"};
    static final int[][] INTERVALS={{0,4,7},{0,3,7},{0,4,7,11},{0,3,7,10},{0,4,7,10},{0,2,7},{0,5,7}};
    static final String[] SYMBOLS={"","m","maj7","m7","7","sus2","sus4"};
    static int[] build(int root,int octave,int quality){
        if(root<0||root>11||octave<-1||octave>9||quality<0||quality>=INTERVALS.length)throw new IllegalArgumentException("Choose a root and chord shape.");
        int[] result=INTERVALS[quality].clone();for(int i=0;i<result.length;i++){result[i]+=12*(octave+1)+root;if(result[i]<0||result[i]>127)throw new IllegalArgumentException("Choose a lower octave so every note fits MIDI 0–127.");}return result;
    }
    static int[] build(int root,int octave,int quality,int inversion){
        int[] notes=build(root,octave,quality);if(inversion<0||inversion>=notes.length)throw new IllegalArgumentException("Choose a valid voicing.");
        for(int i=0;i<inversion;i++){notes[i]+=12;if(notes[i]>127)throw new IllegalArgumentException("Choose a lower octave so every note fits MIDI 0–127.");}Arrays.sort(notes);return notes;
    }
    static String chordName(int root,int quality){return ROOTS[root]+SYMBOLS[quality];}
    static int[] parse(String text){
        String trimmed=text.trim();if(trimmed.isEmpty())throw new IllegalArgumentException("Enter one to four notes, for example A3 C4 E4 G4.");
        String[] tokens=trimmed.split("[\\s,]+");if(tokens.length>4)throw new IllegalArgumentException("This sequencer supports up to four notes per step.");
        TreeSet<Integer> notes=new TreeSet<>();Pattern pattern=Pattern.compile("([A-Ga-g])([#♯b♭]?)(-?\\d+)");
        for(String token:tokens){Matcher match=pattern.matcher(token);if(!match.matches())throw new IllegalArgumentException("Use note names with octaves, for example C4 or Bb3.");
            int pc=new int[]{9,11,0,2,4,5,7}[Character.toUpperCase(match.group(1).charAt(0))-'A'];String accidental=match.group(2);if(accidental.equals("#")||accidental.equals("♯"))pc++;else if(!accidental.isEmpty())pc--;
            long oct;try{oct=Long.parseLong(match.group(3));}catch(NumberFormatException ex){throw new IllegalArgumentException("Notes must fit MIDI 0–127 (C-1 to G9).");}
            if(oct<-2||oct>10)throw new IllegalArgumentException("Notes must fit MIDI 0–127 (C-1 to G9).");
            long n=(oct+1)*12+pc;if(n<0||n>127)throw new IllegalArgumentException("Notes must fit MIDI 0–127 (C-1 to G9).");
            if(!notes.add((int)n))throw new IllegalArgumentException("Each note should appear once.");
        }
        int[] result=new int[notes.size()];int i=0;for(int n:notes)result[i++]=n;return result;
    }
    static String name(int note){return ROOTS[note%12]+(note/12-1);}
    static String format(int[] notes){StringBuilder text=new StringBuilder();for(int note:notes){if(text.length()>0)text.append(' ');text.append(name(note));}return text.toString();}
}
