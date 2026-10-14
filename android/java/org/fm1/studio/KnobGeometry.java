/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;

/** Hit areas follow the painted control, with no surrounding drag margin. */
final class KnobGeometry {
    static boolean dial(float x,float y,float centerX,float centerY,float radius){
        float dx=x-centerX,dy=y-centerY;return radius>0&&dx*dx+dy*dy<=radius*radius;
    }
    static boolean choice(float x,float y,float left,float top,float right,float bottom,float corner){
        if(right<=left||bottom<=top||x<left||x>right||y<top||y>bottom)return false;
        float r=Math.max(0,Math.min(corner,Math.min(right-left,bottom-top)/2));
        float cx=Math.max(left+r,Math.min(right-r,x)),cy=Math.max(top+r,Math.min(bottom-r,y));
        return dial(x,y,cx,cy,r)||r==0;
    }
}
