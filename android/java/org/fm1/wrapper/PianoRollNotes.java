/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;

import java.util.ArrayList;
import java.util.List;

/** Native ties extend an onset; adjacent onsets remain distinct even at the same pitch. */
final class PianoRollNotes {
    static final class Span {
        final int start,end;
        Span(int start,int end){this.start=start;this.end=end;}
    }
    static List<Span> spans(int[][] notes,boolean[] ties,int pitch,int first,int last){
        ArrayList<Span> result=new ArrayList<>();int count=Math.min(notes.length,ties.length);
        first=Math.max(0,first);last=Math.min(count,last);if(first>=last)return result;
        int start=first;while(start>0&&ties[start])start--;
        while(start<last){
            if(ties[start]){start++;continue;}
            int end=start+1;while(end<count&&ties[end])end++;
            if(notes[start]!=null)for(int note:notes[start])if(note==pitch){result.add(new Span(start,end));break;}
            start=end;
        }
        return result;
    }
}
