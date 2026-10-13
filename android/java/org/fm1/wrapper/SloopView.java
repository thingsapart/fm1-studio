/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;
import android.content.Context;
import android.graphics.*;
import android.util.SparseIntArray;
import android.view.*;
import java.util.*;

final class SloopView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap screen=Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888);
    private final int[] pixels=new int[240*240];
    private final List<Control> controls=new ArrayList<>();
    private final SparseIntArray pointers=new SparseIntArray();
    private final SparseIntArray touches=new SparseIntArray();
    private final Map<Integer,Float> knobY=new HashMap<>();
    private RectF display=new RectF();
    private boolean active;
    private final boolean controlsOnly;
    private final String[] buttons=new String[14];
    private final String[] knobs={"SELECT","ALGO","PRESETS","K1","K2","K3","K4"};
    private static final int[] WHITE={0,2,4,6,7,9,11,12,14,16,18,19,21,23,24,26};
    private static final int[] BLACK={1,3,5,8,10,13,15,17,20,22,25};
    private final Runnable refresh=new Runnable(){ public void run(){
        if(!active) return;
        if(!controlsOnly){Native.frame(pixels); screen.setPixels(pixels,0,240,0,0,240,240);} invalidate(); postDelayed(this,33);
    }};
    static final class Control {
        final RectF box; final int kind,id; final String name; final boolean black;
        Control(RectF box,int kind,int id,String name,boolean black){this.box=box;this.kind=kind;this.id=id;this.name=name;this.black=black;}
    }
    SloopView(Context context){this(context,false);}
    SloopView(Context context,boolean controlsOnly){super(context);for(int i=0;i<buttons.length;i++)buttons[i]=Definitions.active.screens[0].controls[i].label;this.controlsOnly=controlsOnly;setFocusable(true);setContentDescription("Original "+Definitions.active.name+" function buttons and encoders; hold buttons while playing the keyboard");}
    void resume(){active=true;removeCallbacks(refresh);post(refresh);}
    void pause(){active=false;removeCallbacks(refresh);releaseAll();}
    private void add(float x,float y,float w,float h,int kind,int id,String name,boolean black){controls.add(new Control(new RectF(x+2,y+2,x+w-2,y+h-2),kind,id,name,black));}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        releaseAll(); controls.clear();
        if(controlsOnly){
            display=new RectF();float bw=w/7f,bh=h*.27f;
            for(int i=0;i<14;i++)add((i%7)*bw,(i/7)*bh,bw,bh,0,i,buttons[i],false);
            for(int i=0;i<7;i++)add(i*bw,2*bh,bw,h-2*bh,1,i,knobs[i],false);
            return;
        }
        float top=h*.52f, size=Math.min(top-16,w*.50f);
        display=new RectF(8,8,8+size,8+size);
        float start=size+20, bw=(w-start)/2f, bh=(top-8)/7f;
        for(int i=0;i<14;i++) add(start+(i%2)*bw,8+(i/2)*bh,bw,bh,0,i,buttons[i],false);
        float kh=h*.12f;
        for(int i=0;i<7;i++)add(i*w/7f,top,w/7f,kh,1,i,knobs[i]+" ↕",false);
        float keyTop=top+kh, whiteH=h-keyTop, ww=w/16f;
        for(int i=0;i<WHITE.length;i++)add(i*ww,keyTop,ww,whiteH,2,WHITE[i],Integer.toString(i+1),false);
        for(int key:BLACK){
            int index=0;while(index<WHITE.length && WHITE[index]<key)index++;
            add(index*ww-ww*.32f,keyTop,ww*.64f,whiteH*.58f,2,key,"",true);
        }
    }
    @Override protected void onDraw(Canvas canvas){
        canvas.drawColor(Color.rgb(16,18,22));paint.setFilterBitmap(false);if(!controlsOnly)canvas.drawBitmap(screen,null,display,paint);
        paint.setTypeface(InstrumentStyle.font(getContext()));paint.setTextAlign(Paint.Align.CENTER);
        for(int i=0;i<controls.size();i++){
            Control c=controls.get(i);boolean held=touches.get(i)>0;
            paint.setColor(held?Color.rgb(72,166,143):c.black?Color.rgb(25,28,34):c.kind==2?Color.rgb(220,224,225):Color.rgb(45,50,59));
            canvas.drawRoundRect(c.box,5,5,paint);
            paint.setColor(c.kind==2&&!c.black?Color.BLACK:Color.WHITE);
            paint.setTextSize(Math.max(9,Math.min(c.box.height()*.25f,c.box.width()*.22f)));
            canvas.drawText(c.name,c.box.centerX(),c.box.centerY()+paint.getTextSize()*.35f,paint);
        }
    }
    private int hit(float x,float y){for(int i=controls.size()-1;i>=0;i--)if(controls.get(i).box.contains(x,y))return i;return -1;}
    private void press(int pointer,int index,float y){
        if(index<0)return;
        pointers.put(pointer,index); Control c=controls.get(index);
        if(c.kind==1){knobY.put(pointer,y);return;}
        int count=touches.get(index);touches.put(index,count+1);if(count==0)Native.event(c.kind,c.id,1);
    }
    private void release(int pointer){
        int index=pointers.get(pointer,-1);pointers.delete(pointer);knobY.remove(pointer);if(index<0)return;
        Control c=controls.get(index);if(c.kind==1)return;
        int count=Math.max(0,touches.get(index)-1);touches.put(index,count);if(count==0)Native.event(c.kind,c.id,0);
    }
    void releaseAll(){while(pointers.size()>0)release(pointers.keyAt(0));touches.clear();knobY.clear();}
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked(),i=event.getActionIndex(),id=event.getPointerId(i);
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){press(id,hit(event.getX(i),event.getY(i)),event.getY(i));}
        else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){release(id);performClick();}
        else if(action==MotionEvent.ACTION_CANCEL)releaseAll();
        else if(action==MotionEvent.ACTION_MOVE){
            for(int p=0;p<event.getPointerCount();p++){
                int pointer=event.getPointerId(p),index=pointers.get(pointer,-1);if(index<0)continue;
                Control c=controls.get(index);float x=event.getX(p),y=event.getY(p);
                if(c.kind==1){float old=knobY.get(pointer),unit=8*getResources().getDisplayMetrics().density;int delta=(int)((old-y)/unit);
                    if(delta!=0){Native.event(1,c.id,delta);knobY.put(pointer,old-delta*unit);}}
                else if(c.kind==2){int next=hit(x,y);if(next!=index){release(pointer);if(next>=0&&controls.get(next).kind==2)press(pointer,next,y);}}
            }
        }
        invalidate();return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
