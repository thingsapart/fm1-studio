/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio.tests;
import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Decoder -> real Activity -> semantic JNI -> native firmware, with user saves restored. */
public final class FirmwareMidiInstrumentation extends Instrumentation {
    private String engine;private Activity activity;private Object view,nativeType,controller,mapping;private Method accept;
    private Throwable failure;private final Map<Integer,Integer> notes=new HashMap<>();private int lastTarget=-1;
    private Object call(Object target,String name,Class<?>[] types,Object... args)throws Exception{
        Class<?> c=target instanceof Class?(Class<?>)target:target.getClass();Method m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target instanceof Class?null:target,args);
    }
    private Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private void main(Runnable r)throws Exception{failure=null;runOnMainSync(()->{try{r.run();}catch(Throwable e){failure=e;}});if(failure!=null)throw new Exception(failure);}
    private void activity(String name,int... values){invoke(activity,name,values);}
    private void invoke(Object object,String name,int... values){try{Class<?>[] t=new Class<?>[values.length];Object[] a=new Object[values.length];for(int i=0;i<a.length;i++){t[i]=int.class;a[i]=values[i];}call(object,name,t,a);}catch(Exception e){throw new RuntimeException(e);}}
    private void event(int kind,int id,int value){invoke(nativeType,"event",kind,id,value);}
    private JSONObject snapshot()throws Exception{return new JSONObject((String)call(nativeType,"editor",new Class<?>[0]));}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(engine+": "+message);}
    private void packet(int status,int d1,int d2)throws Exception{int p=(status>>4)|(status<<8)|(d1<<16)|(d2<<24);main(()->{try{check((Boolean)accept.invoke(controller,p,mapping),"decoder rejected packet");}catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(100);}
    private void cc(int number,int value)throws Exception{packet(0xb0,number,value);}
    private void sweep(int number)throws Exception{cc(number,0);cc(number,127);cc(number,0);cc(number,127);SystemClock.sleep(300);}
    private JSONObject role(int slot)throws Exception{return (JSONObject)call(view,"jpRole",new Class<?>[]{int.class},slot);}
    private void setupDecoder()throws Exception{
        ClassLoader loader=getTargetContext().getClassLoader();nativeType=Class.forName("org.fm1.studio.Native",true,loader);
        Class<?> type=Class.forName("org.fm1.studio.Jp1Controller",true,loader),sink=Class.forName("org.fm1.studio.Jp1Controller$Sink",true,loader),map=Class.forName("org.fm1.studio.Jp1Controller$Mapping",true,loader);
        Object proxy=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{sink},(o,m,a)->{
            String name=m.getName();if(name.equals("button"))activity("controllerButton",(Integer)a[0]);
            else if(name.equals("bank"))activity("controllerBank",(Integer)a[0]);
            else if(name.equals("absolute"))activity("controllerAbsoluteKnob",(Integer)a[0],(Integer)a[1]);
            else if(name.equals("held"))call(activity,"controllerHeld",new Class<?>[]{int.class,boolean.class},a);
            else if(name.equals("note")){
                int ch=(Integer)a[0],note=(Integer)a[1],velocity=(Integer)a[2],key=ch*128+note;
                Integer old=notes.remove(key);if(old!=null){event(16,old,0);activity("controllerNote",old,0);}
                if(velocity>0){lastTarget=(Integer)call(activity,"controllerPadTarget",new Class<?>[]{int.class,int.class,int.class},note,velocity,52);
                    if(lastTarget>=0){notes.put(key,lastTarget);event(16,lastTarget,velocity);activity("controllerNote",lastTarget,velocity);}}
            }
            return null;
        });
        Constructor<?> ctor=type.getDeclaredConstructor(sink);ctor.setAccessible(true);controller=ctor.newInstance(proxy);
        Method defaults=map.getDeclaredMethod("defaults");defaults.setAccessible(true);mapping=defaults.invoke(null);
        accept=type.getDeclaredMethod("accept",int.class,map);accept.setAccessible(true);
    }
    private void select(String title)throws Exception{
        JSONObject s=snapshot();JSONArray groups=s.getJSONArray("groups");int index=-1;
        for(int i=0;i<groups.length();i++)if(title.equals(groups.getJSONObject(i).getString("title"))){index=i;break;}
        check(index>=0,"missing "+title+" metadata");final int choice=index+1;main(()->invoke(view,"selectGroupAt",choice));SystemClock.sleep(350);
    }
    private void checkField(int bank,int ccNumber)throws Exception{
        main(()->invoke(view,"controllerBank",bank));SystemClock.sleep(300);
        Object knob=((ArrayList<?>)field(view,"jpAssignedKnobs")).get(4);check(knob!=null,"bank "+bank+" has no K1");
        int id=(Integer)field(knob,"id"),max=(Integer)field(knob,"max");sweep(ccNumber);
        JSONObject s=snapshot();JSONArray groups=s.getJSONArray("groups");boolean found=false;
        for(int i=0;i<groups.length();i++){JSONArray params=groups.getJSONObject(i).getJSONArray("params");for(int k=0;k<params.length();k++){JSONObject p=params.getJSONObject(k);if(p.getInt("id")==id){check(p.getInt("value")==max,"bank "+bank+" K1 did not reach maximum: "+p);found=true;}}}
        check(found,"lost bank parameter target");
    }
    private void test()throws Exception{
        setupDecoder();main(()->{try{call(field(activity,"midi"),"pause",new Class<?>[0]);}catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(500);
        // Fresh factory state; every modification is isolated from the user's flash image.
        cc(22,127);check(snapshot().getInt("playing")==1,"Play");cc(22,127);check(snapshot().getInt("playing")==0,"Play toggle");
        cc(24,127);check(snapshot().getInt("recording")!=0,"REC arm not published");cc(24,127);check(snapshot().getInt("recording")==0,"REC arm cannot cancel");
        cc(20,127);check(snapshot().getJSONArray("tracks").getJSONObject(0).getInt("mute")==1,"BT mute");cc(20,0);cc(20,127);check(snapshot().getJSONArray("tracks").getJSONObject(0).getInt("mute")==0,"BT unmute");
        cc(19,127);cc(21,127);check(engine.equals("zp12")?snapshot().getInt("pad")==0:snapshot().getInt("selected")==0,"previous/next");
        cc(47,0);cc(47,127);check(engine.equals("zp12")?snapshot().getInt("pad")==31:snapshot().getInt("selected")==3,"ALGO endpoint");cc(47,0);
        sweep(48);JSONObject s=snapshot();check(s.getInt("preset")==s.getJSONArray("presets").length()-1,"PRESETS endpoint");
        sweep(49);check(snapshot().getInt("bpm")==240,"TEMPO endpoint");
        select(engine.equals("zp12")?"SOUND":"ENV");checkField(0,50);checkField(1,34);checkField(2,42);
        if(engine.equals("zp12")){packet(0x90,52,80);check(lastTarget>=0&&lastTarget%128==48,"Auto must play native pads with keyboard hidden");packet(0x80,52,0);}
        // JP pad notes cross the Activity routing boundary into the real native engine.
        getTargetContext().getSharedPreferences("jp1",0).edit().putInt("padMode",1).commit();
        Field mode=activity.getClass().getDeclaredField("controllerPadMode");mode.setAccessible(true);main(()->{try{mode.setInt(activity,1);}catch(Exception e){throw new RuntimeException(e);}});
        int banks=engine.equals("zp12")?3:1;
        for(int bank=0;bank<banks;bank++)for(int pad=0;pad<16;pad++){
            if(!engine.equals("zp12")&&(pad==3||pad==12))continue;
            int note=52+(bank==1?16:bank==2?-16:0)+(3-pad/4)*4+pad%4;
            packet(0x90,note,80);
            check(lastTarget>=0,"pad "+pad+" ignored");
            if(engine.equals("zp12")){int target=36+pad+(bank==1?16:0);check(lastTarget%128==target,"pad bank note mapping");check(snapshot().getInt("pad")==target-36,"native pad differs from mapping");}
            packet(0x80,note,0);
        }
        cc(23,127);check(snapshot().getInt("playing")==0,"Stop");
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);engine=args==null?"zp12":args.getString("engine","zp12");start();}
    @Override public void onStart(){Bundle result=new Bundle();Context context=getTargetContext();SharedPreferences wrapper=context.getSharedPreferences("wrapper",0),jp=context.getSharedPreferences("jp1",0);String original=wrapper.getString("firmware","sloop");Map<String,?> prefs=new HashMap<>(jp.getAll());File flash=new File(context.getFilesDir(),engine+".flash");byte[] backup=null;boolean existed=flash.exists();
        try{
            if(existed){backup=Files.readAllBytes(flash.toPath());check(flash.delete(),"cannot isolate save");}
            wrapper.edit().putString("firmware",engine).commit();jp.edit().putInt("padMode",0).commit();
            activity=startActivitySync(new Intent().setClassName("org.fm1.studio","org.fm1.studio.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));view=field(activity,"instrument");SystemClock.sleep(700);test();
            result.putString("stream","PASS "+engine+" decoded JP-1 transport, REC toggle, mute, navigation, three knob banks, endpoint pickup, preset/tempo and pad-to-native mapping\n");
        }catch(Throwable e){failure=e;result.putString("stream","FAIL "+android.util.Log.getStackTraceString(e));}
        finally{try{
            if(activity!=null){main(()->activity.finish());waitForIdleSync();SystemClock.sleep(200);}
            if(existed&&backup!=null)Files.write(flash.toPath(),backup);else if(!existed)flash.delete();
            wrapper.edit().putString("firmware",original).commit();SharedPreferences.Editor edit=jp.edit().clear();for(Map.Entry<String,?> e:prefs.entrySet()){Object v=e.getValue();if(v instanceof String)edit.putString(e.getKey(),(String)v);else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);else if(v instanceof Long)edit.putLong(e.getKey(),(Long)v);else if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Float)edit.putFloat(e.getKey(),(Float)v);else if(v instanceof Set){Set<String> strings=new HashSet<>();for(Object item:(Set<?>)v)strings.add((String)item);edit.putStringSet(e.getKey(),strings);}}edit.commit();
        }catch(Throwable e){result.putString("stream",result.getString("stream","")+" RESTORE FAILED "+e);failure=e;}}
        finish(result.getString("stream","").startsWith("PASS")&&!result.getString("stream","").contains("RESTORE FAILED")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
