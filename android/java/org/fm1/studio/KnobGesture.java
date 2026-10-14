/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;

/** Drag tunes by default; a stationary hold hands the gesture to the scroll container. */
final class KnobGesture {
    static final int NONE=0,TAP=1,DOUBLE_TAP=2,DRAG=3;
    static final long SCROLL_HOLD_MS=300;
    private static final int PENDING=0,ADJUSTING=1,SCROLLING=2,PASSIVE=3;
    final float slop,doubleSlop;
    final long doubleTimeout;
    private float x,y,lastX,lastY;
    private long began,lastTap=-1;
    private boolean down,horizontal;
    private int mode=PENDING;
    KnobGesture(float slop,float doubleSlop,long doubleTimeout){this.slop=slop;this.doubleSlop=doubleSlop;this.doubleTimeout=doubleTimeout;}
    void begin(float x,float y,long time){begin(x,y,time,true);}
    void begin(float x,float y,long time,boolean tuningRegion){this.x=x;this.y=y;began=time;down=true;mode=tuningRegion?PENDING:PASSIVE;}
    boolean capturesDrag(){return down&&(mode==PENDING||mode==ADJUSTING);}
    boolean isScrolling(){return down&&mode==SCROLLING;}
    boolean hold(long time){if(down&&mode==PENDING&&time-began>=SCROLL_HOLD_MS){mode=SCROLLING;clearTapHistory();return true;}return false;}
    boolean move(float x,float y,long time){
        if(!down)return false;
        if(mode==PASSIVE&&Math.hypot(x-this.x,y-this.y)>slop){mode=SCROLLING;clearTapHistory();}
        if(mode==PENDING){
            if(time-began>=SCROLL_HOLD_MS)hold(time);
            else if(Math.hypot(x-this.x,y-this.y)>slop){mode=ADJUSTING;horizontal=Math.abs(x-this.x)>Math.abs(y-this.y);clearTapHistory();}
        }
        return mode==ADJUSTING;
    }
    float delta(float x,float y){return horizontal?x-this.x:this.y-y;}
    int end(float x,float y,long time){
        if(!down)return NONE;move(x,y,time);down=false;
        if(mode==SCROLLING)return NONE;
        if(mode==ADJUSTING)return DRAG;
        boolean twice=lastTap>=0&&time>=lastTap&&time-lastTap<=doubleTimeout&&Math.hypot(x-lastX,y-lastY)<=doubleSlop;
        if(twice){clearTapHistory();return DOUBLE_TAP;}
        lastTap=time;lastX=x;lastY=y;return TAP;
    }
    void cancel(){down=false;mode=PENDING;clearTapHistory();}
    void clearTapHistory(){lastTap=-1;}
}
