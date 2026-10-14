/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;

public final class TrackGeometryTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        for(Definitions.Firmware firmware:Definitions.ALL){
            Definitions.Studio ui=firmware.studio;int[] b=ui.trackBounds;int count=ui.tracks.length;
            boolean vertical=ui.trackAxis.equals("vertical");float start=b[vertical?0:1],end=b[vertical?2:3];
            for(int i=0;i<count;i++){
                float position=TrackGeometry.edge(start,end,i,count);
                float centre=(position+TrackGeometry.edge(start,end,i+1,count))/2;
                float x=vertical?centre:(b[0]+b[2])/2f,y=vertical?(b[1]+b[3])/2f:centre;
                check(TrackGeometry.hit(ui.trackAxis,b,count,x,y)==i);
                x=vertical?position:x;y=vertical?y:position;
                check(TrackGeometry.hit(ui.trackAxis,b,count,x,y)==i);

            }
            check(TrackGeometry.hit(ui.trackAxis,b,count,b[0]-1,b[1])==-1);
            check(TrackGeometry.hit(ui.trackAxis,b,count,b[0],b[1]-1)==-1);
            check(TrackGeometry.hit(ui.trackAxis,b,count,b[2],b[1])==-1);
            check(TrackGeometry.hit(ui.trackAxis,b,count,b[0],b[3])==-1);
            check(ui.trackButtonsEven==firmware.id.equals("move"));
            if(firmware.id.equals("move")){
                int[] clips=ui.trackBounds(4);
                check(TrackGeometry.hit(ui.trackAxis,clips,count,38,80)==0);
                check(TrackGeometry.hit(ui.trackAxis,clips,count,220,80)==7);
                check(TrackGeometry.hit(ui.trackAxis,ui.trackBounds(0),count,20,150)==0);
                check(TrackGeometry.hit(ui.trackAxis,ui.trackBounds(0),count,216,150)==7);
            }
        }
        System.out.println("PASS source-defined track axes, boundaries and all track hit areas");
    }
}
