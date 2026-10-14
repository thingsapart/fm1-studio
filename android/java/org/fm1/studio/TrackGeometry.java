/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;

/** Geometry shared by native-display hit testing and aligned track buttons. */
final class TrackGeometry {
    static float edge(float start,float end,int index,int count){return start+(end-start)*index/count;}
    static int hit(String axis,int[] bounds,int count,float x,float y){
        if(x<bounds[0]||x>=bounds[2]||y<bounds[1]||y>=bounds[3])return -1;
        boolean vertical=axis.equals("vertical");
        float position=vertical?x:y,start=bounds[vertical?0:1],end=bounds[vertical?2:3];
        return Math.min(count-1,(int)((position-start)*count/(end-start)));
    }
}
