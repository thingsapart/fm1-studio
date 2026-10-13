/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;

import android.app.Dialog;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Bundle;
import android.os.Build;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.function.IntConsumer;

/** The original display is permanent. Everything beside it is a port-owned control surface. */
final class StudioView extends LinearLayout {
    static final int BG=0xff101312, CARD=0xff202523, GREEN=0xff20d586, TEXT=0xffe7eee9, DIM=0xff92a298;
    private final LinearLayout console, transport, tracks, sounds, tabs, tabRow, keyTools, displayArea;
    private final TrackRail trackRail;
    private final Definitions.Firmware firmware=Definitions.active;
    final Definitions.Studio definition=firmware.studio;
    private final int trackCount=definition.tracks.length;
    private final LinearLayout[] trackCells=new LinearLayout[trackCount];
    boolean compactDisplay,cameraClearance;
    private static final float TRACK_COLUMN_DP=83.2f;
    private float largeZoom=1,compactZoom=1;
    float maximumZoom=1;
    private int soundsTrack=-1;
    private final Button play, record, tempo, engine, preset, groupButton;
    private final Button[] trackButtons=new Button[trackCount], tabButtons=new Button[definition.tabs.length];
    private final FrameLayout content;
    private final Keyboard keyboard;
    private final Screen screen;
    private final SloopView hardware;
    private final TextView toolState;
    private final ArrayList<Knob> knobs=new ArrayList<>();
    private final ArrayList<Fader> levels=new ArrayList<>();
    private final ArrayList<Button> mutes=new ArrayList<>();
    private final ArrayList<TextView> mixerNames=new ArrayList<>();
    private final Runnable settings;
    JSONObject state;
    final AndroidPanels panels=new AndroidPanels(this);
    private String page="PANEL", structure="";
    private boolean active, keysOpen, landscape, soundsOpen;
    private int panelPage=-1;
    private OctaveOverview octaveOverview;
    private boolean soundOverview=true;
    private Envelope envelope;
    private View drumEnvelope;
    private boolean drumEditor;
    private int drumKit=-1,drumLane;
    private TextView drumScope;
    private void soundActivity(){if(soundsOpen){removeCallbacks(hideSounds);postDelayed(hideSounds,6000);}}
    private boolean canSelectTrack(){if(state!=null&&state.optInt("recordState")==3){Toast.makeText(getContext(),"Finish or discard the take first",Toast.LENGTH_SHORT).show();return false;}return true;}
    private void selectTrack(int track){if(!canSelectTrack())return;if(track!=selected){soundsOpen=false;soundsTrack=-1;removeCallbacks(hideSounds);updateKeys();}releaseAll();Native.event(Native.SELECT,track,0);}
    private void revealSounds(int track){showSounds(track,true);}
    private void showSounds(int track,boolean toggle){if(!canSelectTrack())return;releaseAll();Native.event(Native.SELECT,track,0);soundsOpen=toggle?!(soundsOpen&&soundsTrack==track):true;soundsTrack=track;updateKeys();soundActivity();}
    private JSONObject panelGroup(){if(panelPage<-1&&state!=null){JSONArray g=state.optJSONArray("groups");for(int i=0;i<g.length();i++)if(g.optJSONObject(i).optInt("page")==panelPage)return g.optJSONObject(i);}return state.optJSONObject("current");}
    private void selectGroup(int id){cancelChordStamp();drumEditor=false;soundOverview=false;releaseAll();page="PANEL";updateKeys();panelPage=id<-1?id:-1;if(id>=0)Native.event(Native.PAGE,id,0);else Native.event(Native.EDITOR_HOME,0,0);if(state!=null){JSONArray groups=state.optJSONArray("groups");for(int i=0;i<groups.length();i++)if(groups.optJSONObject(i).optInt("page")==id){navigate(groups.optJSONObject(i));break;}}rebuild();}
    int selected; private int bank, octave=4;
    private int keyBank;
    private final Rect cameraInsets=new Rect();
    private Grid grid;
    private TextView patternLabel;
    private Button chordButton;
    private ChordPlacement chordStamp;
    private Dialog sheet;
    private final ArrayList<Dialog> sheets=new ArrayList<>();
    private final Map<Dialog,Runnable> sheetCleanups=new HashMap<>();
    private final Runnable hideSounds=new Runnable(){public void run(){if(!sheets.isEmpty()){postDelayed(this,1000);return;}soundsOpen=false;updateKeys();}};
    private final Runnable refresh=new Runnable(){public void run(){
        if(!active)return;
        screen.refresh();
        try {JSONObject next=new JSONObject(Native.editor());if(next.optInt("version")==1)accept(project(next));}
        catch(JSONException e){screen.setContentDescription(firmware.name+" display; waiting for engine");}
        postDelayed(this,33);
    }};

    StudioView(Context context,Runnable settings){
        super(context);this.settings=settings;setBackground(InstrumentStyle.surface(BG,0xff39423c,dp(12)));setPadding(dp(4),dp(4),dp(4),dp(4));
        compactDisplay=((MainActivity)context).getPreferences(0).getBoolean("compactDisplay",false);cameraClearance=((MainActivity)context).getPreferences(0).getBoolean("cameraClearance",true);largeZoom=Math.max(.6f,Math.min(1,((MainActivity)context).getPreferences(0).getFloat("largeZoom",1)));compactZoom=Math.max(.6f,Math.min(1.2f,((MainActivity)context).getPreferences(0).getFloat("compactZoom",1)));
        screen=new Screen(context);displayArea=row();displayArea.setGravity(Gravity.TOP);trackRail=new TrackRail(context);displayArea.addView(screen,new LinearLayout.LayoutParams(0,-1,1));displayArea.addView(trackRail,new LinearLayout.LayoutParams(dp(TRACK_COLUMN_DP),-1));console=column();console.setPadding(dp(6),0,dp(4),0);
        addView(displayArea,new LinearLayout.LayoutParams(dp(240),-1));addView(console,new LinearLayout.LayoutParams(0,-1,1));
        transport=row();play=button("PLAY",v->{if(state!=null)Native.event(Native.TRANSPORT,0,state.optInt("recordState")==3?0:state.optInt("playing")==0?1:0);});
        record=button("REC",v->{if(state!=null){if(state.optInt("recordState")==3)panels.action(42,0);else Native.event(Native.RECORD,selected,(state.optInt("recording")&(1<<selected))==0?1:0);}});
        tempo=button("BPM",v->editTempo());
        transport.addView(play,weight(44));transport.addView(record,weight(44));transport.addView(tempo,weight(44));
        transport.addView(button("MENU",v->showMenu()),weight(44));console.addView(transport);
        tracks=row();for(int i=0;i<trackCount;i++){final int t=i;LinearLayout track=row();trackCells[i]=track;track.setBackground(InstrumentStyle.surface(CARD,0xff3b4840,dp(7)));track.setClipToOutline(true);trackButtons[i]=button(definition.tracks[i],v->selectTrack(t));trackButtons[i].setContentDescription(definition.tracks[i]);trackButtons[i].setOnLongClickListener(v->{showSounds(t,false);return true;});trackButtons[i].setBackground(insetRipple());trackButtons[i].setStateListAnimator(null);trackButtons[i].setElevation(0);track.addView(trackButtons[i],new LinearLayout.LayoutParams(0,dp(44),1));Button arrow=button("▾",v->revealSounds(t));arrow.setOnLongClickListener(v->{showSounds(t,false);return true;});arrow.setContentDescription("Choose engine and instrument for "+(definition.tracks[i]));arrow.setBackground(insetRipple());arrow.setStateListAnimator(null);arrow.setElevation(0);track.addView(arrow,lp(26,44));tracks.addView(track,definition.trackButtonsEven||trackCount<=4?weight(44):buttonSize(102,44));}View trackScroll;if(definition.trackButtonsEven){trackScroll=tracks;console.addView(tracks,lpFill(48));}else {HorizontalScrollView scroll=new HorizontalScrollView(context);scroll.setFillViewport(true);scroll.setHorizontalScrollBarEnabled(false);scroll.addView(tracks);trackScroll=scroll;console.addView(scroll);}
        toolState=label("",12,GREEN);toolState.setOnClickListener(v->{releaseAll();Native.event(Native.PERFORM,71,0);});console.addView(toolState,lpFill(28));toolState.setVisibility(GONE);
        sounds=row();engine=button("ENGINE",v->chooseEngine());preset=button("PRESET",v->choosePreset());
        selectorRow(sounds,engine,preset,v->stepPreset(-1),v->stepPreset(1));console.addView(sounds);
        content=new FrameLayout(context);console.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        groupButton=button("SCREEN",v->chooseGroup());
        tabs=row();String[] names=new String[definition.tabs.length];for(int i=0;i<names.length;i++)names[i]=definition.tabs[i].title;
        for(int i=0;i<names.length;i++){final String name=definition.tabs[i].component;tabButtons[i]=button(names[i],v->openPage(name));tabButtons[i].setTag(name);tabs.addView(tabButtons[i],buttonSize(84,44));}
        HorizontalScrollView tabScroll=new HorizontalScrollView(context);tabScroll.setHorizontalScrollBarEnabled(false);tabScroll.setPadding(0,0,dp(2),0);tabScroll.addView(tabs);tabRow=row();tabRow.addView(tabScroll,new LinearLayout.LayoutParams(0,dp(48),1));console.addView(tabRow,lpFill(48));
        keyTools=row();keyTools.addView(button("OCT -",v->pulse(12)),weight(44));keyTools.addView(button("KEY BANK",v->{releaseAll();switchKeyBank((Button)v);}),weight(44));keyTools.addView(button("OCT 0",v->panels.action(46,0)),weight(44));keyTools.addView(button("OCT +",v->pulse(13)),weight(44));console.addView(keyTools);
        keyboard=new Keyboard(context);console.addView(keyboard,lpFill(72));hardware=new SloopView(context,true);
        console.removeAllViews();
        for(String part:definition.layout){View view=part.equals("transport")?transport:part.equals("tracks")?trackScroll:part.equals("status")?toolState:part.equals("sounds")?sounds:part.equals("workspace")?content:part.equals("tabs")?tabRow:part.equals("keyTools")?keyTools:keyboard;
            console.addView(view,part.equals("workspace")?new LinearLayout.LayoutParams(-1,0,1):view.getLayoutParams());}
        updateKeys();updateTrackLayout();
    }
    void switchKeyBank(Button b){keyBank=keyBank==0?8:0;b.setText(keyBank==0?"KEYS 1-8":"KEYS 9-16");keyboard.invalidate();}
    boolean isDrum(){return isDrum(selected);}
    boolean isDrum(int track){return state!=null&&state.optJSONArray("tracks")!=null&&state.optJSONArray("tracks").optJSONObject(track)!=null?state.optJSONArray("tracks").optJSONObject(track).optInt("drum")!=0:track==definition.fixedDrum;}
    int lanes(){return state==null?definition.drumLanes:state.optInt("laneCount",definition.drumLanes);}
    int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    LinearLayout row(){LinearLayout r=new LinearLayout(getContext());r.setOrientation(HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    LinearLayout column(){LinearLayout r=new LinearLayout(getContext());r.setOrientation(VERTICAL);return r;}
    LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(dp(w),dp(h));}
    LinearLayout.LayoutParams lpFill(int h){return new LinearLayout.LayoutParams(-1,dp(h));}
    LinearLayout.LayoutParams buttonSize(int w,int h){LinearLayout.LayoutParams p=lp(w,h);p.setMargins(dp(2),dp(2),dp(2),dp(2));return p;}
    LinearLayout.LayoutParams buttonFill(int h){LinearLayout.LayoutParams p=lpFill(h);p.setMargins(dp(2),dp(2),dp(2),dp(2));return p;}
    LinearLayout.LayoutParams weight(int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(h),1);p.setMargins(dp(2),dp(2),dp(2),dp(2));return p;}
    /** Sound and page selectors share button height, gutters and right-hand navigation. */
    void selectorRow(LinearLayout row,Button leading,Button selection,View.OnClickListener previous,View.OnClickListener next){
        if(leading!=null)row.addView(leading,weight(44));
        LinearLayout.LayoutParams value=weight(44);value.weight=1.7f;row.addView(selection,value);
        for(int i=0;i<2;i++){
            LinearLayout.LayoutParams arrow=weight(44);arrow.width=dp(44);arrow.weight=0;
            row.addView(button(i==0?"<":">",i==0?previous:next),arrow);
        }
    }
    TextView label(String s,int size,int color){TextView v=new TextView(getContext());v.setText(definition.label(s).toUpperCase(Locale.ROOT));v.setTextColor(color);v.setTextSize(size);InstrumentStyle.text(v);v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(dp(10),0,dp(4),0);return v;}
    Button button(String s,View.OnClickListener listener){Button b=new Button(getContext());b.setText(definition.label(s).toUpperCase(Locale.ROOT));b.setTextColor(TEXT);b.setTextSize(14);InstrumentStyle.text(b);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setMaxLines(1);b.setEllipsize(TextUtils.TruncateAt.END);b.setPadding(dp(6),0,dp(6),0);
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x3349df99),InstrumentStyle.surface(CARD,0xff3b4840,dp(7)),null));b.setOnClickListener(listener);return b;}
    RippleDrawable insetRipple(){return new RippleDrawable(android.content.res.ColorStateList.valueOf(0x3349df99),null,new ColorDrawable(Color.WHITE));}
    GradientDrawable tint(int color){return InstrumentStyle.surface(color,0xff3b4840,dp(10));}
    void setStatus(String value){screen.setContentDescription("Original "+firmware.name+" display. Pinch to resize; double-tap to reset to 100%. "+value);}
    void resume(){active=true;removeCallbacks(refresh);post(refresh);if(page.equals("MORE"))hardware.resume();}
    void pause(){saveDisplayZoom();cancelChordStamp();active=false;removeCallbacks(refresh);hardware.pause();releaseAll();closeSheets();}
    void releaseAll(){keyboard.releaseAll();hardware.releaseAll();for(Knob knob:knobs)knob.cancelGesture();if(grid!=null)grid.removeCallbacks(grid.hold);for(Fader fader:levels)fader.setPressed(false);panels.releasePads();}
    void pulse(int button){releaseAll();Native.event(0,button,1);Native.event(0,button,0);}
    void updateKeys(){keyboard.setVisibility(keysOpen?VISIBLE:GONE);keyTools.setVisibility(keysOpen?VISIBLE:GONE);boolean show=soundsOpen&&!keysOpen;if(show&&sounds.getVisibility()!=VISIBLE){sounds.setAlpha(0);sounds.setVisibility(VISIBLE);sounds.animate().alpha(1).setDuration(160).start();}else if(!show){sounds.animate().cancel();sounds.setVisibility(GONE);}}
    private float edgeStartX,edgeStartY;
    private boolean edgeCandidate,edgeDragging;
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            edgeStartX=event.getX();edgeStartY=event.getY();edgeDragging=false;
            float zone=keysOpen?keyboard.getHeight()+keyTools.getHeight():dp(40);
            Rect tabBounds=new Rect();tabRow.getDrawingRect(tabBounds);offsetDescendantRectToMyCoords(tabRow,tabBounds);
            edgeCandidate=tabBounds.contains((int)edgeStartX,(int)edgeStartY)||
                          (edgeStartY>=getHeight()-zone&&edgeStartX>=console.getLeft());
        }
        if(action==MotionEvent.ACTION_POINTER_DOWN)edgeCandidate=false;
        float dx=event.getX()-edgeStartX,dy=event.getY()-edgeStartY;
        if(edgeCandidate&&!edgeDragging&&action==MotionEvent.ACTION_MOVE&&Math.abs(dy)>dp(16)&&Math.abs(dy)>Math.abs(dx)*1.5f&&(keysOpen?dy>0:dy<0)){
            edgeDragging=true;MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();releaseAll();
        }
        if(edgeDragging){
            if(action==MotionEvent.ACTION_UP){if(Math.abs(dy)>=dp(40))setKeys(!keysOpen);edgeDragging=false;edgeCandidate=false;}
            else if(action==MotionEvent.ACTION_CANCEL){edgeDragging=false;edgeCandidate=false;}
            return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)edgeCandidate=false;
        return super.dispatchTouchEvent(event);
    }
    private void setKeys(boolean open){releaseAll();keysOpen=open;updateKeys();rebuild();requestLayout();}

    void setCompactDisplay(boolean compact){releaseAll();closeSheets();compactDisplay=compact;((MainActivity)getContext()).getPreferences(0).edit().putBoolean("compactDisplay",compact).apply();updateTrackLayout();requestLayout();screen.invalidate();}
    float displayZoom(){return compactDisplay?compactZoom:largeZoom;}
    void setDisplayZoom(float zoom,boolean save){zoom=Math.max(.6f,Math.min(maximumZoom,zoom));if(compactDisplay)compactZoom=zoom;else largeZoom=zoom;displayArea.requestLayout();screen.invalidate();if(save)saveDisplayZoom();}
    void saveDisplayZoom(){((MainActivity)getContext()).getPreferences(0).edit().putFloat("largeZoom",largeZoom).putFloat("compactZoom",compactZoom).apply();}
    void setCameraClearance(boolean clear){cameraClearance=clear;((MainActivity)getContext()).getPreferences(0).edit().putBoolean("cameraClearance",clear).apply();requestLayout();screen.invalidate();}
    private void updateTrackLayout(){
        for(LinearLayout cell:trackCells){if(cell.getParent()!=null)((android.view.ViewGroup)cell.getParent()).removeView(cell);if(compactDisplay){trackRail.addView(cell,new FrameLayout.LayoutParams(-1,dp(44)));}else {for(int j=0;j<cell.getChildCount();j++)cell.getChildAt(j).getLayoutParams().height=dp(44);tracks.addView(cell,definition.trackButtonsEven||trackCount<=4?weight(44):buttonSize(102,44));}}
        trackRail.setVisibility(compactDisplay?VISIBLE:GONE);tracks.setVisibility(compactDisplay?GONE:VISIBLE);
    }
    void windowInsets(WindowInsets insets){
        cameraInsets.set(0,0,0,0);
        if(Build.VERSION.SDK_INT>=28){DisplayCutout cutout=insets.getDisplayCutout();if(cutout!=null)cameraInsets.set(cutout.getSafeInsetLeft(),cutout.getSafeInsetTop(),cutout.getSafeInsetRight(),cutout.getSafeInsetBottom());}
        requestLayout();screen.invalidate();
    }
    private Rect safeArea(View view){int[] position=new int[2];view.getLocationInWindow(position);int width=getRootView().getWidth(),height=getRootView().getHeight();return new Rect(Math.max(0,cameraInsets.left-position[0]),Math.max(0,cameraInsets.top-position[1]),Math.min(view.getWidth(),width-cameraInsets.right-position[0]),Math.min(view.getHeight(),height-cameraInsets.bottom-position[1]));}
    private void padControls(View view,int gutterLeft,int gutterRight){Rect safe=safeArea(view);int left=gutterLeft+Math.max(0,safe.left),top=Math.max(0,safe.top),right=gutterRight+Math.max(0,view.getWidth()-safe.right),bottom=Math.max(0,view.getHeight()-safe.bottom);if(view.getPaddingLeft()!=left||view.getPaddingTop()!=top||view.getPaddingRight()!=right||view.getPaddingBottom()!=bottom)view.setPadding(left,top,right,bottom);}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);padControls(console,dp(6),dp(4));if(compactDisplay)trackRail.alignTracks();}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int w=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight(),h=MeasureSpec.getSize(heightSpec)-getPaddingTop()-getPaddingBottom();
        boolean wide=w>h*1.2f;int size,rail=0;
        int renderInset=wide&&cameraClearance?Math.max(0,cameraInsets.left-getPaddingLeft()):0;
        if(compactDisplay){
            int area=wide?Math.max(dp(200),Math.min(w-dp(240),h+renderInset+dp(TRACK_COLUMN_DP))):w;
            int base=Math.max(dp(160),area-dp(TRACK_COLUMN_DP));maximumZoom=Math.max(1,Math.min(1.2f,(area-dp(64))/(float)base));
            int displaySize=Math.max(dp(160),Math.round(base*Math.min(displayZoom(),maximumZoom)));rail=Math.max(dp(64),area-displaySize);
            size=wide?area:Math.max(displaySize,dp(224)+cameraInsets.top);
        }else{
            maximumZoom=1;int base=wide?Math.min(h+renderInset,Math.max(w/2,w-dp(350))):Math.min(w,Math.max(dp(100),h-dp(keysOpen?474.4f:360)));
            size=Math.max(dp(160),Math.round(base*displayZoom()));
        }
        ((LinearLayout.LayoutParams)keyboard.getLayoutParams()).height=dp(wide?72:86.4f);
        int orientation=wide?HORIZONTAL:VERTICAL;if(getOrientation()!=orientation)setOrientation(orientation);
        LinearLayout.LayoutParams display=(LinearLayout.LayoutParams)displayArea.getLayoutParams();display.width=wide?size:-1;display.height=wide?-1:size;display.weight=0;
        ((LinearLayout.LayoutParams)trackRail.getLayoutParams()).width=rail;
        for(int i=0;i<trackCount;i++){boolean narrow=compactDisplay?rail<dp(104):definition.trackButtonsEven&&w/(float)trackCount<dp(90);String title=narrow?Integer.toString(i+1):definition.tracks[i];if(!title.contentEquals(trackButtons[i].getText()))trackButtons[i].setText(title);trackButtons[i].setTextSize(compactDisplay?12:14);trackButtons[i].setPadding(dp(compactDisplay?3:6),0,dp(compactDisplay?3:6),0);}
        LinearLayout.LayoutParams controls=(LinearLayout.LayoutParams)console.getLayoutParams();controls.width=wide?0:-1;controls.height=wide?-1:0;controls.weight=1;
        landscape=wide;super.onMeasure(widthSpec,heightSpec);
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);releaseAll();closeSheets();}
    void openPage(String name){
        if(!name.equals("STEPS"))cancelChordStamp();
        if(!name.equals("PANEL"))panelPage=-1;
        releaseAll();if(page.equals("MORE")&&!name.equals("MORE")&&state!=null){int current=state.optJSONObject("current").optInt("page");Native.event(current>=0?Native.PAGE:Native.EDITOR_HOME,Math.max(0,current),0);}
        page=name;updateKeys();
        if(name.equals("SONG"))Native.event(Native.ACTION,51,0);
        if(state!=null&&(name.equals("STEPS")||name.equals("MIX"))){
            JSONArray groups=state.optJSONArray("groups");
            if(name.equals("MIX"))Native.event(Native.EDITOR_HOME,0,0);
            else for(int i=0;i<groups.length();i++)if(groups.optJSONObject(i).optString("title").equals("PATTERN"))Native.event(Native.PAGE,groups.optJSONObject(i).optInt("page"),0);
        }
        rebuild();
    }
    private void legacyMenu(){
        String[] names={"HOME / TRACK CONTROLS","MIDI / FILES / SETTINGS","SHOW / HIDE KEYS","ENGINE / KIT","SOUND / PRESET",firmware.name+" PAGES"};
        picker("SLOOP",names,-1,false,n->{if(n==0){Native.event(Native.EDITOR_HOME,0,0);openPage("PANEL");}else if(n==1)settings.run();else if(n==2)setKeys(!keysOpen);else if(n==3)chooseEngine();else if(n==4)choosePreset();else chooseGroup();});
    }
    private void showMenu(){panels.menu();}
    void settings(){settings.run();}
    private void chooseEngine(){soundActivity();if(state==null)return;if(isDrum()&&(definition.fixedDrum>=0||definition.has("clips"))){chooseKit();return;}choose("ENGINE",state.optJSONArray("engines"),state.optInt("engine"),Native.ENGINE);}
    void choosePreset(){soundActivity();if(state==null)return;if(isDrum()&&(definition.fixedDrum>=0||definition.has("clips"))){chooseKit();return;}choose("SOUND / TAP TO LOAD",state.optJSONArray("presets"),state.optInt("preset"),Native.PRESET);}
    private void choose(String name,JSONArray array,int current,int command){if(array==null)return;final int target=selected;ArrayList<String> labels=new ArrayList<>();ArrayList<Integer> ids=new ArrayList<>();int position=-1;for(int i=0;i<array.length();i++){if(array.optString(i).isEmpty())continue;if(i==current)position=labels.size();labels.add(array.optString(i));ids.add(i);}picker(name,labels.toArray(new String[0]),position,labels.size()>12,n->{Native.event(command,target,ids.get(n));soundActivity();});}
    private String[] strings(JSONArray a){String[] result=new String[a.length()];for(int i=0;i<result.length;i++)result[i]=a.optString(i);return result;}
    private void stepPreset(int delta){soundActivity();if(state==null)return;releaseAll();if(isDrum()&&(definition.fixedDrum>=0||definition.has("clips"))){chooseKit();return;}int value=Math.max(0,Math.min(state.optJSONArray("presets").length()-1,state.optInt("preset")+delta));Native.event(Native.PRESET,selected,value);}
    private void chooseKit(){if(state==null)return;JSONArray groups=state.optJSONArray("groups");for(int i=0;i<groups.length();i++)if(groups.optJSONObject(i).optString("title").equals("KIT")){JSONObject p=groups.optJSONObject(i).optJSONArray("params").optJSONObject(0);new Knob(p).openEditor();return;}}
    void chooseGroup(){
        if(state==null)return;JSONArray groups=state.optJSONArray("groups");String[] names=new String[groups.length()+1];names[0]="HOME / TRACK CONTROLS";int current=0;
        for(int i=0;i<groups.length();i++){JSONObject g=groups.optJSONObject(i);names[i+1]=g.optString("title");if(g.optInt("page")==panelGroup().optInt("page"))current=i+1;}
        picker(firmware.name+" PAGES",names,current,false,n->{selectGroup(n==0?-1:groups.optJSONObject(n-1).optInt("page"));});
    }
    private void walkGroup(int delta){
        if(state==null)return;JSONArray groups=state.optJSONArray("groups");int current=panelGroup().optInt("page"),index=-1;
        for(int i=0;i<groups.length();i++)if(groups.optJSONObject(i).optInt("page")==current){index=i;break;}
        int next=Math.max(-1,Math.min(groups.length()-1,index+delta));selectGroup(next<0?-1:groups.optJSONObject(next).optInt("page"));
    }
    private void editTempo(){if(state==null)return;numberEditor("TEMPO / BPM",state.optInt("bpm"),40,240,n->Native.event(Native.GLOBAL,0,n));}

    /** Styled sheets keep the framebuffer uncovered in landscape. Scrolling never sends a command. */
    Dialog showSheet(String title,LinearLayout body){
        title=definition.label(title);releaseAll();final Dialog previous=sheet;if(previous!=null)previous.hide();Dialog dialog=new Dialog(getContext());dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);Fullscreen.configure(dialog.getWindow());
        LinearLayout panel=column();panel.setPadding(dp(10),dp(10),dp(10),dp(10));panel.setBackground(tint(CARD));LinearLayout heading=row();heading.addView(label(title,16,GREEN),new LinearLayout.LayoutParams(0,dp(44),1));heading.addView(button("X",v->dialog.dismiss()),buttonSize(44,44));panel.addView(heading);panel.addView(body,new LinearLayout.LayoutParams(-1,0,1));dialog.setContentView(panel);dialog.show();Fullscreen.hide(dialog.getWindow());
        Window window=dialog.getWindow();window.setBackgroundDrawableResource(android.R.color.transparent);window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setLayout(Math.max(dp(280),console.getWidth()-console.getPaddingLeft()-console.getPaddingRight()-dp(8)),Math.min(console.getHeight()-console.getPaddingTop()-console.getPaddingBottom()-dp(12),Math.max(dp(280),console.getHeight()-dp(12))));
        int[] location=new int[2];console.getLocationOnScreen(location);WindowManager.LayoutParams wp=window.getAttributes();wp.gravity=Gravity.TOP|Gravity.LEFT;wp.x=location[0]+console.getPaddingLeft();wp.y=location[1]+console.getPaddingTop();window.setAttributes(wp);
        sheets.add(dialog);dialog.setOnDismissListener(d->{for(java.util.Iterator<Knob> it=knobs.iterator();it.hasNext();){Knob knob=it.next();if(inside(knob,panel)){knob.cancelGesture();it.remove();}}sheets.remove(dialog);Runnable cleanup=sheetCleanups.remove(dialog);if(cleanup!=null)cleanup.run();if(sheet==dialog){sheet=previous;if(active&&previous!=null)previous.show();}});sheet=dialog;return dialog;
    }
    void sheetCleanup(Dialog dialog,Runnable cleanup){sheetCleanups.put(dialog,cleanup);}
    void closeSheets(){boolean was=active;active=false;for(Dialog d:new ArrayList<>(sheets))d.dismiss();sheets.clear();sheet=null;active=was;}
    void picker(String title,String[] values,int current,boolean search,IntConsumer choose){
        LinearLayout body=column();EditText query=new EditText(getContext());InstrumentStyle.text(query);query.setTextSize(16);query.setSingleLine(true);query.setTextColor(TEXT);query.setHintTextColor(DIM);query.setHint("SEARCH");query.setBackground(tint(BG));if(search)body.addView(query,lpFill(44));
        ScrollView scroll=new ScrollView(getContext());LinearLayout entries=column();scroll.addView(entries);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));Dialog dialog=showSheet(title,body);
        Runnable populate=()->{entries.removeAllViews();String filter=query.getText().toString().toUpperCase(Locale.ROOT);for(int i=0;i<values.length;i++){if(!values[i].toUpperCase(Locale.ROOT).contains(filter))continue;final int index=i;Button b=button((i==current?"> ":"  ")+values[i],v->{dialog.dismiss();choose.accept(index);});b.setTextColor(i==current?GREEN:TEXT);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);entries.addView(b,buttonFill(48));}if(entries.getChildCount()==0)entries.addView(label("NO MATCHES",14,DIM),lpFill(48));};
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){populate.run();}public void afterTextChanged(Editable e){}});populate.run();
        body.setFocusableInTouchMode(true);body.requestFocus();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    void numberEditor(String name,int initial,int min,int max,IntConsumer send){
        LinearLayout body=column();TextView range=label(min+" ... "+max,14,DIM);body.addView(range,lpFill(32));
        EditText input=new EditText(getContext());InstrumentStyle.text(input);input.setTextColor(GREEN);input.setTextSize(28);input.setGravity(Gravity.CENTER);input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);input.setText(Integer.toString(initial));input.setBackground(tint(BG));horizontalNumberDrag(input,initial,min,max);body.addView(input,lpFill(64));
        TextView error=label("",12,0xffff8878);body.addView(error,lpFill(28));LinearLayout fine=row();Dialog dialog=showSheet(name,body);
        for(int delta:new int[]{-10,-1,1,10})fine.addView(button(delta>0?"+"+delta:""+delta,v->{try{int n=Integer.parseInt(input.getText().toString());input.setText(Integer.toString(Math.max(min,Math.min(max,n+delta))));}catch(NumberFormatException e){input.setText(Integer.toString(initial));}}),weight(44));body.addView(fine);
        body.addView(button("APPLY",v->{try{int n=Integer.parseInt(input.getText().toString());if(n<min||n>max)throw new NumberFormatException();dialog.dismiss();send.accept(n);}catch(NumberFormatException e){error.setText("ENTER "+min+" TO "+max);}}),buttonFill(48));
        body.setFocusableInTouchMode(true);body.requestFocus();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    /** Keep typed entry and Apply; horizontal drags edit the same pending value. */
    private void horizontalNumberDrag(EditText input,int initial,int min,int max){
        input.setOnTouchListener(new View.OnTouchListener(){
            float startX,startY;int startValue;boolean dragging;
            public boolean onTouch(View view,MotionEvent event){
                switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    startX=event.getX();startY=event.getY();dragging=false;
                    try{startValue=Integer.parseInt(input.getText().toString());}catch(NumberFormatException e){startValue=initial;}
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float dx=event.getX()-startX,dy=event.getY()-startY;
                    if(!dragging&&Math.abs(dx)>ViewConfiguration.get(getContext()).getScaledTouchSlop()&&Math.abs(dx)>Math.abs(dy)){
                        dragging=true;MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);input.onTouchEvent(cancel);cancel.recycle();input.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if(dragging){float rate=Math.max(1f/dp(6),(max-(float)min)/dp(360));int n=(int)Math.max(min,Math.min(max,startValue+(double)Math.round(dx*rate)));input.setText(Integer.toString(n));input.setSelection(input.length());return true;}
                    return false;
                case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                    if(dragging){dragging=false;input.getParent().requestDisallowInterceptTouchEvent(false);return true;}return false;
                }return false;
            }
        });
        input.setContentDescription("Numeric value. Drag left or right to adjust, or tap to type.");
    }
    /** Parameter pages and their control order are source declarations over native metadata. */
    private JSONObject project(JSONObject next)throws JSONException {
        JSONArray nativeGroups=next.optJSONArray("groups"),groups=new JSONArray();boolean[] used=new boolean[nativeGroups.length()];
        for(Definitions.ParameterPage page:definition.pages)for(int i=0;i<nativeGroups.length();i++){
            JSONObject group=nativeGroups.optJSONObject(i);if(used[i]||(!page.nativeTitle.equals("*")&&!page.nativeTitle.equals(group.optString("title"))))continue;
            JSONObject copy=new JSONObject();copy.put("title",page.title.isEmpty()?group.optString("title"):page.title);copy.put("nativeTitle",group.optString("title"));copy.put("page",group.optInt("page"));
            JSONArray fields=group.optJSONArray("params"),params=new JSONArray();if(page.parameters.length==0)params=fields;else for(String label:page.parameters)for(int k=0;k<fields.length();k++)if(fields.optJSONObject(k).optString("label").equals(label))params.put(fields.optJSONObject(k));
            copy.put("params",params);if(params.length()>0){groups.put(copy);used[i]=true;}
        }
        next.put("groups",groups);int current=next.optJSONObject("current").optInt("page");for(int i=0;i<groups.length();i++)if(groups.optJSONObject(i).optInt("page")==current&&current>=0)next.put("current",groups.optJSONObject(i));return next;
    }
    private void navigate(JSONObject group){String title=group.optString("nativeTitle",group.optString("title"));for(Definitions.ParameterPage page:definition.pages)if(page.nativeTitle.equals(title)){for(int[] event:page.enter)Native.emit(event[0],event[1],event[2]);return;}}
    private void accept(JSONObject next){
        int old=selected;state=next;selected=state.optInt("selected");if(old!=selected){releaseAll();cancelChordStamp();drumEditor=false;drumKit=-1;bank=0;panelPage=-1;if(soundsOpen&&soundsTrack!=selected){soundsOpen=false;soundsTrack=-1;removeCallbacks(hideSounds);updateKeys();}}
        if(drumEditor&&!canEditDrumSounds()){drumEditor=false;drumKit=-1;}
        int layer=state.optInt("layer");toolState.setVisibility(layer==2||layer==3?VISIBLE:GONE);toolState.setText(layer==2?"ERASE ON · TAP TO SWITCH OFF":layer==3?"REPEAT ON · TAP TO SWITCH OFF":"");
        play.setText(state.optInt("playing")==0?"PLAY":"STOP");play.setTextColor(state.optInt("playing")==0?TEXT:GREEN);
        record.setText(new String[]{"REC","ARMED","COUNT","FINISH","REC ON"}[Math.max(0,Math.min(4,state.optInt("recordState")))]);
        record.setTextColor((state.optInt("recording")&(1<<selected))==0?DIM:0xffff776b);tempo.setText(state.optInt("bpm")+" BPM");
        for(int i=0;i<trackCount;i++){trackButtons[i].setTextColor(i==selected?GREEN:DIM);trackButtons[i].setSelected(i==selected);trackButtons[i].setContentDescription((definition.tracks[i])+(i==selected?", selected":""));}
        engine.setText(isDrum()&&(definition.fixedDrum>=0||definition.has("clips"))?"DRUM KIT":state.optJSONArray("engines").optString(state.optInt("engine")).toUpperCase(Locale.ROOT));
        preset.setText(isDrum()&&(definition.fixedDrum>=0||definition.has("clips"))?"CHOOSE KIT":state.optJSONArray("presets").optString(state.optInt("preset")).toUpperCase(Locale.ROOT));
        int user=state.optInt("userSlot",-1);if(!isDrum()&&user>=0&&user<32&&state.optJSONArray("userSounds").optJSONObject(user).optInt("used")!=0)preset.setText(state.optJSONArray("userSounds").optJSONObject(user).optString("name"));
        groupButton.setText(panelGroup().optString("title")+"  /  PAGES");
        Native.selected=selected;Native.drumTrack=-1;Native.drumChannel=-1;for(int i=0;i<trackCount;i++)if(isDrum(i)){Native.drumTrack=i;Native.drumChannel=state.optJSONArray("tracks").optJSONObject(i).optInt("channel",-1);break;}
        if(!signature().equals(structure)){rebuild();return;}
        Map<Integer,JSONObject> values=new HashMap<>();JSONArray groups=state.optJSONArray("groups");for(int i=0;i<groups.length();i++)collect(groups.optJSONObject(i),values);collect(state.optJSONObject("current"),values);
        for(Knob knob:knobs){JSONObject p=knob.dataSource!=null?knob.dataSource.get():values.get(knob.id);if(p!=null)knob.update(p);}
        if(envelope!=null)envelope.invalidate();if(drumEnvelope!=null)drumEnvelope.invalidate();updateDrumScope();
        for(int i=0;i<levels.size();i++){JSONObject t=state.optJSONArray("tracks").optJSONObject(i);if(!levels.get(i).isPressed())levels.get(i).update(t.optInt("level"));mutes.get(i).setText(t.optInt("mute")==0?"MUTE":"MUTED");mutes.get(i).setTextColor(t.optInt("mute")==0?DIM:GREEN);mixerNames.get(i).setText((definition.tracks[i])+"\n"+(state.optInt("solo")!=0&&(state.optInt("solo")&(1<<i))==0?"SOLO EXCLUDED":Math.round(t.optInt("level")*100f/127)+"%"));}
        if(grid!=null){bank=Math.min(bank,Math.max(0,(state.optInt("length")-1)/16));updateChordToolbar();grid.invalidate();if(octaveOverview!=null)octaveOverview.invalidate();}
        panels.refresh();keyboard.invalidate();
    }
    private void collect(JSONObject g,Map<Integer,JSONObject> values){JSONArray p=g.optJSONArray("params");for(int k=0;k<p.length();k++)values.put(p.optJSONObject(k).optInt("id"),p.optJSONObject(k));}
    private String signature(){return page+":"+selected+":"+(state==null?0:state.optInt("engine"))+":"+isDrum()+":"+(state==null?0:state.optInt("recordState"))+":"+(state!=null&&page.equals("PANEL")?panelGroup().optInt("page"):0)+":"+(state!=null&&page.equals("PERFORM")?state.optInt("layer"):0)+":"+(state!=null&&page.equals("MIX")?state.optInt("solo")+":"+state.optInt("drumMute"):"")+":"+(state!=null&&page.equals("SONG")?state.optInt("songMode")+":"+state.optInt("songRecording")+":"+state.optInt("liveSection")+":"+state.optInt("queuedSection")+":"+state.optInt("songLoop")+":"+state.optJSONArray("arrangement").toString()+":"+state.optJSONArray("songLanes"):"");}
    void refreshWorkspace(){rebuild();}
    private void rebuild(){
        for(Knob knob:knobs)knob.cancelGesture();panels.releasePads();hardware.pause();if(grid!=null)grid.removeCallbacks(grid.hold);content.removeAllViews();knobs.clear();levels.clear();mutes.clear();mixerNames.clear();grid=null;patternLabel=null;chordButton=null;octaveOverview=null;envelope=null;drumEnvelope=null;drumScope=null;structure=signature();for(Button b:tabButtons)b.setTextColor(page.equals(b.getTag())?GREEN:DIM);
        if(page.equals("MORE")){ScrollView scroll=new ScrollView(getContext());if(hardware.getParent()!=null)((android.view.ViewGroup)hardware.getParent()).removeView(hardware);scroll.addView(hardware, new ScrollView.LayoutParams(-1,dp(210)));content.addView(scroll);if(active)hardware.resume();return;}
        if(state==null)return;if(page.equals("STEPS")){buildSequencer();return;}
        if(page.equals("PANEL")&&drumEditor&&canEditDrumSounds()){buildDrumEditor();return;}
        ScrollView scroll=new ScrollView(getContext());scroll.setFillViewport(true);LinearLayout body=column();body.setPadding(0,dp(4),0,dp(4));scroll.addView(body);content.addView(scroll);
        if(page.equals("MIX")){buildMixer(body);panels.drumMutes(body);return;}
        if(page.equals("PERFORM")){content.removeAllViews();LinearLayout root=column();content.addView(root);panels.perform(root);return;}
        if(page.equals("SONG")){panels.song(body);return;}
        if(state.optInt("recordState")>0&&state.optInt("recordState")<4){panels.recording(body);return;}
        soundModes(body);
        if(soundOverview){buildSoundEditor(body);return;}
        LinearLayout navigation=row();if(groupButton.getParent()!=null)((android.view.ViewGroup)groupButton.getParent()).removeView(groupButton);selectorRow(navigation,null,groupButton,v->walkGroup(-1),v->walkGroup(1));if(!keysOpen){body.setPadding(0,0,0,dp(4));body.addView(navigation);}
        LinearLayout card=row();card.setGravity(Gravity.TOP);card.setBackground(tint(CARD));JSONArray params=panelGroup().optJSONArray("params");for(int k=0;k<params.length();k++){Knob knob=new Knob(params.optJSONObject(k));knobs.add(knob);card.addView(knob,weight(keysOpen?116:130));}LinearLayout.LayoutParams cardSpace=new LinearLayout.LayoutParams(-1,-2);cardSpace.setMargins(dp(2),dp(4),dp(2),dp(2));body.addView(card,cardSpace);
        if(params.length()==0)body.addView(label("USE MORE FOR THIS SCREEN'S CONTROLS",14,DIM),lpFill(56));
    }
    private boolean canEditDrumSounds(){return isDrum()&&definition.has("drum_synth")&&state.optJSONArray("synthKits")!=null&&state.optJSONArray("synthKits").length()>0;}
    private void soundModes(LinearLayout body){
        LinearLayout mode=row();Button overview=button("SOUND",v->{drumEditor=false;soundOverview=true;rebuild();});overview.setTextColor(!drumEditor&&soundOverview?GREEN:DIM);mode.addView(overview,weight(44));
        if(canEditDrumSounds()){Button drums=button("DRUM SOUNDS",v->{drumEditor=true;drumKit=panels.activeSynthKit();rebuild();});drums.setTextColor(drumEditor?GREEN:DIM);mode.addView(drums,weight(44));}
        Button pages=button("ALL PAGES",v->{drumEditor=false;soundOverview=false;rebuild();});pages.setTextColor(!drumEditor&&!soundOverview?GREEN:DIM);mode.addView(pages,weight(44));body.addView(mode);
    }
    private void buildDrumEditor(){
        LinearLayout root=column();content.addView(root);soundModes(root);
        Button kit=button(drumKit<0?"CHOOSE EDITABLE KIT":"EDIT SYN"+(drumKit+1)+" · "+state.optJSONArray("synthKits").optJSONObject(drumKit).optString("name"),v->{JSONArray kits=state.optJSONArray("synthKits");String[] names=new String[kits.length()];for(int i=0;i<names.length;i++)names[i]="SYN"+(i+1)+" · "+kits.optJSONObject(i).optString("name");picker("EDITABLE DRUM KIT",names,drumKit,false,n->{drumKit=n;rebuild();});});kit.setMaxLines(2);kit.setTextSize(12);LinearLayout context=row();context.addView(kit,new LinearLayout.LayoutParams(0,dp(56),1));Button hear=button("▶",v->Native.event(Native.ADVANCED,9100+drumKit,drumLane));hear.setEnabled(drumKit>=0);hear.setContentDescription(drumKit<0?"Choose a kit before auditioning":"Audition "+DrumSound.LANES[drumLane]+" from SYN"+(drumKit+1));context.addView(hear,buttonSize(48,44));root.addView(context);if(drumKit>=0){drumScope=kit;updateDrumScope();}
        if(drumKit<0){TextView help=label("Choose a SYN slot to edit. Factory sounds can be copied into that slot using COPY FACTORY KIT. Selecting an editing slot does not change the playing kit.",14,DIM);help.setSingleLine(false);root.addView(help,new LinearLayout.LayoutParams(-1,-2));return;}
        HorizontalScrollView lanes=new HorizontalScrollView(getContext());lanes.setHorizontalScrollBarEnabled(false);LinearLayout rail=row();
        for(int i=0;i<DrumSound.LANES.length;i++){final int lane=i;Button b=button(DrumSound.LANES[i],v->{drumLane=lane;rebuild();});b.setTextColor(i==drumLane?GREEN:DIM);b.setSelected(i==drumLane);rail.addView(b,buttonSize(i==4?100:80,44));}lanes.addView(rail);root.addView(lanes,lpFill(48));lanes.post(()->lanes.scrollTo(Math.max(0,rail.getChildAt(drumLane).getLeft()-lanes.getWidth()/2+dp(40)),0));
        ScrollView scroll=new ScrollView(getContext());LinearLayout body=column();scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView hint=label("Drag the control to tune. Swipe labels or gaps to scroll. Hold the control for 750 ms to scroll; double-tap for exact entry.",12,DIM);body.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        drumEnvelope=new DrumEnvelope();body.addView(drumEnvelope,lpFill(130));panels.drumSoundControls(body,drumKit,drumLane);
        LinearLayout actions=column();actions.setBackground(tint(CARD));actions.addView(label("WHOLE KIT",14,GREEN),lpFill(32));
        actions.addView(button("COPY FACTORY KIT",v->panels.confirm("COPY INTO SYN"+(drumKit+1),"Replace every drum sound in this editing slot?",()->panels.choose("FACTORY KIT",panels.strings(state.optJSONArray("synthFactory")),-1,n->Native.event(Native.ADVANCED,9000+drumKit,n)))),buttonFill(44));
        actions.addView(button("NAME KIT",v->panels.text("KIT NAME",8,name->Native.data(11,drumKit,name.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))),buttonFill(44));
        actions.addView(button("USE THIS KIT ON DRUM TRACK",v->panels.action(50,drumKit)),buttonFill(44));
        actions.addView(button("STORE ALL SYN KITS",v->Native.event(Native.ADVANCED,9200,0)),buttonFill(44));
        panels.numericControl(actions,"BIT CRUSH",0,15,()->state.optJSONArray("synthKits").optJSONObject(drumKit).optInt("crush")&15,n->Native.event(Native.ADVANCED,9400+drumKit,(state.optJSONArray("synthKits").optJSONObject(drumKit).optInt("crush")&240)|n));
        panels.numericControl(actions,"SAMPLE / HOLD",1,16,()->(state.optJSONArray("synthKits").optJSONObject(drumKit).optInt("crush")>>4)+1,n->Native.event(Native.ADVANCED,9400+drumKit,(state.optJSONArray("synthKits").optJSONObject(drumKit).optInt("crush")&15)|((n-1)<<4)));body.addView(actions);
    }
    private void updateDrumScope(){if(drumScope!=null&&drumKit>=0)drumScope.setText("EDITING SYN"+(drumKit+1)+" · "+state.optJSONArray("synthKits").optJSONObject(drumKit).optString("name")+" · "+DrumSound.LANES[drumLane]+"\nPLAYING: "+panels.playingKitName());}
    final class DrumEnvelope extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        DrumEnvelope(){super(StudioView.this.getContext());setContentDescription("Normalized tone and noise hold/decay envelope sketch for "+DrumSound.LANES[drumLane]);}
        float field(int field){int id=DrumSound.parameterId(drumKit,drumLane,field);for(Knob knob:knobs)if(knob.id==id)return knob.value/(float)DrumSound.MAX[field];return 0;}
        @Override protected void onDraw(Canvas c){
            paint.setTypeface(InstrumentStyle.font(getContext()));paint.setTextSize(dp(11));paint.setColor(DIM);c.drawText("TONE / NOISE · NORMALIZED ENVELOPES",dp(12),dp(18),paint);
            drawEnvelope(c,dp(67),6,7,8,GREEN,"TONE");drawEnvelope(c,dp(114),13,14,12,0xff65bdea,"NOISE");
        }
        void drawEnvelope(Canvas c,float base,int hold,int decay,int level,int color,String name){float left=dp(62),space=Math.max(1,getWidth()-left-dp(14)),endHold=left+space*(.05f+.3f*field(hold)),end=endHold+space*(.05f+.55f*field(decay)),peak=base-dp(32)*field(level);paint.setColor(DIM);paint.setStrokeWidth(dp(1));c.drawLine(left,base,getWidth()-dp(12),base,paint);paint.setColor(color);c.drawText(name,dp(10),base-dp(12),paint);Path path=new Path();path.moveTo(left,base);path.lineTo(left,peak);path.lineTo(endHold,peak);path.quadTo(endHold+(end-endHold)*.35f,base,end,base);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);}
    }
    private void buildSoundEditor(LinearLayout body){
        TextView hint=label("Drag the control to tune. Swipe labels or gaps to scroll. Hold the control for 750 ms to scroll; double-tap for exact entry.",12,DIM);body.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        JSONArray groups=state.optJSONArray("groups");HashSet<Integer> shown=new HashSet<>();
        for(String title:definition.soundPages)for(int i=0;i<groups.length();i++){
            JSONObject group=groups.optJSONObject(i);if(!title.equals(group.optString("nativeTitle",group.optString("title"))))continue;
            JSONArray params=group.optJSONArray("params");ArrayList<JSONObject> fields=new ArrayList<>();
            for(int k=0;k<params.length();k++){JSONObject param=params.optJSONObject(k);if(shown.add(param.optInt("id")))fields.add(param);}
            if(fields.isEmpty())continue;
            LinearLayout section=column();section.setBackground(tint(CARD));boolean trackFields=false,sharedFields=false;for(JSONObject field:fields){if(field.optInt("id")>=Native.GLOBAL_BASE)sharedFields=true;else trackFields=true;}String scope=sharedFields?(trackFields?"TRACK / SHARED":"SHARED"):isDrum()?"DRUM TRACK":"TRACK "+(selected+1);section.addView(label(group.optString("title")+" · "+scope,12,GREEN),lpFill(32));
            if(title.equals("ENV")&&hasAdsr(params)){envelope=new Envelope(params);section.addView(envelope,lpFill(120));}
            for(int k=0;k<fields.size();k+=4){LinearLayout controls=row();for(int j=k;j<Math.min(k+4,fields.size());j++)controls.addView(instrumentKnob(fields.get(j)),weight(130));section.addView(controls);}
            LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,-2);space.setMargins(dp(2),dp(4),dp(2),dp(4));body.addView(section,space);
        }
        if(shown.isEmpty())body.addView(label("Use ALL PAGES to edit this instrument's native controls.",14,DIM),lpFill(56));
        if(isDrum()&&definition.has("drum_synth"))body.addView(button("EDIT DRUM SYNTH KIT",v->panels.synthKits()),buttonFill(44));
        if(definition.has("dx7"))body.addView(button("DX7 VOICE / OPERATORS",v->panels.dx7()),buttonFill(44));
        else if(definition.has("fm6")&&state.optJSONArray("engines").optString(state.optInt("engine")).equalsIgnoreCase("FM6"))body.addView(button("FM6 OPERATORS",v->panels.fm6()),buttonFill(44));
    }
    private boolean hasAdsr(JSONArray params){HashSet<String> labels=new HashSet<>();for(int i=0;i<params.length();i++)labels.add(params.optJSONObject(i).optString("label"));return labels.containsAll(Arrays.asList("ATK","DEC","SUS","REL"));}
    /** Normalized parameter sketch: native DSP times and modulation remain firmware-owned. */
    final class Envelope extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Map<String,Integer> ids=new HashMap<>();
        Envelope(JSONArray params){super(StudioView.this.getContext());for(int i=0;i<params.length();i++){JSONObject p=params.optJSONObject(i);ids.put(p.optString("label"),p.optInt("id"));}setContentDescription("Envelope shape: attack rises, decay falls to sustain, release fades after note off. Normalized sketch.");}
        float fraction(String label){for(Knob k:knobs)if(ids.containsKey(label)&&k.id==ids.get(label))return Math.max(0,Math.min(1,(k.value-k.min)/(float)Math.max(1,k.max-k.min)));return 0;}
        @Override protected void onDraw(Canvas c){
            float x=dp(14),base=dp(82),height=dp(54),unit=Math.max(1,(getWidth()-dp(28))/5f);
            float a=x+unit*(.15f+.85f*fraction("ATK")),d=a+unit*(.15f+.85f*fraction("DEC")),off=d+unit,sustain=base-height*fraction("SUS"),end=off+unit*(.15f+.85f*fraction("REL"));
            paint.setColor(DIM);paint.setStrokeWidth(dp(1));c.drawLine(x,base,getWidth()-x,base,paint);
            Path path=new Path();path.moveTo(x,base);path.lineTo(a,base-height);path.lineTo(d,sustain);path.lineTo(off,sustain);path.lineTo(end,base);
            paint.setColor(GREEN);paint.setStrokeWidth(dp(3));paint.setStyle(Paint.Style.STROKE);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(InstrumentStyle.font(getContext()));paint.setTextSize(dp(10));paint.setColor(DIM);c.drawText("ATTACK → DECAY → SUSTAIN → RELEASE",x,dp(16),paint);c.drawText("Normalized shape · note off at dotted line",x,dp(108),paint);
            paint.setStrokeWidth(dp(1));for(int y=28;y<86;y+=6)c.drawLine(off,dp(y),off,dp(y+3),paint);
        }
    }
    private void buildMixer(LinearLayout body){LinearLayout strips=row();strips.setGravity(Gravity.TOP);HorizontalScrollView mixerScroll=new HorizontalScrollView(getContext());mixerScroll.addView(strips);body.addView(mixerScroll);for(int i=0;i<trackCount;i++){final int target=i;JSONObject t=state.optJSONArray("tracks").optJSONObject(i);LinearLayout strip=column();strip.setBackground(tint(CARD));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(trackCount>4?dp(94):Math.max(dp(70),(console.getWidth()-dp(24))/trackCount),-2);p.setMargins(dp(2),0,dp(2),0);strips.addView(strip,p);TextView name=label(definition.tracks[i],14,TEXT);strip.addView(name,lpFill(36));mixerNames.add(name);Fader level=new Fader(target,t.optInt("level"));strip.addView(level,lpFill(100));levels.add(level);Button mute=button("MUTE",v->Native.event(Native.MUTE,target,state.optJSONArray("tracks").optJSONObject(target).optInt("mute")==0?1:0));strip.addView(mute,buttonFill(44));mutes.add(mute);}}
    void armChord(ChordPlacement chord){
        if(state==null||selected!=chord.track||isDrum()){Toast.makeText(getContext(),"Choose a synth track first",Toast.LENGTH_SHORT).show();return;}
        chordStamp=chord;octave=chord.notes()[0]/12;openPage("STEPS");updateChordToolbar();Toast.makeText(getContext(),chord.name+" ready — tap a step to place it. Tap the chord icon to cancel.",Toast.LENGTH_LONG).show();
    }
    private void cancelChordStamp(){chordStamp=null;if(grid!=null){grid.stampPreviewStep=-1;grid.invalidate();}updateChordToolbar();}
    private void updateChordToolbar(){
        if(patternLabel!=null&&state!=null)patternLabel.setText(chordStamp!=null?chordStamp.name+" · "+chordStamp.length+" steps\nTAP A STEP":(bank*16+1)+"-"+Math.min(bank*16+16,state.optInt("length"))+" / "+state.optInt("length")+(isDrum()?"":"  C"+(octave-1)));
        if(chordButton!=null){chordButton.setSelected(chordStamp!=null);chordButton.setBackground(tint(chordStamp!=null?0xff174531:CARD));chordButton.setContentDescription(chordStamp!=null?"Cancel "+chordStamp.name+" chord placement":"Choose a chord");chordButton.setTooltipText(chordStamp!=null?"Cancel chord placement":"Chord");}
        if(grid!=null)grid.setContentDescription(chordStamp!=null?chordStamp.name+" chord ready. Tap a step number or any cell in its column to place one chord. Tap the chord icon to cancel.":isDrum()?"Drum sequencer. Tap cells to toggle hits.":"Piano roll. Separate blocks mark new note attacks; continuous bars mark held notes. Tap cells to add or remove notes; up to four per step.");
    }
    private String chordPlacementError(int step){
        if(chordStamp==null)return "Choose a chord first.";
        if(chordStamp.track!=selected||isDrum())return "Choose a synth track first.";
        if(state.optInt("recordState")!=0)return "Stop recording before placing a chord.";
        if(state.optInt("playing")!=0)return "Stop playback before placing a chord.";
        int length=state.optInt("length");boolean[] occupied=new boolean[length];for(int i=0;i<length;i++)occupied[i]=state.optJSONArray("steps").optJSONArray(i).optInt(0,-1)>=0||state.optJSONArray("detail").optJSONObject(i).optInt("time")==1;
        return chordStamp.error(step,length,occupied);
    }
    private void stampChord(int step){
        String error=chordPlacementError(step);if(error!=null){Toast.makeText(getContext(),error,Toast.LENGTH_LONG).show();return;}
        ChordPlacement chord=chordStamp;panels.action(52,0);try{for(int pitch:chord.notes())Native.event(Native.STEP,selected*state.optInt("stepStride",8192)+step*128+pitch,1);panels.detail(step,0,5,chord.length);}finally{panels.action(53,0);}
        cancelChordStamp();if(grid!=null){grid.lastStampStep=step;grid.stampFlashUntil=android.os.SystemClock.uptimeMillis()+650;grid.invalidate();grid.postInvalidateDelayed(700);}Toast.makeText(getContext(),chord.name+" placed at step "+(step+1),Toast.LENGTH_SHORT).show();
    }
    /** Three stacked piano-roll notes and a plus, rendered on a crisp 12x12 pixel grid. */
    final class ChordIcon extends android.graphics.drawable.Drawable {
        final Paint paint=new Paint();
        final String[] pixels={"000000000100","000000001110","000000000100","011111000000","011111000000","000000000000","011111000000","011111000000","000000000000","011111000000","011111000000","000000000000"};
        ChordIcon(){paint.setColor(GREEN);}
        @Override public void draw(Canvas canvas){Rect b=getBounds();float unit=Math.min(b.width(),b.height())/12f;for(int y=0;y<12;y++)for(int x=0;x<12;x++)if(pixels[y].charAt(x)=='1')canvas.drawRect(b.left+x*unit,b.top+y*unit,b.left+(x+1)*unit,b.top+(y+1)*unit,paint);}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private void buildSequencer(){
        LinearLayout body=column();content.addView(body);LinearLayout tools=row();
        tools.addView(button("<",v->{bank=Math.max(0,bank-1);accept(state);}),buttonSize(40,44));
        patternLabel=label("",12,TEXT);patternLabel.setMaxLines(2);tools.addView(patternLabel,new LinearLayout.LayoutParams(dp(90),dp(44)));
        tools.addView(button(">",v->{bank=Math.min((state.optInt("length")-1)/16,bank+1);accept(state);}),buttonSize(40,44));
        if(!isDrum()){chordButton=button("",v->{if(chordStamp!=null)cancelChordStamp();else panels.chord();});ChordIcon icon=new ChordIcon();icon.setBounds(0,0,dp(24),dp(24));chordButton.setCompoundDrawables(icon,null,null,null);chordButton.setContentDescription("Choose a chord");chordButton.setTooltipText("Chord");tools.addView(chordButton,buttonSize(40,44));}
        tools.addView(button("EDIT",v->panels.pattern()),buttonSize(48,44));
        tools.addView(button("LEN",v->{final int parameter=state.optInt("lengthParameter");numberEditor("PATTERN LENGTH",state.optInt("length"),1,state.optInt("maxSteps",64),n->Native.event(Native.PARAM,parameter,n));}),buttonSize(48,44));
        if(!isDrum()){tools.addView(button("O-",v->{octave=Math.max(0,octave-1);accept(state);}),buttonSize(40,44));tools.addView(button("O+",v->{octave=Math.min(10,octave+1);accept(state);}),buttonSize(40,44));}
        HorizontalScrollView toolbar=new HorizontalScrollView(getContext());toolbar.setFillViewport(true);toolbar.setHorizontalScrollBarEnabled(false);toolbar.addView(tools);body.addView(toolbar,lpFill(48));if(!isDrum()){octaveOverview=new OctaveOverview(getContext());body.addView(octaveOverview,lpFill(32));}
        ScrollView vertical=new ScrollView(getContext());HorizontalScrollView horizontal=new HorizontalScrollView(getContext());grid=new Grid(getContext());horizontal.addView(grid);vertical.addView(horizontal);grid.horizontal=horizontal;grid.vertical=vertical;final Grid viewport=grid;horizontal.setOnScrollChangeListener((v,x,y,ox,oy)->viewport.invalidate());vertical.setOnScrollChangeListener((v,x,y,ox,oy)->viewport.invalidate());body.addView(vertical,new LinearLayout.LayoutParams(-1,0,1));accept(state);
    }

    /** Profiles choose evenly spaced buttons or exact native row alignment. */
    final class TrackRail extends FrameLayout {
        TrackRail(Context context){super(context);}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){alignTracks();}
        void alignTracks(){
            if(!compactDisplay||screen.getHeight()==0)return;
            RectF bounds=screen.renderBounds();Rect safe=safeArea(this);
            int left=dp(4)+safe.left,right=getWidth()-dp(4)-Math.max(0,getWidth()-safe.right);
            float first,last;
            if(definition.trackButtonsEven){
                last=safe.bottom;
                first=safe.top;
            }
            else {first=screen.getTop()-getTop()+bounds.top+bounds.height()*definition.trackBounds[1]/240f;
                  last=screen.getTop()-getTop()+bounds.top+bounds.height()*definition.trackBounds[3]/240f;}
            for(int i=0;i<trackCount;i++){
                LinearLayout cell=trackCells[i];if(cell==null||cell.getParent()!=this)continue;
                int top=Math.round(TrackGeometry.edge(first,last,i,trackCount));
                int bottom=Math.round(TrackGeometry.edge(first,last,i+1,trackCount));
                if(definition.trackButtonsEven){int gap=Math.min(dp(2),Math.max(0,(bottom-top-1)/2));top+=gap;bottom-=gap;}
                int height=Math.max(1,bottom-top);
                for(int j=0;j<cell.getChildCount();j++)cell.getChildAt(j).getLayoutParams().height=height;
                cell.measure(MeasureSpec.makeMeasureSpec(Math.max(1,right-left),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
                cell.layout(left,top,right,bottom);
            }
        }
    }
    final class Screen extends View {
        final Bitmap bitmap=Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888);
        final int[] pixels=new int[240*240];final Paint paint=new Paint();final GestureDetector gestures;
        boolean multiTouch,pinching;float firstSpan,lastSpan;
        Screen(Context c){
            super(c);setClickable(true);setFocusable(true);
            gestures=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onSingleTapConfirmed(MotionEvent e){int track=trackAt(e.getX(),e.getY());if(track>=0)selectTrack(track);return true;}@Override public void onLongPress(MotionEvent e){int track=trackAt(e.getX(),e.getY());if(track>=0)showSounds(track,false);}@Override public boolean onDown(MotionEvent e){return true;}@Override public boolean onDoubleTap(MotionEvent e){performClick();return true;}});
            setContentDescription("Original "+firmware.name+" display. Pinch to resize; double-tap to reset to 100%.");
        }
        void refresh(){Native.frame(pixels);bitmap.setPixels(pixels,0,240,0,0,240,240);invalidate();}
        RectF frame(RectF area){float side=Math.min(area.width(),area.height())-dp(4);float x=area.left+(area.width()-side)/2,y=area.top+(area.height()-side)/2;return new RectF(x,y,x+side,y+side);}
        // HOME rows are y=40..184 in the original 240px display, on every page.
        RectF renderBounds(){RectF area=new RectF(0,0,getWidth(),getHeight());if(cameraClearance){Rect safe=safeArea(this);if(safe.width()>dp(8)&&safe.height()>dp(8))area=new RectF(safe);}RectF result=frame(area);result.inset(dp(2),dp(2));return result;}
        int trackAt(float x,float y){RectF bounds=renderBounds();if(!bounds.contains(x,y))return -1;return TrackGeometry.hit(definition.trackAxis,definition.trackBounds(state==null?-1:state.optInt("nativeScreen",-1)),trackCount,(x-bounds.left)*240/bounds.width(),(y-bounds.top)*240/bounds.height());}
        @Override public boolean onTouchEvent(MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){multiTouch=false;pinching=false;firstSpan=lastSpan=0;}
            if(event.getPointerCount()>1){
                if(!multiTouch){multiTouch=true;MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);gestures.onTouchEvent(cancel);cancel.recycle();}
                float span=(float)Math.hypot(event.getX(1)-event.getX(0),event.getY(1)-event.getY(0));
                if(span>=dp(24)){
                    if(lastSpan==0)firstSpan=lastSpan=span;
                    else if(action==MotionEvent.ACTION_MOVE&&(pinching||Math.abs(span-firstSpan)>dp(4))){
                        if(!pinching){pinching=true;releaseAll();getParent().requestDisallowInterceptTouchEvent(true);}
                        setDisplayZoom(displayZoom()*span/lastSpan,false);lastSpan=span;
                    }
                }
            }
            if(!multiTouch)gestures.onTouchEvent(event);
            if(action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){if(pinching)saveDisplayZoom();pinching=false;firstSpan=lastSpan=0;getParent().requestDisallowInterceptTouchEvent(false);}return true;
        }
        @Override public boolean performClick(){super.performClick();setDisplayZoom(1,true);return true;}
        @Override protected void onDraw(Canvas c){
            RectF display=frame(new RectF(0,0,getWidth(),getHeight()));paint.setColor(0xff020403);c.drawRoundRect(display,dp(6),dp(6),paint);
            display=renderBounds();paint.setFilterBitmap(false);c.drawBitmap(bitmap,null,display,paint);
        }
    }
    private boolean inside(View child,View ancestor){for(android.view.ViewParent parent=child.getParent();parent instanceof View;parent=parent.getParent())if(parent==ancestor)return true;return false;}
    Knob instrumentKnob(JSONObject data){Knob k=new Knob(data);knobs.add(k);return k;}
    final class Knob extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);int id,value,min,max;String label,text;JSONArray choices,choiceValues;boolean dragging;int startValue;
        final KnobGesture gesture=new KnobGesture(ViewConfiguration.get(getContext()).getScaledTouchSlop(),ViewConfiguration.get(getContext()).getScaledDoubleTapSlop(),ViewConfiguration.getDoubleTapTimeout());
        java.util.function.Supplier<JSONObject> dataSource;IntConsumer sendValue;
        final Runnable scrollHold=()->{if(gesture.hold(android.os.SystemClock.uptimeMillis())){getParentDisallow(false);describe();invalidate();}};
        Knob(JSONObject data){super(StudioView.this.getContext());setFocusable(true);setClickable(true);update(data);}
        void describe(){setContentDescription(label+", "+text+(gesture.isScrolling()?". Scroll ready. Drag to scroll.":". Drag the dial or value box to adjust. Swipe labels or surrounding space to scroll, or hold the control still for 750 milliseconds before scrolling. Double-tap for exact entry."));}
        void update(JSONObject data){if(dragging||gesture.capturesDrag())return;id=data.optInt("id");value=data.optInt("value");min=data.optInt("min");max=data.optInt("max");label=definition.label(data.optString("label"));text=data.optString("text");choices=data.optJSONArray("choices");choiceValues=data.optJSONArray("choiceValues");describe();invalidate();}
        void send(int n){value=Math.max(min,Math.min(max,n));text=Integer.toString(value);if(sendValue!=null)sendValue.accept(value);else Native.event(id>=Native.GLOBAL_BASE?Native.GLOBAL:Native.PARAM,id>=Native.GLOBAL_BASE?id-Native.GLOBAL_BASE:id,value);describe();invalidate();if(envelope!=null)envelope.invalidate();if(drumEnvelope!=null)drumEnvelope.invalidate();}
        void cancelGesture(){removeCallbacks(scrollHold);if(dragging)Native.event(Native.ACTION,selected*256+53,0);dragging=false;gesture.cancel();getParentDisallow(false);describe();invalidate();}
        void getParentDisallow(boolean disallow){if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(disallow);}
        boolean discrete(){return choices!=null&&choices.length()>0;}
        float dialRadius(){return Math.min(dp(27),getWidth()*.29f);}
        boolean tuningRegion(float x,float y){
            if(getHeight()<=0)return false;float canvasY=y*dp(130)/(float)getHeight();
            return discrete()?KnobGeometry.choice(x,canvasY,dp(5),dp(35),getWidth()-dp(5),dp(84),dp(6)):KnobGeometry.dial(x,canvasY,getWidth()/2f,dp(57),dialRadius()+dp(5)/2f);
        }
        @Override protected void onDraw(Canvas c){
            int saved=c.save();c.scale(1,getHeight()/(float)dp(130));
            float x=getWidth()/2f,y=dp(57),r=dialRadius();
            if(gesture.isScrolling()){p.setShader(new RadialGradient(x,y,r+dp(22),new int[]{0x6065bdea,0x2065bdea,0x0065bdea},new float[]{0,.65f,1},Shader.TileMode.CLAMP));c.drawCircle(x,y,r+dp(22),p);p.setShader(null);p.setColor(0x2265bdea);c.drawRoundRect(dp(2),dp(28),getWidth()-dp(2),dp(113),dp(8),dp(8),p);}
            p.setTextAlign(Paint.Align.CENTER);p.setTypeface(InstrumentStyle.font(getContext()));p.setTextSize(dp(14));while(p.measureText(label.toUpperCase(Locale.ROOT))>getWidth()-dp(8)&&p.getTextSize()>dp(9))p.setTextSize(p.getTextSize()-1);p.setColor(gesture.isScrolling()?GREEN:DIM);c.drawText(label.toUpperCase(Locale.ROOT),x,dp(19),p);
            if(discrete()){
                p.setColor(0xff111915);c.drawRoundRect(dp(5),dp(35),getWidth()-dp(5),dp(84),dp(6),dp(6),p);
                p.setColor(GREEN);p.setTextSize(dp(16));while(p.measureText(text)>getWidth()-dp(14)&&p.getTextSize()>dp(10))p.setTextSize(p.getTextSize()-1);c.drawText(text.toUpperCase(Locale.ROOT),x,dp(64),p);
                p.setTextSize(dp(9));p.setColor(gesture.isScrolling()?GREEN:DIM);c.drawText(gesture.isScrolling()?"SCROLL READY":"HOLD TO SCROLL",x,dp(111),p);c.restoreToCount(saved);return;
            }
            RectF arc=new RectF(x-r,y-r,x+r,y+r);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(5));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(0xff394a40);c.drawArc(arc,135,270,false,p);
            float f=max==min?0:(value-min)/(float)(max-min);p.setColor(GREEN);c.drawArc(arc,135,270*f,false,p);
            if(min<0&&max>0){double zero=Math.toRadians(135+270*(-min)/(float)(max-min));p.setColor(DIM);p.setStrokeWidth(dp(1));c.drawLine(x+(float)Math.cos(zero)*(r+dp(5)),y+(float)Math.sin(zero)*(r+dp(5)),x+(float)Math.cos(zero)*(r+dp(9)),y+(float)Math.sin(zero)*(r+dp(9)),p);}
            double a=Math.toRadians(135+270*f);p.setColor(GREEN);p.setStrokeWidth(dp(3));c.drawLine(x+(float)Math.cos(a)*r*.45f,y+(float)Math.sin(a)*r*.45f,x+(float)Math.cos(a)*r*.8f,y+(float)Math.sin(a)*r*.8f,p);
            p.setStyle(Paint.Style.FILL);p.setTextSize(dp(14));c.drawText(text.toUpperCase(Locale.ROOT),x,dp(103),p);p.setColor(gesture.isScrolling()?GREEN:DIM);p.setTextSize(dp(9));c.drawText(gesture.isScrolling()?"SCROLL READY":"HOLD TO SCROLL",x,dp(123),p);c.restoreToCount(saved);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(!active)return false;
            switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                startValue=value;boolean tuning=tuningRegion(e.getX(),e.getY());gesture.begin(e.getX(),e.getY(),e.getEventTime(),tuning);if(tuning)postDelayed(scrollHold,KnobGesture.SCROLL_HOLD_MS);getParentDisallow(tuning);return true;
            case MotionEvent.ACTION_MOVE:
                if(gesture.move(e.getX(),e.getY(),e.getEventTime())){
                    removeCallbacks(scrollHold);if(!dragging){dragging=true;Native.event(Native.ACTION,selected*256+52,0);}
                    float delta=gesture.delta(e.getX(),e.getY());
                    if(discrete()){int index=0;for(int i=0;i<choices.length();i++)if((choiceValues!=null?choiceValues.optInt(i):i+min)==startValue)index=i;index=Math.max(0,Math.min(choices.length()-1,index+Math.round(delta/dp(18))));send(choiceValues!=null?choiceValues.optInt(index):index+min);}
                    else send(startValue+Math.round(delta/Math.max(dp(1.5f),dp(240f)/Math.max(1,max-min))));
                }else if(gesture.isScrolling()){getParentDisallow(false);describe();invalidate();}return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(scrollHold);int result=gesture.end(e.getX(),e.getY(),e.getEventTime());if(dragging)Native.event(Native.ACTION,selected*256+53,0);dragging=false;getParentDisallow(false);describe();invalidate();
                if(result==KnobGesture.DOUBLE_TAP){super.performClick();openEditor();}else if(result==KnobGesture.TAP)performClick();return true;
            case MotionEvent.ACTION_CANCEL:cancelGesture();return true;
            case MotionEvent.ACTION_POINTER_DOWN:cancelGesture();return true;
            }return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        void openEditor(){
            if(discrete()){int selectedChoice=0;if(choiceValues!=null)for(int i=0;i<choiceValues.length();i++)if(choiceValues.optInt(i)<=value)selectedChoice=i;picker(label,strings(choices),selectedChoice,choices.length()>12,n->send(choiceValues!=null?choiceValues.optInt(n):n+min));}
            else numberEditor(label,value,min,max,this::send);
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(SeekBar.class.getName());info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,min,max,value));info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK);}
        @Override public boolean performAccessibilityAction(int action,Bundle args){if(action==AccessibilityNodeInfo.ACTION_CLICK||action==AccessibilityNodeInfo.ACTION_LONG_CLICK){openEditor();return true;}if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&args!=null){send(Math.round(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)));return true;}return super.performAccessibilityAction(action,args);}
    }
    final class Fader extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final int target;int value;
        Fader(int track,int level){super(StudioView.this.getContext());target=track;setFocusable(true);setClickable(true);update(level);}
        void update(int n){value=n;setContentDescription((isDrum(target)?"Drums":"Track "+(target+1))+" level, "+Math.round(value*100f/127)+" percent");invalidate();}
        void send(int n){update(Math.max(0,Math.min(127,n)));Native.event(Native.MIX,target,value);}
        @Override protected void onDraw(Canvas c){float x=getWidth()/2f,top=dp(15),bottom=getHeight()-dp(15),y=bottom-(bottom-top)*value/127f;
            p.setStrokeWidth(dp(1));p.setColor(0xff3e4941);for(int i=0;i<=8;i++){float ty=top+(bottom-top)*i/8;c.drawLine(x-dp(24),ty,x+dp(24),ty,p);}
            p.setStrokeWidth(dp(8));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(0xff080a09);c.drawLine(x,top,x,bottom,p);p.setColor(GREEN);c.drawLine(x,y,x,bottom,p);
            p.setColor(TEXT);c.drawRoundRect(x-dp(18),y-dp(7),x+dp(18),y+dp(7),dp(3),dp(3),p);
        }
        @Override public boolean onTouchEvent(MotionEvent e){if(!active)return false;int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN){setPressed(true);getParent().requestDisallowInterceptTouchEvent(true);}if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_MOVE||a==MotionEvent.ACTION_UP)send(Math.round(127*(getHeight()-dp(15)-e.getY())/(getHeight()-dp(30))));
            if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){setPressed(false);getParent().requestDisallowInterceptTouchEvent(false);if(a==MotionEvent.ACTION_UP)performClick();}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(SeekBar.class.getName());info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,0,127,value));info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}
        @Override public boolean performAccessibilityAction(int action,Bundle args){if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&args!=null){send(Math.round(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)));return true;}if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){send(value+(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1));return true;}return super.performAccessibilityAction(action,args);}
    }
    final class OctaveOverview extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final ArrayList<Integer> octaves=new ArrayList<>();
        OctaveOverview(Context c){super(c);setContentDescription("Occupied octaves. Tap an octave to focus its notes.");}
        void collect(){octaves.clear();boolean[] used=new boolean[11];JSONArray steps=state.optJSONArray("steps");for(int i=0;i<state.optInt("length");i++)for(int j=0;j<4;j++){int n=steps.optJSONArray(i).optInt(j,-1);if(n>=0&&n<128)used[n/12]=true;}if(chordStamp!=null)for(int n:chordStamp.notes())used[n/12]=true;used[octave]=true;for(int i=10;i>=0;i--)if(used[i])octaves.add(i);}
        @Override protected void onDraw(Canvas c){if(state==null)return;collect();float width=getWidth()/(float)octaves.size();paint.setTypeface(InstrumentStyle.font(getContext()));paint.setTextSize(dp(10));for(int k=0;k<octaves.size();k++){int o=octaves.get(k);paint.setColor(o==octave?GREEN:DIM);c.drawText("C"+(o-1),k*width+dp(3),dp(12),paint);for(int step=0;step<state.optInt("length");step++)for(int j=0;j<4;j++){int n=state.optJSONArray("steps").optJSONArray(step).optInt(j,-1);if(n>=0&&n/12==o){float x=k*width+dp(3)+(width-dp(6))*step/state.optInt("length");float y=dp(18)+(11-n%12)*8f/12;c.drawRect(x,y,x+Math.max(2,(width-dp(6))/state.optInt("length")),y+1,paint);}}}}
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_UP&&!octaves.isEmpty()){octave=octaves.get(Math.max(0,Math.min(octaves.size()-1,(int)(e.getX()*octaves.size()/getWidth()))));accept(state);performClick();}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
    final class Grid extends View {
        int stampPreviewStep=-1,lastStampStep=-1;long stampFlashUntil;boolean stampingGesture;
        HorizontalScrollView horizontal;ScrollView vertical;
        int scrollX(){return horizontal==null?0:horizontal.getScrollX();}
        int scrollY(){return vertical==null?0:vertical.getScrollY();}
        boolean inCells(float x,float y){return x>=scrollX()+left&&y>=scrollY()+top;}
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);float downX,downY;final int widthCell=dp(30),cell=dp(24),left=dp(68),top=dp(28);boolean moved;long downTime;int dragStep=-1,dragNote;boolean resizeDrag,held;
        final Runnable hold=()->{if(!moved){int col=(int)((downX-left)/((getWidth()-left)/16f)),r=(int)((downY-top)/cell),step=bank*16+col;if(inCells(downX,downY)&&col>=0&&col<16&&r>=0&&r<(isDrum()?lanes():12)&&step<state.optInt("length")){if(note(r)>127)return;held=true;int owner=step;while(!isDrum()&&owner>0&&state.optJSONArray("detail").optJSONObject(owner).optInt("time")==1)owner--;panels.step(owner,note(r));}}};
        Grid(Context c){super(c);setContentDescription(isDrum()?"Drum sequencer. Tap cells to toggle hits.":"Piano roll. Separate blocks mark new note attacks; continuous bars mark held notes. Tap cells to add or remove notes; up to four per step.");}
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(Math.max(left+16*widthCell,MeasureSpec.getSize(w)),top+(isDrum()?lanes():12)*cell);}
        boolean on(int step,int note){JSONArray notes=state.optJSONArray("steps").optJSONArray(step);if(isDrum())return (notes.optInt(0)&(1<<note))!=0;while(step>0&&state.optJSONArray("detail").optJSONObject(step).optInt("time")==1){step--;notes=state.optJSONArray("steps").optJSONArray(step);}for(int i=0;i<4;i++)if(notes.optInt(i,-1)==note)return true;return false;}
        int note(int row){return isDrum()?row:octave*12+11-row;}
        @Override protected void onDraw(Canvas c){if(state==null)return;p.setTypeface(InstrumentStyle.font(getContext()));p.setTextSize(dp(11));int rows=isDrum()?lanes():12;float cw=(getWidth()-left)/16f;
            String[] names={"C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B"};
            // Paint cells first, then each synth onset as one bar across its native ties.
            for(int r=0;r<rows;r++)for(int col=0;col<16;col++){
                int step=bank*16+col,n=note(r);boolean enabled=step<state.optInt("length")&&n<128;
                p.setColor(!enabled?0xff101210:isDrum()&&on(step,n)?GREEN:col%4==0?0xff303830:0xff202620);
                c.drawRoundRect(left+col*cw+2,top+r*cell+2,left+(col+1)*cw-2,top+(r+1)*cell-2,dp(4),dp(4),p);
            }
            if(!isDrum()){
                int count=state.optInt("length");int[][] notes=new int[count][4];boolean[] ties=new boolean[count];
                for(int step=0;step<count;step++){JSONArray pitches=state.optJSONArray("steps").optJSONArray(step);for(int slot=0;slot<4;slot++)notes[step][slot]=pitches.optInt(slot,-1);ties[step]=state.optJSONArray("detail").optJSONObject(step).optInt("time")==1;}
                for(int r=0;r<rows;r++){
                    int n=note(r);if(n>127)continue;int saved=c.save();c.clipRect(left,top+r*cell,getWidth(),top+(r+1)*cell);
                    for(PianoRollNotes.Span span:PianoRollNotes.spans(notes,ties,n,bank*16,bank*16+16)){
                        float x1=left+(span.start-bank*16)*cw+2,x2=left+(span.end-bank*16)*cw-2,y1=top+r*cell+2,y2=top+(r+1)*cell-2;
                        p.setColor(GREEN);c.drawRoundRect(x1,y1,x2,y2,dp(4),dp(4),p);
                        // Subtle beat divisions keep timing readable without splitting the note.
                        p.setColor(0x3030473b);p.setStrokeWidth(dp(1));for(int step=Math.max(span.start+1,bank*16);step<Math.min(span.end,bank*16+16);step++){float x=left+(step-bank*16)*cw;c.drawLine(x,y1+dp(2),x,y2-dp(2),p);}
                        // One bright attack mark per actual onset, never on tied continuation cells.
                        if(span.start>=bank*16){p.setColor(0xffc7ffe0);p.setStrokeWidth(dp(2));c.drawLine(x1+dp(4),y1+dp(3),x1+dp(4),y2-dp(3),p);}
                    }
                    c.restoreToCount(saved);
                }
            }
            // Automation and play-position markers remain visible over the note bars.
            for(int r=0;r<rows;r++)for(int col=0;col<16;col++){
                int step=bank*16+col;boolean enabled=step<state.optInt("length")&&note(r)<128;
                if(enabled){JSONObject detail=state.optJSONArray("detail").optJSONObject(step);if(detail.optInt("nudge")!=0||detail.optInt("fill")!=0||detail.optJSONArray("locks").length()!=0){p.setColor(0xffffce61);c.drawCircle(left+(col+1)*cw-dp(6),top+(r+1)*cell-dp(6),dp(2),p);}}
                if(enabled&&step==state.optInt("position")){p.setColor(0xffd9ffe7);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));c.drawRoundRect(left+col*cw+2,top+r*cell+2,left+(col+1)*cw-2,top+(r+1)*cell-2,dp(4),dp(4),p);p.setStyle(Paint.Style.FILL);}
            }
            int target=stampPreviewStep>=0?stampPreviewStep:android.os.SystemClock.uptimeMillis()<stampFlashUntil?lastStampStep:-1;
            if(target>=bank*16&&target<bank*16+16){float x=left+(target-bank*16)*cw;int color=stampPreviewStep>=0&&chordPlacementError(target)!=null?0xffff8878:GREEN;p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));c.drawRect(x+1,top+1,x+cw-1,getHeight()-1,p);p.setStyle(Paint.Style.FILL);
                if(chordStamp!=null&&stampPreviewStep>=0){int[] notes=chordStamp.notes();p.setColor((color&0x00ffffff)|0x66000000);for(int r=0;r<rows;r++)for(int pitch:notes)if(note(r)==pitch)c.drawRoundRect(x+2,top+r*cell+2,Math.min(getWidth(),x+Math.min(chordStamp.length,bank*16+16-target)*cw)-2,top+(r+1)*cell-2,dp(4),dp(4),p);}
            }
            int sx=scrollX(),sy=scrollY();
            p.setColor(BG);c.drawRect(sx,0,sx+left,getHeight(),p);
            for(int r=0;r<rows;r++){int n=note(r);p.setColor(DIM);c.drawText(isDrum()?state.optJSONArray("lanes").optString(r):names[n%12]+(n/12-1),sx+dp(4),top+r*cell+cell*.65f,p);}
            p.setColor(BG);c.drawRect(0,sy,getWidth(),sy+top,p);
            for(int col=0;col<16;col++){int step=bank*16+col;p.setColor(step==state.optInt("position")?GREEN:DIM);c.drawText(""+(step+1),left+col*cw+dp(8),sy+dp(19),p);}
            p.setColor(CARD);c.drawRect(sx,sy,sx+left,sy+top,p);
        }
        int stampStep(float x,float y){float cw=(getWidth()-left)/16f;int col=(int)((x-left)/cw),row=(int)((y-top)/cell);boolean header=y>=scrollY()&&y<scrollY()+top,cells=inCells(x,y)&&row>=0&&row<(isDrum()?lanes():12);if(x<scrollX()+left||col<0||col>=16||(!header&&!cells))return -1;int step=bank*16+col;return step<state.optInt("length")?step:-1;}
        @Override public boolean onTouchEvent(MotionEvent e){
            if(!active)return false;int action=e.getActionMasked();float cw=(getWidth()-left)/16f;
            if(action==MotionEvent.ACTION_DOWN&&chordStamp!=null){stampingGesture=true;downX=e.getX();downY=e.getY();moved=false;removeCallbacks(hold);stampPreviewStep=stampStep(downX,downY);invalidate();return true;}
            if(stampingGesture){
                if(action==MotionEvent.ACTION_MOVE&&(Math.abs(e.getX()-downX)>dp(8)||Math.abs(e.getY()-downY)>dp(8))){moved=true;stampPreviewStep=-1;invalidate();}
                if(action==MotionEvent.ACTION_POINTER_DOWN){moved=true;stampPreviewStep=-1;invalidate();}
                if(action==MotionEvent.ACTION_CANCEL){stampingGesture=false;stampPreviewStep=-1;invalidate();}
                if(action==MotionEvent.ACTION_UP){int step=stampPreviewStep;stampingGesture=false;stampPreviewStep=-1;if(!moved&&step>=0&&chordStamp!=null){stampChord(step);performClick();}invalidate();}return true;
            }
            if(action==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();downTime=e.getEventTime();moved=false;held=false;dragStep=-1;postDelayed(hold,ViewConfiguration.getLongPressTimeout());
                int col=(int)((downX-left)/cw),r=(int)((downY-top)/cell),step=bank*16+col;
                if(!isDrum()&&inCells(downX,downY)&&col>=0&&col<16&&r>=0&&r<12&&step<state.optInt("length")&&on(step,note(r))){while(step>0&&state.optJSONArray("detail").optJSONObject(step).optInt("time")==1)step--;dragStep=step;dragNote=note(r);float end=left+(step-bank*16+state.optJSONArray("detail").optJSONObject(step).optInt("length"))*cw;resizeDrag=downX>=end-dp(12);getParent().requestDisallowInterceptTouchEvent(true);}return true;}
            if(action==MotionEvent.ACTION_MOVE){if(Math.abs(e.getX()-downX)>dp(8)||Math.abs(e.getY()-downY)>dp(8)){moved=true;removeCallbacks(hold);}return true;}
            if(action==MotionEvent.ACTION_CANCEL){removeCallbacks(hold);dragStep=-1;getParent().requestDisallowInterceptTouchEvent(false);return true;}
            if(action==MotionEvent.ACTION_UP){removeCallbacks(hold);if(held){dragStep=-1;getParent().requestDisallowInterceptTouchEvent(false);return true;}getParent().requestDisallowInterceptTouchEvent(false);
                if(moved&&dragStep>=0){int col=Math.max(0,Math.min(15,(int)((e.getX()-left)/cw))),step=bank*16+col;
                    if(resizeDrag)panels.detail(dragStep,0,5,Math.max(1,Math.min(state.optInt("length")-dragStep,step-dragStep+1)));
                    else{int row=Math.max(0,Math.min(11,(int)((e.getY()-top)/cell)));panels.detail(dragStep,0,9,step*128+Math.max(0,Math.min(127,note(row)-dragNote+64)));}dragStep=-1;performClick();return true;}
                if(!moved){int col=(int)((e.getX()-left)/cw),r=(int)((e.getY()-top)/cell),step=bank*16+col;
                    if(inCells(e.getX(),e.getY())&&col>=0&&col<16&&r>=0&&r<(isDrum()?lanes():12)&&step<state.optInt("length")){int n=note(r);if(n>127)return true;boolean enabled=on(step,n);JSONArray notes=state.optJSONArray("steps").optJSONArray(step);
                        if(e.getEventTime()-downTime>=500)panels.step(step,n);
                        else if(!enabled&&!isDrum()&&notes.optInt(3)>=0)Toast.makeText(getContext(),"Four notes per step — remove one first",Toast.LENGTH_SHORT).show();
                        else if(enabled){int owner=step;while(!isDrum()&&owner>0&&state.optJSONArray("detail").optJSONObject(owner).optInt("time")==1)owner--;Native.event(Native.STEP,selected*state.optInt("stepStride",8192)+owner*128+n,0);}
                        else Native.event(Native.STEP,selected*state.optInt("stepStride",8192)+step*128+n,1);performClick();}}
                dragStep=-1;return true;}
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
    final class Keyboard extends View {
        final int[] white={0,2,4,6,7,9,11,12,14,16,18,19,21,23,24,26},black={1,3,5,8,10,13,15,17,20,22,25};
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final android.util.SparseIntArray touches=new android.util.SparseIntArray();final int[] counts=new int[27],owners=new int[27];
        Keyboard(Context c){super(c);Arrays.fill(owners,-1);p.setTypeface(InstrumentStyle.font(c));setContentDescription("Multitouch performance keyboard; drums use "+definition.drumLanes+" labeled pads");}
        int whiteCount(){return getWidth()<dp(500)?8:16;}
        RectF box(int key){
            int visible=isDrum()&&!definition.drumKeysPhysical?lanes():16;int count=Math.min(whiteCount(),visible),offset=isDrum()||count==16?0:keyBank;float w=getWidth()/(float)count;
            for(int i=0;i<white.length;i++)if(white[i]==key){
                if(i>=visible)return new RectF();
                if(!isDrum()&&(i<offset||i>=offset+count))return new RectF();
                float h=(getHeight()-dp(6))/(isDrum()?(visible+count-1)/count:1f),top=dp(6)+(i/count)*h;
                return new RectF(((i-offset+count)%count)*w+1,top+1,((i-offset+count)%count+1)*w-1,top+h-1);
            }
            int i=0;while(i<white.length&&white[i]<key)i++;if(i<offset||i>=offset+count)return new RectF();i-=keyBank;
            return new RectF(i*w-w*.3f,dp(6),i*w+w*.3f,getHeight()*.60f);
        }
        @Override protected void onDraw(Canvas c){for(int k:white)drawKey(c,k,false);if(!isDrum())for(int k:black)drawKey(c,k,true);}
        void drawKey(Canvas c,int k,boolean dark){RectF b=box(k);if(b.isEmpty())return;p.setColor((counts[k]>0||state!=null&&(state.optInt("sounding")&(1<<k))!=0)?GREEN:dark?0xff111612:0xffdce3dd);c.drawRoundRect(b,dp(3),dp(3),p);if(!dark){p.setColor(0xff47544a);p.setTextSize(dp(10));p.setTextAlign(Paint.Align.CENTER);int i=0;while(white[i]!=k)i++;c.drawText(isDrum()&&state!=null?state.optJSONArray("lanes").optString(i):Integer.toString(i+1),b.centerX(),b.bottom-dp(7),p);}}
        int hit(float x,float y){if(x<0||x>=getWidth()||y<0||y>=getHeight())return -1;if(!isDrum())for(int k:black)if(box(k).contains(x,y))return k;for(int k:white)if(box(k).contains(x,y))return k;return -1;}
        void sendKey(int key,int down){if(down!=0){owners[key]=-1;if(isDrum()&&!definition.drumKeysPhysical){int lane=0;while(lane<white.length&&white[lane]!=key)lane++;if(lane<definition.drumNotes.length)owners[key]=selected*128+definition.drumNotes[lane];}}if(owners[key]>=0)Native.event(Native.NOTE,owners[key],down!=0?100:0);else Native.event(Native.KEY,key,down);if(down==0)owners[key]=-1;}
        void release(int pointer){int k=touches.get(pointer,-1);if(k>=0){if(--counts[k]==0)sendKey(k,0);touches.delete(pointer);}}
        void press(int pointer,int k){if(k<0)return;touches.put(pointer,k);if(counts[k]++==0)sendKey(k,1);}
        void releaseAll(){while(touches.size()>0)release(touches.keyAt(0));invalidate();}
        @Override public boolean onTouchEvent(MotionEvent e){if(!active)return false;int a=e.getActionMasked(),i=e.getActionIndex(),id=e.getPointerId(i);if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN)press(id,hit(e.getX(i),e.getY(i)));else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP){release(id);performClick();}else if(a==MotionEvent.ACTION_CANCEL)releaseAll();else if(a==MotionEvent.ACTION_MOVE)for(int j=0;j<e.getPointerCount();j++){int pointer=e.getPointerId(j),key=hit(e.getX(j),e.getY(j));if(touches.get(pointer,-1)!=key){release(pointer);press(pointer,key);}}invalidate();return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
