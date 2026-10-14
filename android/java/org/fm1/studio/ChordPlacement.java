/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;

/** A remembered chord's pitches are independent of the row tapped to place it. */
final class ChordPlacement {
    final int track,length;
    final String name;
    private final int[] pitches;
    ChordPlacement(int track,int[] notes,int length,String name){
        if(track<0||notes.length<1||notes.length>4||length<1)throw new IllegalArgumentException("Invalid chord");
        java.util.HashSet<Integer> unique=new java.util.HashSet<>();for(int note:notes)if(note<0||note>127||!unique.add(note))throw new IllegalArgumentException("Invalid chord notes");
        this.track=track;this.pitches=notes.clone();this.length=length;this.name=name;
    }
    int[] notes(){return pitches.clone();}
    String error(int step,int patternLength,boolean[] occupied){
        if(step<0||step>=patternLength||(long)step+length>patternLength||occupied.length<patternLength)return "The chord needs "+length+" steps. Choose an earlier step or shorten its length.";
        for(int i=step;i<step+length;i++)if(occupied[i])return "Step "+(i+1)+" already has notes. Choose empty steps or clear them first.";
        return null;
    }
}
