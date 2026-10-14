/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio.tests;

import android.app.*;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Checks the real shared panel, responsive geometry and mode restoration. */
public final class Fm1PanelInstrumentation extends Instrumentation {
    private Object view;
    private String firmware;
    private Throwable failure;
    private Object field(String name)throws Exception {Field f=view.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(view);}
    private Object call(String name,Class<?>[] types,Object... args)throws Exception {Method m=view.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(view,args);}
    interface Check {void run()throws Exception;}
    private void main(Check check)throws Exception {failure=null;runOnMainSync(()->{try{check.run();}catch(Throwable e){failure=e;}});if(failure!=null)throw new Exception(failure);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);firmware=args==null?null:args.getString("firmware");start();}
    @Override public void onStart(){Bundle result=new Bundle();Activity host=null;boolean original=false;
        android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("wrapper",0);
        String previous=prefs.getString("firmware","sloop");
        if(firmware!=null)prefs.edit().putString("firmware",firmware).commit();
        try {
            host=startActivitySync(new Intent().setClassName("org.fm1.studio","org.fm1.studio.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Field f=host.getClass().getDeclaredField("instrument");f.setAccessible(true);view=f.get(host);SystemClock.sleep(700);
            original=(Boolean)field("fm1Layout");
            main(()->{call("openPage",new Class<?>[]{String.class},"PANEL");if(!(Boolean)call("fm1Panel",new Class<?>[0]))call("togglePanelLayout",new Class<?>[0]);});
            for(int orientation:new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}){
                final Activity activity=host;main(()->activity.setRequestedOrientation(orientation));SystemClock.sleep(900);
                main(()->{
                    check(((List<?>)field("knobs")).size()==8,"Eight shared knobs");
                    Object screen=field("screen"),definition=field("definition");
                    Method render=screen.getClass().getDeclaredMethod("renderBounds");render.setAccessible(true);android.graphics.RectF rect=(android.graphics.RectF)render.invoke(screen);
                    Method hit=screen.getClass().getDeclaredMethod("trackAt",float.class,float.class);hit.setAccessible(true);
                    Field boundsField=definition.getClass().getDeclaredField("trackBounds"),axisField=definition.getClass().getDeclaredField("trackAxis"),tracksField=definition.getClass().getDeclaredField("tracks");
                    boundsField.setAccessible(true);axisField.setAccessible(true);tracksField.setAccessible(true);int[] bounds=(int[])boundsField.get(definition);boolean columns="vertical".equals(axisField.get(definition));int count=((String[])tracksField.get(definition)).length;
                    for(int i=0;i<count;i++){float x=columns?bounds[0]+(bounds[2]-bounds[0])*(i+.5f)/count:(bounds[0]+bounds[2])/2f,y=columns?(bounds[1]+bounds[3])/2f:bounds[1]+(bounds[3]-bounds[1])*(i+.5f)/count;check((Integer)hit.invoke(screen,rect.left+x*rect.width()/240,rect.top+y*rect.height()/240)==i,"Native screen track target "+i);}

                    check(((Map<?,?>)field("panelBindings")).size()==14,"Twelve buttons plus two octave buttons");
                    check(((View)field("play")).getVisibility()==View.GONE,"Duplicate transport hidden");
                    View navigation=(View)field("tabRow");check(navigation.getParent()==view,"Navigation stays in the bottom dock");
                    check(((View)field("transport")).getVisibility()==View.GONE,"FM-1 has no extra top toolbar");
                    View keys=(View)field("keyboard");check(keys.getParent()==view,"Playing surface docked across root");
                    Method pads=keys.getClass().getDeclaredMethod("isDrum");pads.setAccessible(true);
                    check(((View)field("keyTools")).getVisibility()==((Boolean)pads.invoke(keys)?View.GONE:View.VISIBLE),"Octave row appears only with piano keys");
                    check(((View)field("keysSwitch")).getParent()==field("tabRow"),"Playing surface toggle stays in navigation");
                    call("setKeys",new Class<?>[]{boolean.class},false);check(((View)field("keysSwitch")).isShown(),"Can reopen collapsed keys from navigation");
                    call("setKeys",new Class<?>[]{boolean.class},true);
                    call("togglePanelLayout",new Class<?>[0]);check(!(Boolean)call("fm1Panel",new Class<?>[0]),"Studio restored");
                    check(((View)field("play")).getVisibility()==View.VISIBLE,"Studio transport restored");
                    check(((View)field("tabRow")).getParent()==view,"Studio navigation remains docked");
                    call("togglePanelLayout",new Class<?>[0]);
                    call("openPage",new Class<?>[]{String.class},"zp12".equals(firmware)?"MIX":"STEPS");check(((View)field("tabRow")).getParent()==view,"STEPS navigation remains docked");
                    call("openPage",new Class<?>[]{String.class},"PANEL");
                    call("choosePanelLayout",new Class<?>[0]);check(field("sheet")!=null,"Layout chooser opens");call("closeSheets",new Class<?>[0]);
                });
                final int[] before={0};
                main(()->{View display=(View)field("displayArea");before[0]=orientation==ActivityInfo.SCREEN_ORIENTATION_PORTRAIT?display.getHeight():display.getWidth();call("setDisplayZoom",new Class<?>[]{float.class,boolean.class},.7f,false);});
                SystemClock.sleep(200);
                main(()->{View display=(View)field("displayArea");int after=orientation==ActivityInfo.SCREEN_ORIENTATION_PORTRAIT?display.getHeight():display.getWidth();check(after<before[0],"FM-1 display responds to zoom");call("setDisplayZoom",new Class<?>[]{float.class,boolean.class},1f,false);});
                SystemClock.sleep(400);
                android.graphics.Bitmap shot=getUiAutomation().takeScreenshot();
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getCacheDir(),"fm1-"+orientation+".png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}shot.recycle();
            }
            main(()->{
                Button panel=((Button[])field("tabButtons"))[0];check(panel.getText().toString().contains("STUDIO"),"FM-1 exposes a direct Studio return");panel.performClick();
                check(!(Boolean)field("fm1Layout")&&!(Boolean)field("jpKnobPane")&&field("page").equals("PANEL"),"FM-1 tab returns to standard Studio");
                call("openPage",new Class<?>[]{String.class},"JP LIVE");check(panel.getText().toString().contains("STUDIO"),"JP Performance exposes a direct Studio return");panel.performClick();
                check(field("page").equals("PANEL")&&!(Boolean)field("jpKnobPane"),"JP Performance returns to standard Studio");
                call("togglePanelLayout",new Class<?>[0]);
            });
            final java.util.Set<Integer> colors=new java.util.HashSet<>();
            for(int i=0;i<8;i++){main(()->{
                org.json.JSONObject originalState=(org.json.JSONObject)field("state"),playing=new org.json.JSONObject(originalState.toString());playing.put("playing",1);call("accept",new Class<?>[]{org.json.JSONObject.class},playing);
                for(Object item:((Map<?,?>)field("panelBindings")).entrySet()){Map.Entry<?,?> entry=(Map.Entry<?,?>)item;Field target=entry.getValue().getClass().getDeclaredField("target");target.setAccessible(true);if(target.getInt(entry.getValue())==10)colors.add(((Button)entry.getKey()).getCurrentTextColor());}
                call("accept",new Class<?>[]{org.json.JSONObject.class},originalState);
            });SystemClock.sleep(120);}
            check(colors.contains(0xffff514d)&&colors.size()==2,"PLAY pulses red while running");
            result.putString("stream","PASS FM-1: zoom, track hit targets, PLAY pulse, shared controls, portrait/landscape, keyboard docking/collapse and Studio restoration\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
        finally {if(firmware!=null)prefs.edit().putString("firmware",previous).commit();if(host!=null){final Activity activity=host;final boolean restore=original;try{main(()->{if((Boolean)field("fm1Layout")!=restore)call("togglePanelLayout",new Class<?>[0]);activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);});}catch(Exception ignored){}}}
    }
}
