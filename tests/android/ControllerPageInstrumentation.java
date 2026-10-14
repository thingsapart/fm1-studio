/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio.tests;

import android.app.*;
import android.content.Intent;
import android.os.*;
import org.json.*;
import java.lang.reflect.*;

/** Exercises the installed app's real Views, native snapshots and controller entry points. */
public final class ControllerPageInstrumentation extends Instrumentation {
    private Object view,midi;
    private int originalBank,originalPage;
    private Throwable failure;
    private int count;
    private long checks;
    private boolean viewportOnly,padsOnly,tracksOnly;
    private Activity host;
    private Object call(String name,Class<?>[] types,Object... args)throws Exception{
        Method method=view.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(view,args);
    }
    private void main(Runnable task)throws Exception{
        failure=null;runOnMainSync(()->{try{task.run();}catch(Throwable e){failure=e;}});
        if(failure!=null)throw new Exception("Controller page regression",failure);
    }
    private void invoke(String name,int... args){
        try{Class<?>[] types=new Class<?>[args.length];Object[] boxed=new Object[args.length];for(int i=0;i<args.length;i++){types[i]=int.class;boxed[i]=args[i];}call(name,types,boxed);}
        catch(Exception e){throw new RuntimeException(e);}
    }
    private JSONObject role(){try{return (JSONObject)call("jpRole",new Class<?>[]{int.class},0);}catch(Exception e){throw new RuntimeException(e);}}
    private void check(int expected){int actual=role().optInt("value");checks++;if(actual!=expected)throw new AssertionError("Expected page "+expected+", got "+actual+" / "+role());}
    private void testTrackChanges() throws Exception {
        Field stateField=view.getClass().getDeclaredField("state");stateField.setAccessible(true);
        int originalTrack=((JSONObject)stateField.get(view)).optInt("selected");
        try {
            for(int bank=0;bank<3;bank++){
                final int selectedBank=bank;
                main(()->invoke("controllerBank",selectedBank));
                int tracks=((JSONObject)call("jpRole",new Class<?>[]{int.class},1)).optInt("max")+1;
                // No pickup/acquisition movement: start at a different track, then traverse both ways.
                main(()->invoke("selectTrack",tracks/2));SystemClock.sleep(350);
                for(int step=0;step<tracks*2+2;step++){
                    final int target=step<tracks?step:step<tracks*2?tracks*2-1-step:step==tracks*2?tracks-1:0;
                    main(()->{
                        try {
                            invoke("controllerAbsoluteKnob",selectedBank*8+1,Math.round(target*127f/(tracks-1)));
                        }catch(Exception e){throw new RuntimeException(e);}
                    });
                    SystemClock.sleep(350);
                    main(()->{
                        try {
                            if(((JSONObject)stateField.get(view)).optInt("selected")!=target)throw new AssertionError("Track knob did not select "+target+" actual="+((JSONObject)stateField.get(view)).optInt("selected")+" ready="+call("controllerKnobsReady",new Class<?>[0])+" role="+call("jpRole",new Class<?>[]{int.class},1)+" record="+((JSONObject)stateField.get(view)).optInt("recordState"));
                            int max=role().optInt("max");
                            // Move away from the new track's page: navigation must not wait for pickup.
                            int raw=role().optInt("value")<max/2?127:0;
                            invoke("controllerAbsoluteKnob",selectedBank*8,raw);check(raw==0?0:max);
                            invoke("controllerAbsoluteKnob",selectedBank*8,raw==0?127:0);check(raw==0?max:0);
                        }catch(Exception e){throw new RuntimeException(e);}
                    });
                }
                // Fast sweeps overlap native snapshots and control rebuilds.
                for(int direction:new int[]{1,-1}){
                    for(int step=0;step<128;step++){
                        final int raw=direction>0?step:127-step;
                        main(()->invoke("controllerAbsoluteKnob",selectedBank*8+1,raw));
                        SystemClock.sleep(2);
                    }
                    SystemClock.sleep(350);
                    final int expected=direction>0?tracks-1:0;
                    main(()->{
                        try{if(((JSONObject)stateField.get(view)).optInt("selected")!=expected)throw new AssertionError("Fast track sweep missed endpoint "+expected);}
                        catch(Exception e){throw new RuntimeException(e);}
                    });
                }
            }
        } finally {
            main(()->invoke("selectTrack",originalTrack));SystemClock.sleep(350);
            main(()->{invoke("controllerBank",originalBank);invoke("selectGroupAt",originalPage);});
        }
    }
    private void testDrumPads(){
        Field mode=null,keys=null;int originalMode=0;boolean originalKeys=false;
        try{
            if(!(Boolean)call("isDrum",new Class<?>[0]))throw new AssertionError("Select a drum track before running kit routing checks");
            Field stateField=view.getClass().getDeclaredField("state");stateField.setAccessible(true);JSONObject state=(JSONObject)stateField.get(view);
            JSONArray groups=state.optJSONArray("groups");int kit=-1;for(int i=0;i<groups.length();i++)if("KIT".equals(groups.optJSONObject(i).optString("title")))kit=i+1;
            if(kit<0)throw new AssertionError("KIT page missing");
            mode=host.getClass().getDeclaredField("controllerPadMode");mode.setAccessible(true);originalMode=mode.getInt(host);mode.setInt(host,0);
            keys=view.getClass().getDeclaredField("keysOpen");keys.setAccessible(true);originalKeys=keys.getBoolean(view);keys.setBoolean(view,false);
            invoke("controllerBank",0);invoke("selectGroupAt",kit);
            if(!(Boolean)call("controllerPadsPlay",new Class<?>[]{int.class},0))throw new AssertionError("Auto still edits steps on KIT");
            if((Boolean)call("controllerPadsPlay",new Class<?>[]{int.class},2))throw new AssertionError("Explicit Steps mode ignored");
            Method base=host.getClass().getDeclaredMethod("controllerPadBase");base.setAccessible(true);int middle=(Integer)base.invoke(host);
            Field padBank=host.getClass().getDeclaredField("controllerPadBank");padBank.setAccessible(true);int bank=padBank.getInt(host);
            Field definition=view.getClass().getDeclaredField("definition");definition.setAccessible(true);Object layout=definition.get(view);
            Field notesField=layout.getClass().getDeclaredField("drumNotes");notesField.setAccessible(true);int[] notes=(int[])notesField.get(layout);
            Method target=host.getClass().getDeclaredMethod("controllerPadTarget",int.class,int.class,int.class);target.setAccessible(true);
            Field page=view.getClass().getDeclaredField("page");page.setAccessible(true);
            for(int lane=0;lane<16;lane++){
                int source=middle+(bank==1?16:bank==2?-16:0)+(3-lane/4)*4+lane%4;
                int actual=(Integer)target.invoke(host,source,100,middle);
                int expected=lane<notes.length?state.optInt("selected")*128+notes[lane]:-1;
                if(actual!=expected||!"PANEL".equals(page.get(view)))throw new AssertionError("Pad "+lane+" navigated or targeted "+actual+" instead of "+expected);
            }
            invoke("showHome");if((Boolean)call("controllerPadsPlay",new Class<?>[]{int.class},0))throw new AssertionError("KIT override leaked into HOME");
        }catch(Exception e){throw new RuntimeException(e);}
        finally{try{if(mode!=null)mode.setInt(host,originalMode);if(keys!=null)keys.setBoolean(view,originalKeys);invoke("controllerBank",originalBank);invoke("selectGroupAt",originalPage);}catch(Exception e){throw new RuntimeException(e);}}
    }
    private void testViewport(){
        JSONObject original=null;Field stateField=null;
        try{
            stateField=view.getClass().getDeclaredField("state");stateField.setAccessible(true);original=(JSONObject)stateField.get(view);
            Field gridField=view.getClass().getDeclaredField("grid");gridField.setAccessible(true);Object grid=gridField.get(view);Class<?> type=grid.getClass();
            Field horizontalField=type.getDeclaredField("horizontal");horizontalField.setAccessible(true);
            android.widget.HorizontalScrollView horizontal=(android.widget.HorizontalScrollView)horizontalField.get(grid);
            Field verticalField=type.getDeclaredField("vertical");verticalField.setAccessible(true);
            android.widget.ScrollView vertical=(android.widget.ScrollView)verticalField.get(grid);
            Field leftField=type.getDeclaredField("left");leftField.setAccessible(true);int left=leftField.getInt(grid);
            Field topField=type.getDeclaredField("top");topField.setAccessible(true);int top=topField.getInt(grid);
            Field cellField=type.getDeclaredField("cell");cellField.setAccessible(true);int cell=cellField.getInt(grid);
            Method reveal=type.getDeclaredMethod("revealStep",int.class,int.class);reveal.setAccessible(true);
            Method follow=type.getDeclaredMethod("followPosition");follow.setAccessible(true);
            Field bankField=view.getClass().getDeclaredField("bank");bankField.setAccessible(true);
            JSONObject snapshot=new JSONObject(original.toString());snapshot.put("length",48);snapshot.put("playing",1);stateField.set(view,snapshot);
            boolean drum=(Boolean)call("isDrum",new Class<?>[0]);int lane=drum?original.optJSONArray("lanes").length()-1:60;
            reveal.invoke(grid,15,lane);
            android.view.View gridView=(android.view.View)grid;float width=(gridView.getWidth()-left)/16f;
            if(left+16*width>horizontal.getScrollX()+horizontal.getWidth()+1)throw new AssertionError("Edited last column remains offscreen");
            if(drum&&top+(lane+1)*cell>vertical.getScrollY()+vertical.getHeight())throw new AssertionError("Edited drum lane remains offscreen");
            snapshot.put("position",17);follow.invoke(grid);
            if(bankField.getInt(view)!=1||left+width<horizontal.getScrollX()+left-1)throw new AssertionError("Playhead failed to cross bank");
            int scroll=horizontal.getScrollX();snapshot.put("position",18);follow.invoke(grid);
            if(horizontal.getScrollX()!=scroll)throw new AssertionError("Visible playhead caused scrolling");
            Field touching=type.getDeclaredField("touching");touching.setAccessible(true);touching.setBoolean(grid,true);
            snapshot.put("position",35);follow.invoke(grid);if(bankField.getInt(view)!=1)throw new AssertionError("Follow interrupted touch");
            touching.setBoolean(grid,false);follow.invoke(grid);if(bankField.getInt(view)!=2)throw new AssertionError("Follow did not resume");
            snapshot.put("position",0);follow.invoke(grid);
            if(bankField.getInt(view)!=0||horizontal.getScrollX()!=0)throw new AssertionError("Loop start remains hidden");
        }catch(Exception e){throw new RuntimeException(e);}
        finally{try{if(stateField!=null&&original!=null)stateField.set(view,original);}catch(Exception e){throw new RuntimeException(e);}}
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);viewportOnly=args!=null&&"true".equals(args.getString("viewport"));padsOnly=args!=null&&"true".equals(args.getString("pads"));tracksOnly=args!=null&&"true".equals(args.getString("tracks"));start();}
    @Override public void onStart(){
        Bundle result=new Bundle();Activity activity=null;
        try{
            activity=startActivitySync(new Intent().setClassName("org.fm1.studio","org.fm1.studio.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            host=activity;
            Field field=activity.getClass().getDeclaredField("instrument");field.setAccessible(true);view=field.get(activity);
            Field midiField=activity.getClass().getDeclaredField("midi");midiField.setAccessible(true);midi=midiField.get(activity);
            main(()->{try{Method pause=midi.getClass().getDeclaredMethod("pause");pause.setAccessible(true);pause.invoke(midi);}catch(Exception e){throw new RuntimeException(e);}});
            SystemClock.sleep(600);
            Field bankField=view.getClass().getDeclaredField("jpKnobBank");bankField.setAccessible(true);originalBank=bankField.getInt(view);
            main(()->originalPage=role().optInt("value"));
            if(tracksOnly){
                testTrackChanges();
                result.putString("stream","PASS track selection without acquisition: all tracks both ways, endpoint jumps, fast sweeps, all banks and immediate page navigation\n");
                finish(Activity.RESULT_OK,result);return;
            }
            if(padsOnly){
                main(()->testDrumPads());
                result.putString("stream","PASS kit overview: all drum pad destinations, Auto without keyboard, no sequence navigation, explicit Steps override and exit behavior\n");
                finish(Activity.RESULT_OK,result);return;
            }
            if(viewportOnly){
                main(()->{try{call("openPage",new Class<?>[]{String.class},"STEPS");}catch(Exception e){throw new RuntimeException(e);}});
                SystemClock.sleep(400);
                main(()->testViewport());
                result.putString("stream","PASS actual Steps viewport: edited cell visibility, drum lane, bank crossing, loop wrap, visible playhead stability and touch suppression\n");
                finish(Activity.RESULT_OK,result);return;
            }
            for(int bank=0;bank<3;bank++){
                final int selectedBank=bank;
                main(()->{invoke("controllerBank",selectedBank);invoke("showHome");count=role().optInt("max")+1;});
                SystemClock.sleep(300);
                main(()->{invoke("controllerAbsoluteKnob",selectedBank*8,0);check(0);});
                for(int delay:new int[]{2,65}){
                    for(int raw=1;raw<=127;raw++){
                        final int position=raw;
                        main(()->{invoke("controllerAbsoluteKnob",selectedBank*8,position);check(Math.round(position*(count-1)/127f));});
                        SystemClock.sleep(delay);
                    }
                    for(int raw=126;raw>=0;raw--){
                        final int position=raw;
                        main(()->{invoke("controllerAbsoluteKnob",selectedBank*8,position);if(position<126||delay>=80)check(Math.round(position*(count-1)/127f));});
                        SystemClock.sleep(delay);
                    }
                    SystemClock.sleep(300);main(()->check(0));
                }
                // No native snapshot can arrive between these relative turns on the UI thread.
                main(()->{
                    for(int i=1;i<count;i++){invoke("controllerAssignedKnob",0,1);check(i);}
                    for(int i=count-2;i>=0;i--){invoke("controllerAssignedKnob",0,-1);check(i);}
                });
                SystemClock.sleep(300);main(()->check(0));
            }
            main(()->{invoke("controllerBank",originalBank);invoke("selectGroupAt",originalPage);});
            result.putString("stream","PASS actual page Views: all banks, slow/fast forward/back sweeps, relative bursts, HOME and "+checks+" assertions\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
}
