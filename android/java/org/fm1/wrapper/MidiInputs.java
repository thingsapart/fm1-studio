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
    private static final int[] DRUM_NOTES = {36,35,38,39,42,46,44,37,40,43,48,49,51,70,63,56};
    private final MidiManager.DeviceCallback callback = new MidiManager.DeviceCallback() {
        @Override public void onDeviceRemoved(MidiDeviceInfo info) {
            if (device != null && device.getInfo().getId() == info.getId()) disconnect();
            if(outputDevice!=null&&outputDevice.getInfo().getId()==info.getId())disconnectOutput();
        }
    };
    MidiInputs(Activity activity) {
        this.activity = activity;
        manager = (MidiManager) activity.getSystemService(Activity.MIDI_SERVICE);
    }
    void resume() { active = true;handler.removeCallbacks(sendOutput);handler.post(sendOutput); if (manager != null) manager.registerDeviceCallback(callback, handler); }
    void pause() { active = false;handler.removeCallbacks(sendOutput);disconnectOutput(); stopScan(); disconnect(); if (manager != null) manager.unregisterDeviceCallback(callback); }
    private void message(String s) { Toast.makeText(activity,s,Toast.LENGTH_LONG).show(); }
    void disconnect() {
        generation++;
        for (MidiOutputPort port : ports) try { port.close(); } catch (IOException ignored) {}
        ports.clear();
        if (device != null) try { device.close(); } catch (IOException ignored) {}
        device = null; Native.event(4,0,0); Native.event(6,0,0);
    }
    private void attach(MidiDevice opened, int request) {
        if (opened == null) { if (active && request == generation) message("MIDI connection failed"); return; }
        if (!active || request != generation) { try { opened.close(); } catch (IOException ignored) {} return; }
        device = opened;
        for (int i=0; i<device.getInfo().getOutputPortCount(); i++) {
            MidiOutputPort port = device.openOutputPort(i);
            if (port == null) continue;
            MidiEncoders encoders=new MidiEncoders((knob,delta)->Native.event(1,knob,delta));
            MidiParser parser = new MidiParser(packet -> {
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
            });
            port.connect(new MidiReceiver() {
                @Override public void onSend(byte[] data,int offset,int count,long timestamp) {
                    if (active && request == generation) parser.feed(data,offset,count);
                }
                @Override public void onFlush() { parser.reset(); encoders.reset(); Native.event(4,0,0); }
            });
            ports.add(port);
        }
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
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Bluetooth LE MIDI · scanning")
            .setAdapter(adapter,(d,which)->{
                BluetoothDevice selected=found.get(which); stopScan(); disconnect(); int request=generation;
                try { manager.openBluetoothDevice(selected,opened->attach(opened,request),handler); }
                catch(SecurityException e) { message("Bluetooth permission is required"); }
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
