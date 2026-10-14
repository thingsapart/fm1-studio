/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;
import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;

public final class MainActivity extends Activity {
    private StudioView instrument;
    private LinearLayout layout;
    private Definitions.Firmware firmware;
    private MidiInputs midi;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private AudioManager audio;
    private AudioFocusRequest focus;
    private boolean resumed, started,hasAudioFocus;
    private int sampleSlot;
    private int objectKind,objectId,wavMode;
    private byte[] exportData;
    private File storage;
    private final Runnable update=new Runnable(){public void run(){if(!resumed)return;instrument.setStatus(Native.status());handler.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);Fullscreen.configure(getWindow());
        String id=getSharedPreferences("wrapper",0).getString("firmware","sloop");
        firmware=Definitions.ALL[0];for(Definitions.Firmware f:Definitions.ALL)if(f.id.equals(id))firmware=f;
        Definitions.active=firmware;
        String failure=Native.select(getApplicationInfo().nativeLibraryDir+"/libfm1_"+firmware.id+".so",firmware.id,getFilesDir().getAbsolutePath());
        if(failure!=null){toast(failure);finish();return;}
        storage=new File(getFilesDir(),firmware.id+".flash");
        layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);layout.setBackgroundColor(0xff101216);
        instrument=new StudioView(this,()->menu());layout.addView(instrument,new LinearLayout.LayoutParams(-1,0,1));setContentView(layout);layout.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets caption=insets.getInsets(WindowInsets.Type.captionBar());view.setPadding(caption.left,caption.top,caption.right,caption.bottom);}
            instrument.windowInsets(insets);return insets;
        });layout.requestApplyInsets();Fullscreen.hide(getWindow());
        controllerPadBank=Math.max(0,Math.min(2,getSharedPreferences("jp1",0).getInt("lastPadBank",0)));controllerPadMode=Math.max(0,Math.min(2,getSharedPreferences("jp1",0).getInt("padMode",0)));midi=new MidiInputs(this);midi.restorePreferences();audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(change->{
                if(change==AudioManager.AUDIOFOCUS_GAIN){hasAudioFocus=true;if(resumed)startSound();}
                else if(change<0){hasAudioFocus=false;stopSound();}
            },handler).build();
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)Fullscreen.hide(getWindow());}
    private void startSound(){if(started||!resumed||!hasAudioFocus)return;Native.start();started=true;Native.event(5,0,getPreferences(0).getInt("master",2048));instrument.resume();}
    private void stopSound(){if(instrument!=null)instrument.pause();if(!started)return;Native.stop();started=false;}
    @Override protected void onResume(){super.onResume();resumed=true;midi.resume();hasAudioFocus=audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;if(hasAudioFocus)startSound();handler.post(update);if(pendingEditor!=null){SampleEditor editor=pendingEditor;pendingEditor=null;editor.show();}}
    @Override protected void onPause(){resumed=false;instrument.saveDisplayZoom();handler.removeCallbacks(update);midi.pause();stopSound();hasAudioFocus=false;audio.abandonAudioFocusRequest(focus);super.onPause();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==71){boolean granted=true;for(int result:results)granted&=result==android.content.pm.PackageManager.PERMISSION_GRANTED;if(granted)midi.scanBluetooth();else toast("Bluetooth permissions are needed to discover MIDI pads");}
    }
    @Override public android.content.SharedPreferences getPreferences(int mode){return getSharedPreferences("instrument-"+(firmware==null?"sloop":firmware.id),mode);}
    String[] sampleNames(){String[] a=new String[firmware.studio.sampleSlots];for(int i=0;i<a.length;i++)a[i]="USR"+(i+1);return a;}
    void chooseFirmware(){String[] names=new String[Definitions.ALL.length];int current=0;for(int i=0;i<names.length;i++){names[i]=Definitions.ALL[i].name;if(Definitions.ALL[i]==firmware)current=i;}
        instrument.picker("FIRMWARE",names,current,false,index->{Definitions.Firmware next=Definitions.ALL[index];if(next==firmware)return;
            instrument.pause();midi.pause();Native.stop();started=false;
            String error=Native.select(getApplicationInfo().nativeLibraryDir+"/libfm1_"+next.id+".so",next.id,getFilesDir().getAbsolutePath());
            if(error!=null){toast(error);midi.resume();if(resumed)startSound();return;}
            pendingEditor=null;firmware=next;Definitions.active=next;storage=new File(getFilesDir(),next.id+".flash");getSharedPreferences("wrapper",0).edit().putString("firmware",next.id).apply();
            layout.removeView(instrument);instrument=new StudioView(this,()->menu());layout.addView(instrument,new LinearLayout.LayoutParams(-1,0,1));layout.requestApplyInsets();midi.restorePreferences();if(resumed){midi.resume();startSound();}
        });
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    boolean controllerDrum(int track){return instrument.isDrum(track);}
    private int controllerTempo;
    private long controllerTempoAt;
    private int controllerPadMode=Jp1Pads.AUTO,controllerPadOctave;
    private int controllerPadBank,controllerPadBase=52;
    private boolean controllerConnected,controllerBankLive;
    private int controllerLiveKnobBank;
    private long controllerKnobObservedAt,controllerPadObservedAt;
    void controllerConnection(boolean connected){controllerConnected=connected;instrument.controllerConnection(connected);if(connected)instrument.controllerPadStatus(controllerPadMode,controllerPadBank,controllerPadOctave);}
    int controllerPadBase(){return controllerPadBase;}
    void controllerFeedback(int[] colors,boolean repaint){if(midi!=null)midi.controllerFeedback(colors,repaint);}
    void controllerBank(int bank){
        // CC ranges identify the physical knob, but a programmed preset can reuse them
        // in another bank. Do not let that inference fight the vendor's bank report.
        if(controllerBankLive)bank=controllerLiveKnobBank;
        else controllerKnobObservedAt=SystemClock.uptimeMillis();
        instrument.controllerBank(bank);
    }
    void controllerBankStatus(boolean live){controllerBankLive=live;instrument.controllerBankStatus(live);}
    void controllerBanks(int pad,int knob,long requestedAt){
        if(!controllerConnected)return;
        controllerLiveKnobBank=knob;
        controllerBankStatus(true);
        // A query can describe state sampled before a newer MIDI event reached us.
        if(controllerKnobObservedAt<=requestedAt)instrument.controllerReportedKnobBank(knob);
        if(controllerPadObservedAt<=requestedAt){
            if(controllerPadBank!=pad)getSharedPreferences("jp1",0).edit().putInt("lastPadBank",pad).apply();
            controllerPadBank=pad;instrument.controllerPadStatus(controllerPadMode,pad,controllerPadOctave);
        }
    }
    void controllerPadModeChanged(){if(midi!=null)midi.releaseJpNotes();instrument.clearControllerPiano();instrument.controllerPadStatus(controllerPadMode,controllerPadBank,controllerPadOctave);}
    int controllerPadTarget(int note,int velocity,int middleBase){
        if(instrument.state==null)return -1;
        int pad=Jp1Pads.lane(note,middleBase),sourceBank=Jp1Pads.bank(note,middleBase);
        if(sourceBank>=0&&!controllerBankLive){controllerPadObservedAt=SystemClock.uptimeMillis();if(controllerPadBank!=sourceBank)getSharedPreferences("jp1",0).edit().putInt("lastPadBank",sourceBank).apply();controllerPadBank=sourceBank;controllerPadBase=middleBase;}
        boolean plays=instrument.controllerPadsPlay(controllerPadMode);
        instrument.controllerPadStatus(controllerPadMode,controllerPadBank,controllerPadOctave);
        if(!plays){int step=pad<0?-1:instrument.controllerStepWindow()*16+pad;if(step>=0)instrument.controllerStep(step);return -1;}
        boolean drum=instrument.isDrum();int shift=Jp1Pads.octaveControl(pad,true,drum);
        if(shift!=0){controllerPadOctave=Math.max(-3,Math.min(3,controllerPadOctave+shift));controllerPadModeChanged();return -1;}
        int pitch=Jp1Pads.pitch(note,controllerPadOctave);
        if(drum&&pad>=0){int[] notes=firmware.studio.drumNotes;if(pad>=notes.length)return -1;pitch=notes[pad];instrument.controllerSound(pad,pitch);}
        else instrument.controllerSound(-1,pitch);
        return instrument.selected*128+pitch;
    }
    void controllerPadSettings(){new AlertDialog.Builder(this).setTitle("JP-1 pad mode").setSingleChoiceItems(new String[]{"Auto · play kit overview / keyboard; steps elsewhere","Play · notes / drum sounds","Steps · toggle the selected sound's pattern"},controllerPadMode,(dialog,n)->{controllerPadMode=n;getSharedPreferences("jp1",0).edit().putInt("padMode",n).apply();controllerPadModeChanged();dialog.dismiss();}).show();}
    void controllerAbsoluteKnob(int knob,int value){if(controllerBankLive)knob=controllerLiveKnobBank*8+knob%8;instrument.controllerAbsoluteKnob(knob,value);}
    void controllerKnob(int knob,int delta){
        if(!instrument.controllerKnobsReady())return;
        int bank=knob/8;knob%=8;
        long now=SystemClock.uptimeMillis();
        if(knob<3)instrument.controllerAssignedKnob(knob,Integer.signum(delta));
        else if(knob==3){if(now-controllerTempoAt>250)controllerTempo=instrument.state.optInt("bpm");controllerTempoAt=now;controllerTempo=Math.max(40,Math.min(240,controllerTempo+delta));Native.event(Native.GLOBAL,0,controllerTempo);}
        else instrument.controllerAssignedKnob(knob,delta);
    }
    void controllerNote(int target,int velocity){instrument.controllerNote(target,velocity);}
    private int heldDirection;
    private final Runnable heldNavigation=new Runnable(){public void run(){if(!resumed||heldDirection==0)return;instrument.controllerPage(heldDirection);handler.postDelayed(this,250);}};
    void clearControllerNotes(){heldDirection=0;handler.removeCallbacks(heldNavigation);if(instrument!=null)instrument.clearControllerNotes();}
    void controllerHeld(int control,boolean down){
        if(control<2){int direction=control==0?-1:1;if(down){if(heldDirection!=direction){heldDirection=direction;handler.removeCallbacks(heldNavigation);handler.post(heldNavigation);}}else if(heldDirection==direction){heldDirection=0;handler.removeCallbacks(heldNavigation);}return;}
        if(control==2){if(firmware.studio.has("repeat"))Native.event(Native.PERFORM,31,down?1:0);return;}
        if(!down)return;
        if(control==3)instrument.toggleControllerKeys();
        if(control==4&&instrument.state!=null&&instrument.state.optInt("recordState")==0&&instrument.state.optInt("playing")==0)Native.event(Native.ACTION,instrument.selected*256+3,0);
    }
    void controllerButton(int action){
        org.json.JSONObject state=instrument.state;if(state==null)return;int track=instrument.selected;
        if(action==0||action==6)Native.event(Native.TRANSPORT,0,action==6?1:state.optInt("recordState")==3?0:state.optInt("playing")==0?1:0);
        else if(action==1){Native.event(Native.TRANSPORT,0,0);Native.event(4,0,0);clearControllerNotes();}
        else if(action==2||action==7){if(state.optInt("recordState")==3){if(action==2)instrument.panels.action(42,0);}else Native.event(Native.RECORD,track,action==7?0:(state.optInt("recording")&(1<<track))==0?1:0);}
        else if(action==3||action==4){if(state.optInt("recordState")==3){toast("Finish or discard the take first");return;}Native.event(Native.SELECT,Math.floorMod(track+(action==3?-1:1),firmware.studio.tracks.length),0);}
        else if(action==5)Native.event(Native.MUTE,track,state.optJSONArray("tracks").optJSONObject(track).optInt("mute")==0?1:0);
    }
    private void menu(){
        instrument.releaseAll();
        String[] allItems=firmware.studio.sheet("files");
        java.util.ArrayList<String> visible=new java.util.ArrayList<>();java.util.ArrayList<Integer> operations=new java.util.ArrayList<>();
        for(int i=0;i<allItems.length;i++){if(i==7&&firmware.studio.sampleSlots==0)continue;visible.add(allItems[i]);operations.add(i);}
        String[] items=visible.toArray(new String[0]);
        instrument.picker(firmware.name+" / SETTINGS",items,-1,false,n->{
            switch(operations.get(n)){
            case 0:midi.chooseUsb();break;
            case 1:midi.scanBluetooth();break;
            case 2:midi.routing();break;
            case 3:midi.disconnectInput();break;
            case 4:volume();break;
            case 5:pick(81,true,firmware.id+"-android.zip");break;
            case 6:pick(82,false,null);break;
            case 7:instrument.picker("FSMP SAMPLE SLOT",sampleNames(),-1,false,slot->{sampleSlot=slot;pick(83,false,null);});break;
            case 8:new AlertDialog.Builder(this).setMessage(Native.status()+"\nEngine: 44.1 kHz. Output rate conversion: linear interpolation. Playback stops when the app leaves the foreground.").setPositiveButton("OK",null).show();break;
            case 9:midi.encoderSettings();break;case 10:midi.chooseOutput();break;case 11:midi.disconnectOutput();break;
            case 12:handler.post(()->midi.jpSettings());break;
            }
        });
    }
    void exportObject(int kind,int id,String name){stopSound();exportData=Native.object(kind,id);if(exportData==null){toast("This slot is empty");if(resumed)startSound();return;}if(kind==5&&exportData.length==4096){byte[] syx=new byte[4104];syx[0]=(byte)240;syx[1]=67;syx[3]=9;syx[4]=32;System.arraycopy(exportData,0,syx,6,4096);int sum=0;for(byte b:exportData)sum+=b&127;syx[4102]=(byte)((-sum)&127);syx[4103]=(byte)247;exportData=syx;}pick(84,true,name);}
    void exportMidi(){org.json.JSONObject s=instrument.state;int length=s.optInt("length");boolean drums=instrument.isDrum();int[][] notes=new int[length][4],levels=new int[length][drums?firmware.studio.drumLanes:4],ratchets=new int[length][drums?firmware.studio.drumLanes:4];int[] duration=new int[length],velocity=new int[length],nudge=new int[length];for(int i=0;i<length;i++){org.json.JSONObject d=s.optJSONArray("detail").optJSONObject(i);for(int j=0;j<4;j++)notes[i][j]=s.optJSONArray("steps").optJSONArray(i).optInt(j);for(int j=0;j<levels[i].length;j++){levels[i][j]=d.optJSONArray("levels").optInt(j);ratchets[i][j]=d.optJSONArray("ratchets").optInt(j);}duration[i]=d.optInt("length");velocity[i]=d.optInt("flags")%2==1?127:Math.max(1,d.optInt("velocity"));nudge[i]=d.optInt("nudge");}exportData=MidiFile.write(notes,duration,velocity,nudge,levels,ratchets,length,s.optInt("division"),s.optInt("bpm"),drums,firmware.studio.drumNotes,firmware.studio.nudgeMax-firmware.studio.nudgeMin+1);pick(84,true,firmware.id+"-track"+(instrument.selected+1)+".mid");}
    void importMidi(){objectId=instrument.selected;objectKind=6;pick(87,false,null);}
    void importObject(int kind,int id){objectKind=kind;objectId=id;pick(85,false,null);}
    void importWavs(int slot,int mode){sampleSlot=slot;wavMode=mode;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*");intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,mode!=1);startActivityForResult(intent,86);}
    private boolean wavLoading;
    private SampleEditor pendingEditor;
    private void loadWavs(Intent result)throws IOException{
        if(wavLoading){toast("A recording is already loading");return;}
        java.util.ArrayList<android.net.Uri> uris=new java.util.ArrayList<>();if(result.getClipData()!=null)for(int i=0;i<result.getClipData().getItemCount();i++)uris.add(result.getClipData().getItemAt(i).getUri());else uris.add(result.getData());if(uris.size()>(wavMode==2?firmware.studio.drumLanes:16))throw new IOException("Choose at most "+(wavMode==2?firmware.studio.drumLanes:16)+" WAVs");final Definitions.Firmware loadingFirmware=firmware;final int slot=sampleSlot,mode=wavMode;wavLoading=true;toast("Loading WAVs…");
        new Thread(()->{try{java.util.ArrayList<SampleCodec.Zone> zones=new java.util.ArrayList<>();int[] drums=firmware.studio.drumNotes;for(int i=0;i<uris.size();i++){byte[] bytes=readLimited(getContentResolver().openInputStream(uris.get(i)),32000000);SampleCodec.Zone z=new SampleCodec.Zone(SampleCodec.wav(bytes),"SOUND "+(i+1));z.root=mode==2?drums[i]:Math.min(127,48+i*4);if(mode==2)z.lo=z.hi=z.root;zones.add(z);}handler.post(()->{wavLoading=false;if(isFinishing()||isDestroyed())return;if(firmware!=loadingFirmware){toast("Firmware changed; sample import cancelled");return;}SampleEditor editor=new SampleEditor(instrument.panels,slot,mode,zones);if(resumed)editor.show();else pendingEditor=editor;});}catch(IOException|RuntimeException e){handler.post(()->{wavLoading=false;toast(e.getMessage());});}},"sloop-wav-import").start();
    }
    private byte[] patchData(byte[] bytes)throws IOException{if(bytes.length==128||bytes.length==155)return bytes;if(bytes.length==163&&(bytes[0]&255)==240&&(bytes[1]&255)==67&&bytes[3]==0&&bytes[4]==1&&bytes[5]==27&&(bytes[162]&255)==247){int sum=0;for(int i=6;i<161;i++)sum+=bytes[i]&127;if(((sum+(bytes[161]&127))&127)!=0)throw new IOException("SysEx checksum failed");return java.util.Arrays.copyOfRange(bytes,6,161);}throw new IOException("Choose a 128-byte packed FM6 patch or a DX7 single-voice SysEx");}
    private byte[] backup()throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(bytes)){zip.putNextEntry(new java.util.zip.ZipEntry(firmware.id+".flash"));Files.copy(storage.toPath(),zip);zip.closeEntry();org.json.JSONObject preferences=new org.json.JSONObject(getPreferences(0).getAll());zip.putNextEntry(new java.util.zip.ZipEntry("android-settings.json"));zip.write(preferences.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}return bytes.toByteArray();}
    private void restoreBackup(byte[] data)throws IOException{byte[] image=data;org.json.JSONObject preferences=null;if(data.length>=4&&data[0]=='P'&&data[1]=='K'){image=null;try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(data))){java.util.zip.ZipEntry entry;int entries=0;while((entry=zip.getNextEntry())!=null){if(++entries>2||(!entry.getName().equals(firmware.id+".flash")&&!entry.getName().equals("android-settings.json")))throw new IOException("Unexpected backup entry");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;int limit=entry.getName().equals(firmware.id+".flash")?1048576:65536;while((n=zip.read(block))!=-1){if(out.size()+n>limit)throw new IOException("Backup entry too large");out.write(block,0,n);}if(entry.getName().equals(firmware.id+".flash"))image=out.toByteArray();if(entry.getName().equals("android-settings.json"))try{preferences=new org.json.JSONObject(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));}catch(org.json.JSONException e){throw new IOException("Invalid Android settings",e);}zip.closeEntry();}}}
      if(preferences!=null){int master=preferences.optInt("master",2048),routing=preferences.optInt("routing",0),pad=preferences.optInt("padBase",-1);if(master<0||master>4096||routing<0||routing>2||pad<-1||pad>112)throw new IOException("Invalid backup preferences");try{new MidiEncoders.Mapping(preferences.optInt("encoderMode",0),preferences.optInt("encoderBase",20),preferences.optInt("encoderChannel",0));}catch(IllegalArgumentException e){throw new IOException("Invalid MIDI encoder preferences",e);}}
      if(image==null||image.length!=1048576)throw new IOException("Expected a backup for the selected firmware");stopSound();if(!Native.restore(image))throw new IOException("Backup validation failed; previous storage retained");if(preferences!=null){android.content.SharedPreferences.Editor edit=getPreferences(0).edit();for(String key:new String[]{"master","routing","padBase","encoderMode","encoderBase","encoderChannel"})if(preferences.has(key))edit.putInt(key,preferences.optInt(key));edit.apply();for(int i=0;i<4;i++){String key="sectionName"+i;if(preferences.has(key))edit.putString(key,preferences.optString(key));}edit.apply();midi.restorePreferences();}toast("Backup restored");}
    private void volume(){instrument.numberEditor("MASTER VOLUME",getPreferences(0).getInt("master",2048),0,4096,value->{Native.event(5,0,value);getPreferences(0).edit().putInt("master",value).apply();});}
    private void pick(int request,boolean create,String name){
        Intent intent=new Intent(create?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream");
        if(name!=null)intent.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(intent,request);
    }
    private byte[] readLimited(InputStream input,int maximum)throws IOException{
        if(input==null)throw new IOException("Could not open file");
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>maximum)throw new IOException("File is too large");out.write(b,0,n);}return out.toByteArray();
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||(data.getData()==null&&data.getClipData()==null))return;
        try{
            if(request==81){stopSound();try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new IOException("Could not create file");out.write(backup());}toast("Android backup exported");}
            else if(request==82){restoreBackup(readLimited(getContentResolver().openInputStream(data.getData()),1200000));}
            else if(request==83){byte[] sample=readLimited(getContentResolver().openInputStream(data.getData()),81920);if(resumed&&!started)startSound();Native.sample(sampleSlot,sample);toast("Sample queued; check audio diagnostics for result");}
            else if(request==84){try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null||exportData==null)throw new IOException("Could not export file");out.write(exportData);}exportData=null;toast("File exported");}
            else if(request==85){byte[] bytes=readLimited(getContentResolver().openInputStream(data.getData()),81920);if(objectKind==1)bytes=patchData(bytes);if(objectKind==5){if(bytes.length==4104&&(bytes[0]&255)==240&&(bytes[1]&255)==67&&bytes[3]==9&&bytes[4]==32&&bytes[5]==0&&(bytes[4103]&255)==247){int sum=0;for(int i=6;i<4102;i++)sum+=bytes[i]&127;if(((sum+(bytes[4102]&127))&127)!=0)throw new IOException("Cartridge checksum failed");bytes=java.util.Arrays.copyOfRange(bytes,6,4102);}if(bytes.length!=4096)throw new IOException("Choose a DX7 32-voice cartridge");}if(resumed&&!started)startSound();Native.data(objectKind,objectId,bytes);toast("Import queued; check diagnostics for result");}
            else if(request==86){loadWavs(data);}
            else if(request==87){java.util.List<MidiFile.Track> tracks=MidiFile.read(readLimited(getContentResolver().openInputStream(data.getData()),8000000));String[] names=new String[tracks.size()];for(int i=0;i<names.length;i++)names[i]=tracks.get(i).name+" · "+tracks.get(i).notes.size()+" NOTES";final int target=objectId;instrument.picker("MIDI TRACK",names,-1,false,i->{MidiFile.Track track=tracks.get(i);instrument.panels.confirm("IMPORT MIDI",track.notes.size()+" notes. Quantize to this track's division, keep at most four notes per step and crop after step "+instrument.state.optInt("maxSteps",64)+". Replace this pattern?",()->Native.data(6,target,MidiFile.pattern(track,instrument.state.optInt("division"),instrument.isDrum(target),firmware.studio.drumNotes,instrument.state.optInt("maxSteps",64))));});}
        }catch(IOException|RuntimeException e){toast(e.getMessage());}
        if(resumed&&!started)startSound();
    }
}
