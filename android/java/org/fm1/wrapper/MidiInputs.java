/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.*;
import android.content.pm.PackageManager;
import android.media.midi.*;
import android.os.*;
import android.widget.Toast;
import java.io.IOException;
import java.util.*;

final class MidiInputs {
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MidiManager manager;
    private final Jp1BleLights jpLights;
    private MidiDevice device,outputDevice;private MidiInputPort output;private int outputGeneration;
    private final Runnable sendOutput=new Runnable(){public void run(){if(!active)return;int[] packets=Native.midi();if(output!=null)try{for(int packet:packets){int status=(packet>>>8)&255;byte[] bytes={(byte)status,(byte)(packet>>>16),(byte)(packet>>>24)};int count=status>=0xf8?1:(status&0xf0)==0xc0||(status&0xf0)==0xd0?2:3;output.send(bytes,0,count);}}catch(IOException e){disconnectOutput();message("MIDI output disconnected");}handler.postDelayed(this,10);}};
    void disconnectOutput(){outputGeneration++;if(output!=null)try{for(int ch=0;ch<16;ch++)output.send(new byte[]{(byte)(0xb0|ch),123,0},0,3);output.close();}catch(IOException ignored){}output=null;if(outputDevice!=null)try{outputDevice.close();}catch(IOException ignored){}outputDevice=null;}
    void chooseOutput(){if(manager==null)return;java.util.ArrayList<MidiDeviceInfo> devices=new java.util.ArrayList<>();java.util.ArrayList<String> names=new java.util.ArrayList<>();for(MidiDeviceInfo d:manager.getDevices())if(d.getInputPortCount()>0){devices.add(d);names.add(d.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME,"MIDI output"));}if(devices.isEmpty()){message("Connect a MIDI output device first");return;}new AlertDialog.Builder(activity).setTitle("MIDI output destination").setItems(names.toArray(new String[0]),(dialog,i)->{disconnectOutput();final int request=outputGeneration;manager.openDevice(devices.get(i),opened->{if(opened==null)return;if(!active||request!=outputGeneration){try{opened.close();}catch(IOException ignored){}return;}Native.midi();outputDevice=opened;output=opened.openInputPort(0);if(output==null)message("MIDI output port unavailable");},handler);}).show();}

    private final List<MidiOutputPort> ports = new ArrayList<>();
    private BluetoothLeScanner scanner;
    private ScanCallback scan;
    private volatile int generation;
    private int scanGeneration;
    private volatile boolean active;
    private volatile int routing, padBase = -1;
    private volatile MidiEncoders.Mapping encoderMapping=new MidiEncoders.Mapping(0,20,0);
    private volatile Jp1Controller.Mapping jpMapping=Jp1Controller.Mapping.defaults();
    private volatile int learnControl=-1;
    private volatile boolean jpConnected;
    private AlertDialog learnDialog;
    private final int[] heldNotes=new int[16*128];
    private MidiInputPort jpLedOutput;
    private volatile boolean jpProbeRunning;
    private int jpProbeChannel,jpProbeSerial;
    private int[] jpProbeNotes=new int[0];
    private void closeJpLedOutput(){jpProbeSerial++;jpProbeRunning=false;if(jpLedOutput!=null)try{jpLedOutput.close();}catch(IOException ignored){}jpLedOutput=null;}
    void controllerFeedback(int[] colors,boolean repaint){
        if(!active||!jpConnected||jpProbeRunning||device==null)return;
        BluetoothDevice bluetooth=device.getInfo().getProperties().getParcelable(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE);
        if(bluetooth==null)return;jpLights.follow(bluetooth);jpLights.update(colors,repaint,activity.getSharedPreferences("jp1",0).getBoolean("ledFeedback",true));
    }
    private void ledSettings(){boolean enabled=activity.getSharedPreferences("jp1",0).getBoolean("ledFeedback",true);new AlertDialog.Builder(activity).setTitle("JP-1 automatic pad lights").setSingleChoiceItems(new String[]{"On · Steps / Drums / Piano follow pad mode","Off"},enabled?0:1,(dialog,n)->{activity.getSharedPreferences("jp1",0).edit().putBoolean("ledFeedback",n==0).apply();if(n!=0)jpLights.update(new int[18],false,false);dialog.dismiss();}).show();}
    void chooseJpLedTest(){
        new AlertDialog.Builder(activity).setTitle("JP-1 Bluetooth LED test").setItems(new String[]{"Channel 1 · pad note numbers (5 seconds)","Channel 1 · pad indices 0–15 (5 seconds)","Channel 1 · all note addresses (16 seconds)","Channel 10 · pad note numbers (previous test)","Vendor RGB · red, green, blue, off (10 seconds)","Vendor RGB · pad order, top-left to bottom-right (9 seconds)"},(dialog,mode)->testJpLeds(mode)).setNegativeButton("Cancel",null).show();
    }
    void testJpLeds(int mode){
        if(mode>=4){if(!jpConnected||device==null){message("Connect the JP-1 first");return;}BluetoothDevice bluetooth=device.getInfo().getProperties().getParcelable(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE);if(bluetooth==null){message("Connect the JP-1 via Bluetooth for this test");return;}releaseJpNotes();closeJpLedOutput();jpProbeRunning=true;message(mode==5?"Watch one white pad move left to right, top row to bottom row":"Watch the pads turn red, green, blue, then off");jpLights.test(bluetooth,()->jpProbeRunning=false,mode==5);return;}
        if(!jpConnected||device==null){message("Connect the JP-1 first");return;}
        releaseJpNotes();closeJpLedOutput();
        jpLights.close();jpLedOutput=device.openInputPort(0);
        if(jpLedOutput==null){message("The JP-1's MIDI receive port is unavailable; disconnect any MIDI output to it first");return;}
        int request=generation,serial=jpProbeSerial;jpProbeRunning=true;jpProbeChannel=mode==3?9:0;
        if(mode==0||mode==3){jpProbeNotes=new int[3];for(int bank=0;bank<3;bank++)jpProbeNotes[bank]=Jp1Pads.sourceNote(0,bank,jpMapping.padBase);}
        else {jpProbeNotes=new int[mode==1?16:128];for(int note=0;note<jpProbeNotes.length;note++)jpProbeNotes[note]=note;}
        final int[] notes=jpProbeNotes.clone();final int channel=jpProbeChannel;int duration;
        message("LED test · channel "+(channel+1)+": watch all pads; do not play during the test");
        // Standard note messages only; no guessed CCs or vendor preset/firmware commands.
        android.util.Log.d("FM1-LED","BEGIN mode="+mode+" channel="+(channel+1));
        if(mode==2){duration=16000;for(int i=0;i<128;i++){final int note=i;handler.postDelayed(()->{if(request!=generation||serial!=jpProbeSerial||!jpProbeRunning)return;sendJpLedTest(channel,note,true,33);},i*125);handler.postDelayed(()->{if(request!=generation||serial!=jpProbeSerial||!jpProbeRunning)return;sendJpLedTest(channel,note,false,0);},i*125+90);}}
        else {duration=4500;for(int flash=0;flash<6;flash++){final int value=new int[]{127,33,1}[flash%3];int delay=flash*750;
            handler.postDelayed(()->{if(request!=generation||serial!=jpProbeSerial||!jpProbeRunning)return;for(int note:notes)sendJpLedTest(channel,note,true,value);},delay);
            handler.postDelayed(()->{if(request!=generation||serial!=jpProbeSerial||!jpProbeRunning)return;for(int note:notes)sendJpLedTest(channel,note,false,0);},delay+350);
        }}
        handler.postDelayed(()->{if(request!=generation||serial!=jpProbeSerial)return;closeJpLedOutput();message("LED test finished · channel "+(channel+1));},duration);
    }
    private void sendJpLedTest(int channel,int note,boolean on,int velocity){if(jpLedOutput==null||note<0||note>127)return;try{byte[] bytes={(byte)((on?0x90:0x80)|channel),(byte)note,(byte)velocity};jpLedOutput.send(bytes,0,3);android.util.Log.d("FM1-LED","TX ch="+(channel+1)+" "+(on?"on":"off")+" note="+note+" velocity="+velocity);}catch(IOException e){closeJpLedOutput();message("Bluetooth MIDI send failed");}}
    private static final int[] DRUM_NOTES = {36,35,38,39,42,46,44,37,40,43,48,49,51,70,63,56};
    private final MidiManager.DeviceCallback callback = new MidiManager.DeviceCallback() {
        @Override public void onDeviceRemoved(MidiDeviceInfo info) {
            if (device != null && device.getInfo().getId() == info.getId()) disconnect();
            if(outputDevice!=null&&outputDevice.getInfo().getId()==info.getId())disconnectOutput();
        }
    };
    MidiInputs(Activity activity) {
        this.activity = activity;
        jpLights=new Jp1BleLights(activity);
        manager = (MidiManager) activity.getSystemService(Activity.MIDI_SERVICE);
        Arrays.fill(heldNotes,-1);
        restoreJpMapping();
    }
    void resume() { active = true;handler.removeCallbacks(sendOutput);handler.post(sendOutput); if (manager != null) manager.registerDeviceCallback(callback, handler); reconnectBluetooth(); }
    private static boolean isJp1(String name){return name!=null&&name.toUpperCase(java.util.Locale.ROOT).replace(" ","").contains("JP-1");}
    private void reconnectBluetooth(){
        if(!activity.getPreferences(0).getBoolean("bluetoothMidiReconnect",true))return;
        if(manager==null || (Build.VERSION.SDK_INT>=31&&activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED))return;
        BluetoothManager bm=(BluetoothManager)activity.getSystemService(Activity.BLUETOOTH_SERVICE);
        if(bm==null||bm.getAdapter()==null||!bm.getAdapter().isEnabled())return;
        String address=activity.getPreferences(0).getString("bluetoothMidi",null);
        for(BluetoothDevice d:bm.getAdapter().getBondedDevices())if(address!=null?address.equals(d.getAddress()):isJp1(d.getName())){connectBluetooth(d);break;}
    }
    private void connectBluetooth(BluetoothDevice selected){
        stopScan();disconnect();int request=generation;
        try { manager.openBluetoothDevice(selected,opened->{attach(opened,request);if(device==opened&&opened!=null)activity.getPreferences(0).edit().putString("bluetoothMidi",selected.getAddress()).putBoolean("bluetoothMidiReconnect",true).apply();},handler); }
        catch(SecurityException e){message("Bluetooth permission is required");}
    }
    void pause() { active = false;learnControl=-1;if(learnDialog!=null)learnDialog.dismiss();handler.removeCallbacks(sendOutput);disconnectOutput(); stopScan(); disconnect(); if (manager != null) manager.unregisterDeviceCallback(callback); }
    private void message(String s) { Toast.makeText(activity,s,Toast.LENGTH_LONG).show(); }
    void disconnect() {
        jpLights.stop();
        closeJpLedOutput();
        generation++;
        for (MidiOutputPort port : ports) try { port.close(); } catch (IOException ignored) {}
        ports.clear();
        if (device != null) try { device.close(); } catch (IOException ignored) {}
        device = null;jpConnected=false;((MainActivity)activity).controllerConnection(false);Arrays.fill(heldNotes,-1);((MainActivity)activity).clearControllerNotes(); Native.event(4,0,0); Native.event(6,0,0);
    }
    void releaseJpNotes(){for(int i=0;i<heldNotes.length;i++){int target=heldNotes[i];if(target>=0){Native.event(Native.NOTE,target,0);((MainActivity)activity).controllerNote(target,0);heldNotes[i]=-1;}}}
    void disconnectInput(){activity.getPreferences(0).edit().putBoolean("bluetoothMidiReconnect",false).apply();disconnect();}
    private void attach(MidiDevice opened, int request) {
        if (opened == null) { if (active && request == generation) message("MIDI connection failed"); return; }
        if (!active || request != generation) { try { opened.close(); } catch (IOException ignored) {} return; }
        device = opened;
        BluetoothDevice bluetooth=device.getInfo().getProperties().getParcelable(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE);
        jpConnected=isJp1(device.getInfo().getProperties().getString(MidiDeviceInfo.PROPERTY_NAME))||(bluetooth!=null&&isJp1(bluetooth.getName()));
        for (int i=0; i<device.getInfo().getOutputPortCount(); i++) {
            MidiOutputPort port = device.openOutputPort(i);
            if (port == null) continue;
            MidiEncoders encoders=new MidiEncoders((knob,delta)->Native.event(1,knob,delta));
            Jp1Controller controller=new Jp1Controller(new Jp1Controller.Sink(){
                private void run(Runnable r){handler.post(()->{if(active&&request==generation)r.run();});}
                public void button(int action){run(()->((MainActivity)activity).controllerButton(action));}
                public void bank(int bank){run(()->((MainActivity)activity).controllerBank(bank));}
                public void absolute(int knob,int value){run(()->((MainActivity)activity).controllerAbsoluteKnob(knob,value));}
                public void knob(int knob,int delta){if(jpMapping.mode!=1)run(()->((MainActivity)activity).controllerKnob(knob,delta));}
                public void held(int control,boolean down){run(()->((MainActivity)activity).controllerHeld(control,down));}
                public void note(int channel,int note,int velocity){run(()->{
                    int index=channel*128+note,target=heldNotes[index];
                    if(velocity==0){if(target>=0){Native.event(Native.NOTE,target,0);((MainActivity)activity).controllerNote(target,0);}heldNotes[index]=-1;return;}
                    if(target>=0){Native.event(Native.NOTE,target,0);((MainActivity)activity).controllerNote(target,0);}
                    target=((MainActivity)activity).controllerPadTarget(note,velocity,jpMapping.padBase);if(target<0)return;
                    heldNotes[index]=target;Native.event(Native.NOTE,target,velocity);((MainActivity)activity).controllerNote(target,velocity);
                });}
            });
            MidiParser parser = new MidiParser(packet -> {
                if(jpConnected){
                    if(jpProbeRunning)return;
                    if(learnControl>=0){learn(packet);return;}
                    if(controller.accept(packet,jpMapping))return;
                }
                if(encoders.accept(packet,encoderMapping)) return;
                int status = (packet >> 8) & 255;
                if (status < 0xf0) {
                    int route = routing;
                    if(route!=0){
                        Native.event(Native.GLOBAL,Definitions.active.studio.midiRouteParameter,route==1?1:0);
                        if(route==2){
                            if(Native.drumChannel<0||Native.drumChannel>15)return;
                            status=(status&0xf0)|Native.drumChannel;packet=(packet&~0xff00)|(status<<8);
                            int[] notes=Definitions.active.studio.drumNotes;int note=(packet>>16)&127,index=note-padBase;
                            if(padBase>=0&&index>=0&&index<notes.length&&((status&0xf0)==0x90||(status&0xf0)==0x80))packet=(packet&~0x7f0000)|(notes[index]<<16);
                        }
                    }
                }
                Native.event(3,0,packet);
            },bytes->{if(jpConnected){if(learnControl>=0)return;controller.mmc(bytes);}});
            port.connect(new MidiReceiver() {
                @Override public void onSend(byte[] data,int offset,int count,long timestamp) {
                    // Debug builds expose input for hardware verification without changing its routing.
                    if((activity.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0){StringBuilder bytes=new StringBuilder();for(int j=offset;j<offset+count;j++)bytes.append(String.format(java.util.Locale.ROOT,"%02X ",data[j]&255));android.util.Log.d("FM1-MIDI-RAW",bytes.toString());}
                    if (active && request == generation) parser.feed(data,offset,count);
                }
                @Override public void onFlush() { parser.reset(); encoders.reset();controller.reset();handler.post(()->{if(request==generation){Arrays.fill(heldNotes,-1);((MainActivity)activity).clearControllerNotes();Native.event(4,0,0);}}); }
            });
            ports.add(port);
        }
        ((MainActivity)activity).controllerConnection(jpConnected&&!ports.isEmpty());

        Native.event(6,0,ports.isEmpty()?0:1);
        message(ports.isEmpty()?"This MIDI device has no available output ports":"MIDI input connected");
    }
    void chooseUsb() {
        if (manager == null) { message("Android MIDI is unavailable on this device"); return; }
        MidiDeviceInfo[] all=manager.getDevices();
        List<MidiDeviceInfo> inputs=new ArrayList<>();
        List<String> names=new ArrayList<>();
        for (MidiDeviceInfo d:all) if (d.getOutputPortCount()>0) {
            inputs.add(d);
            names.add(d.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME,"MIDI device "+d.getId()));
        }
        if (inputs.isEmpty()) { message("Connect a USB MIDI controller with a data cable/OTG adapter, then try again"); return; }
        new AlertDialog.Builder(activity).setTitle("MIDI input").setItems(names.toArray(new String[0]),(dialog,which)->{
            disconnect(); int request=generation;
            manager.openDevice(inputs.get(which),opened->attach(opened,request),handler);
        }).setNegativeButton("Cancel",null).show();
    }
    void routing() {
        new AlertDialog.Builder(activity).setTitle("MIDI routing").setSingleChoiceItems(
            new String[]{"Original channels (1–3 synths, 10 drums)","Selected track","Drums"},routing,(d,n)->{
                Native.event(4,0,0); routing=n;
                activity.getPreferences(0).edit().putInt("routing",n).apply(); d.dismiss();
            }).setNeutralButton("Pad notes",(d,n)->padMapping()).show();
    }
    void restorePreferences() {
        routing=activity.getPreferences(0).getInt("routing",0);
        padBase=activity.getPreferences(0).getInt("padBase",-1);
        encoderMapping=new MidiEncoders.Mapping(activity.getPreferences(0).getInt("encoderMode",0),
            activity.getPreferences(0).getInt("encoderBase",20),activity.getPreferences(0).getInt("encoderChannel",0));
    }
    private void restoreJpMapping(){
        android.content.SharedPreferences prefs=activity.getSharedPreferences("jp1",0);
        int[] bindings=Jp1Controller.Mapping.defaults().bindings.clone();for(int i=0;i<bindings.length;i++)bindings[i]=prefs.getInt("control"+i,bindings[i]);
        int toggle=prefs.getInt("toggleButtons",5);if(!prefs.contains("pulseButtons"))toggle&=7;
        try{jpMapping=new Jp1Controller.Mapping(bindings,prefs.getInt("mode",1),prefs.getInt("pad",52),toggle,prefs.getInt("pulseButtons",56),new int[]{0xb02e,prefs.getInt("bankB",0xb01e),prefs.getInt("bankC",0xb026)});}
        catch(IllegalArgumentException e){jpMapping=Jp1Controller.Mapping.defaults();}
    }
    private void saveJpMapping(){android.content.SharedPreferences.Editor edit=activity.getSharedPreferences("jp1",0).edit();for(int i=0;i<14;i++)edit.putInt("control"+i,jpMapping.bindings[i]);edit.putInt("mode",jpMapping.mode).putInt("pad",jpMapping.padBase).putInt("toggleButtons",jpMapping.toggleButtons).putInt("pulseButtons",jpMapping.pulseButtons).putInt("bankB",jpMapping.bankFirst[1]).putInt("bankC",jpMapping.bankFirst[2]).apply();}
    private void learn(int packet){
        int control=learnControl;if(control<0||!Jp1Controller.learnable(packet,(control>=6&&control<14)||control>=15))return;
        if(control==14&&(((packet>>>8)&240)!=144||((packet>>>16)&127)>112))return;
        if(control>=15&&((packet>>>16)&127)>120)return;
        learnControl=-1;
        handler.post(()->{
            if(learnDialog==null||!learnDialog.isShowing())return;
            int[] bindings=jpMapping.bindings.clone(),banks=jpMapping.bankFirst.clone();int pad=jpMapping.padBase;
            if(control>=15)banks[control-14]=Jp1Controller.binding(packet);
            else if(control==14)pad=(packet>>>16)&127;
            else{int key=Jp1Controller.binding(packet);for(int i=0;i<14;i++)if(bindings[i]==key)bindings[i]=-1;bindings[control]=key;}
            jpMapping=new Jp1Controller.Mapping(bindings,jpMapping.mode,pad,jpMapping.toggleButtons,jpMapping.pulseButtons,banks);saveJpMapping();learnDialog.dismiss();jpSettings();
        });
    }
    void jpSettings(){
        String[] rows=new String[25];for(int i=0;i<15;i++)rows[i]=Jp1Controller.CONTROLS[i]+" · "+(i==14?"note "+jpMapping.padBase:bindingName(jpMapping.bindings[i]));
        rows[15]="Knob format · "+new String[]{"","Absolute 0–127","Relative 1 / 127","Relative 65 / 63","Relative 1 / 65"}[jpMapping.mode];
        rows[16]="Button press style (press-only / momentary / toggle)";rows[17]="Reset to tested JP-1 preset";rows[18]="Bank B · Sound · "+bindingName(jpMapping.bankFirst[1]);rows[19]="Bank C · Pattern · "+bindingName(jpMapping.bankFirst[2]);rows[20]="Pad mode · Auto / Play / Steps";rows[21]="Test Bluetooth pad LEDs";rows[24]="Bank LED slots · "+(activity.getSharedPreferences("jp1",0).getBoolean("swapBankLeds",false)?"16 pad / 17 knob":"16 knob / 17 pad");rows[23]="Step pads / LEDs follow playhead · "+(activity.getSharedPreferences("jp1",0).getBoolean("ledFollow",true)?"On":"Off");rows[22]="Automatic pad LED feedback · "+(activity.getSharedPreferences("jp1",0).getBoolean("ledFeedback",true)?"On":"Off");
        new AlertDialog.Builder(activity).setTitle("JP-1 controller mapping").setItems(rows,(d,i)->{
            if(i==24){boolean swap=activity.getSharedPreferences("jp1",0).getBoolean("swapBankLeds",false);activity.getSharedPreferences("jp1",0).edit().putBoolean("swapBankLeds",!swap).apply();jpSettings();return;}
            if(i==23){boolean enabled=activity.getSharedPreferences("jp1",0).getBoolean("ledFollow",true);new AlertDialog.Builder(activity).setTitle("JP-1 step window").setSingleChoiceItems(new String[]{"Follow playhead · lights and pad edits use active window","Fixed · pad banks select steps 1–16 / 17–32 / 33–48"},enabled?0:1,(dialog,n)->{activity.getSharedPreferences("jp1",0).edit().putBoolean("ledFollow",n==0).apply();dialog.dismiss();}).show();return;}
            if(i==22){ledSettings();return;}
            if(i==20){((MainActivity)activity).controllerPadSettings();return;}
            if(i==21){chooseJpLedTest();return;}
            if(i==15){new AlertDialog.Builder(activity).setTitle("JP-1 knob format").setSingleChoiceItems(new String[]{"Absolute 0–127","Relative 1 up / 127 down","Relative 65 up / 63 down","Relative 1 up / 65 down"},jpMapping.mode-1,(dialog,n)->{jpMapping=new Jp1Controller.Mapping(jpMapping.bindings,n+1,jpMapping.padBase,jpMapping.toggleButtons,jpMapping.pulseButtons,jpMapping.bankFirst);saveJpMapping();dialog.dismiss();jpSettings();}).show();return;}
            if(i==16){new AlertDialog.Builder(activity).setTitle("Choose a button").setItems(Arrays.copyOf(Jp1Controller.CONTROLS,6),(dialog,button)->{int bit=1<<button;int style=(jpMapping.toggleButtons&bit)!=0?2:(jpMapping.pulseButtons&bit)!=0?0:1;new AlertDialog.Builder(activity).setTitle(Jp1Controller.CONTROLS[button]).setSingleChoiceItems(new String[]{"Press-only · every positive message","Momentary · press/release pair","Toggle · alternate values per press"},style,(choice,n)->{int toggle=jpMapping.toggleButtons&~bit,pulse=jpMapping.pulseButtons&~bit;if(n==2)toggle|=bit;if(n==0)pulse|=bit;jpMapping=new Jp1Controller.Mapping(jpMapping.bindings,jpMapping.mode,jpMapping.padBase,toggle,pulse,jpMapping.bankFirst);saveJpMapping();choice.dismiss();jpSettings();}).show();}).show();return;}
            if(i==17){jpMapping=Jp1Controller.Mapping.defaults();saveJpMapping();jpSettings();return;}
            if(!jpConnected){message("Connect the JP-1 first");return;}
            Native.event(4,0,0);((MainActivity)activity).clearControllerNotes();Arrays.fill(heldNotes,-1);learnControl=i>=18?i-3:i;
            learnDialog=new AlertDialog.Builder(activity).setTitle(i>=18?rows[i]:Jp1Controller.CONTROLS[i]).setMessage(i>=18?"Select this knob bank and turn its top-left knob. Eight consecutive CCs will control this bank.":i>=6&&i<14?"Turn this knob. The first CC will be assigned.":i==14?"Tap the BOTTOM-LEFT pad in the middle note bank (notes 52–67 in the tested preset). The three consecutive 16-note banks map to the same 4×4 drum layout.":"Press this button. Note or CC messages can be learned. MMC transport commands work automatically.").setNegativeButton("Cancel",null).create();
            learnDialog.setOnDismissListener(dialog->learnControl=-1);learnDialog.show();
        }).setNeutralButton("Help",(d,n)->new AlertDialog.Builder(activity).setTitle("JP-1 mapping help").setMessage("Tap a control to learn its MIDI message. Pads play the selected track with velocity. Assignments apply in all firmwares. Use the JP-1 bank you will play. All banks retain SELECT, TRACK, PRESET and TEMPO above four context controls. A follows the current page, B shows Sound, C shows Pattern. The first knob turn reveals its bank; a silent bank switch cannot be detected. Hold < or > to browse pages, hold Play for Repeat, hold Stop to show/hide the keyboard, and hold Rec for undo while stopped. Auto pad mode plays notes/drums with the keyboard shown and toggles steps when hidden. Synth play reserves bottom-left/top-right for octave down/up. Press-only buttons send identical positive CC values; momentary buttons send press/release pairs; toggle buttons alternate values.").setPositiveButton("Back",(dialog,i)->jpSettings()).show()).setPositiveButton("Done",null).show();
    }
    private static String bindingName(int binding){return binding<0?"Learn":((binding>>>12)==11?"CC ":"Note ")+(binding&127)+" / ch "+(((binding>>>8)&15)+1);}
    void encoderSettings() {
        MidiEncoders.Mapping mapping=encoderMapping;
        android.widget.LinearLayout layout=new android.widget.LinearLayout(activity);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);layout.setPadding(24,0,24,0);
        android.widget.Spinner mode=new android.widget.Spinner(activity);
        mode.setAdapter(new android.widget.ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,
            new String[]{"Off · standard parameter CCs","Absolute 0–127 · use movement","Relative · 1 up / 127 down","Relative · 65 up / 63 down","Relative · 1 up / 65 down"}));
        mode.setSelection(mapping.mode);layout.addView(mode);
        android.widget.TextView label=new android.widget.TextView(activity);
        label.setText("First CC (seven consecutive CCs, 0–121)");layout.addView(label);
        android.widget.EditText base=new android.widget.EditText(activity);
        base.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);base.setText(Integer.toString(mapping.base));layout.addView(base);
        android.widget.Spinner channel=new android.widget.Spinner(activity);
        String[] channels=new String[17];channels[0]="Any MIDI channel";
        for(int i=1;i<=16;i++)channels[i]="MIDI channel "+i;
        channel.setAdapter(new android.widget.ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,channels));
        channel.setSelection(mapping.channel);layout.addView(channel);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("MIDI knobs → FM-1")
            .setMessage("Order: SELECT, ALGO, PRESETS, K1–K4. These CCs follow the current page and replace their standard parameter actions. Relative mode is preferred if your controller supports it.")
            .setView(layout).setPositiveButton("Apply",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                MidiEncoders.Mapping next=new MidiEncoders.Mapping(mode.getSelectedItemPosition(),
                    Integer.parseInt(base.getText().toString()),channel.getSelectedItemPosition());
                encoderMapping=next;
                activity.getPreferences(0).edit().putInt("encoderMode",next.mode).putInt("encoderBase",next.base)
                    .putInt("encoderChannel",next.channel).apply();dialog.dismiss();
            } catch(IllegalArgumentException e) { base.setError("Enter a CC number from 0 to 121"); }
        }));dialog.show();
    }
    private void padMapping() {
        android.widget.EditText text=new android.widget.EditText(activity);
        text.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        text.setText(Integer.toString(padBase));
        new AlertDialog.Builder(activity).setTitle("First pad note (drum routing)")
            .setMessage("-1 keeps GM notes. A value such as 36 maps consecutive pad notes to the selected firmware's drum lanes.")
            .setView(text).setPositiveButton("Apply",(d,n)->{
                try { int value=Integer.parseInt(text.getText().toString()); if(value< -1 || value>112) throw new NumberFormatException();
                    Native.event(4,0,0); padBase=value; activity.getPreferences(0).edit().putInt("padBase",value).apply();
                } catch (NumberFormatException e) { message("Enter -1 or a note number from 0 to 112"); }
            }).setNegativeButton("Cancel",null).show();
    }
    void scanBluetooth() {
        if (manager==null) { message("Android MIDI is unavailable"); return; }
        String[] permissions=Build.VERSION.SDK_INT>=31 ? new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT} : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        for(String permission:permissions) if(activity.checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(permissions,71); return;
        }
        BluetoothManager bm=(BluetoothManager)activity.getSystemService(Activity.BLUETOOTH_SERVICE);
        if(bm==null || bm.getAdapter()==null || !bm.getAdapter().isEnabled()) { message("Enable Bluetooth, then scan again"); return; }
        stopScan(); scanner=bm.getAdapter().getBluetoothLeScanner();
        if(scanner==null) { message("Bluetooth LE scanning is unavailable"); return; }
        List<BluetoothDevice> found=new ArrayList<>(); List<String> names=new ArrayList<>();
        android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<>(activity,android.R.layout.simple_list_item_1,names);
        // Paired BLE devices may stop advertising once Android connects their HID service.
        // Opening their MIDI service directly does not require a new scan result.
        for(BluetoothDevice d:bm.getAdapter().getBondedDevices()){
            boolean midiDevice=isJp1(d.getName());ParcelUuid[] uuids=d.getUuids();
            if(uuids!=null)for(ParcelUuid uuid:uuids)midiDevice|=uuid.getUuid().equals(UUID.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700"));
            if(midiDevice){found.add(d);names.add((d.getName()==null?"BLE MIDI":d.getName())+" · paired");}
        }
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Bluetooth LE MIDI · scanning")
            .setAdapter(adapter,(d,which)->{
                connectBluetooth(found.get(which));
            }).setNegativeButton("Cancel",null).create();
        scan=new ScanCallback() {
            @Override public void onScanResult(int type,ScanResult result) {
                handler.post(()->{
                    if(!dialog.isShowing() || !active || found.contains(result.getDevice())) return;
                    BluetoothDevice d=result.getDevice(); found.add(d);
                    String name=d.getName(); names.add((name==null?"BLE MIDI":name)+" · "+d.getAddress()); adapter.notifyDataSetChanged();
                });
            }
            @Override public void onScanFailed(int code) { message("Bluetooth scan failed: "+code); stopScan(); }
        };
        dialog.setOnDismissListener(d->stopScan()); dialog.show();
        ScanFilter filter=new ScanFilter.Builder().setServiceUuid(new ParcelUuid(UUID.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700"))).build();
        try { scanner.startScan(Collections.singletonList(filter),new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scan); }
        catch(SecurityException e) { message("Bluetooth scan permission is required"); stopScan(); }
        int scanRequest=scanGeneration;
        handler.postDelayed(()->{ if(scanRequest!=scanGeneration)return; if(dialog.isShowing()) dialog.setTitle(found.isEmpty()?"No BLE MIDI found · try controller Bluetooth mode":"Bluetooth LE MIDI"); stopScan(); },10000);
    }
    private void stopScan() {
        scanGeneration++;
        if(scanner!=null && scan!=null) try { scanner.stopScan(scan); } catch(SecurityException ignored) {}
        scanner=null; scan=null;
    }
}
