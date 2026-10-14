/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;

/** Reveal a cell with the minimum scroll, accounting for the sticky row/column header. */
final class StepViewport {
    static int reveal(int offset,int viewport,int header,int start,int end,int content){
        if(viewport<=header)return offset;
        int next=offset;
        if(start<offset+header)next=start-header;
        else if(end>offset+viewport)next=end-viewport;
        return Math.max(0,Math.min(Math.max(0,content-viewport),next));
    }
}
