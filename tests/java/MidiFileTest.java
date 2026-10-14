/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;
import java.util.*;
public final class MidiFileTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){int[][] notes=new int[64][4],levels=new int[64][4],ratchets=new int[64][4];for(int[] a:notes)Arrays.fill(a,-1);notes[0][0]=60;notes[4][0]=64;int[] duration=new int[64],vel=new int[64],nudge=new int[64];Arrays.fill(duration,1);Arrays.fill(vel,100);duration[0]=4;
  byte[] bytes=MidiFile.write(notes,duration,vel,nudge,levels,ratchets,16,2,120,false);List<MidiFile.Track> tracks=MidiFile.read(bytes);check(tracks.size()==1&&tracks.get(0).notes.size()==2&&tracks.get(0).bpm==120);
  byte[] pattern=MidiFile.pattern(tracks.get(0),2,false);check(pattern.length==652&&pattern[12]==60&&pattern[17]==0&&pattern[27]==1&&pattern[52]==64);
  MidiFile.Track drums=new MidiFile.Track();drums.ppq=480;drums.notes.add(new MidiFile.Note(0,36,32,9));drums.notes.get(0).end=120;pattern=MidiFile.pattern(drums,2,true);check((pattern[12]&1)!=0&&(pattern[14]&3)==1&&pattern[17]==0);
  try{MidiFile.read(new byte[14]);throw new AssertionError();}catch(IllegalArgumentException expected){}
  MidiFile.Track longTrack=new MidiFile.Track();longTrack.ppq=480;MidiFile.Note last=new MidiFile.Note(127*120,72,100,0);last.end=128*120;longTrack.notes.add(last);
  pattern=MidiFile.pattern(longTrack,2,false,MidiFile.DRUM_NOTES,128);check(pattern.length==1292&&(pattern[4]&255)==128&&pattern[12+127*10]==72);
  pattern=MidiFile.pattern(longTrack,2,false);check(pattern.length==652&&(pattern[4]&255)==1);
  System.out.println("PASS MIDI file note lengths, tempo, drum levels and malformed-file rejection");
 }
}
