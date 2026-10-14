/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;
import android.app.Activity;
import android.bluetooth.*;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.util.Arrays;
import java.util.UUID;

/** The vendor LED transport is separate from Android's BLE MIDI connection. */
final class Jp1BleLights {
    private static final String TAG="FM1-JP1-GATT";
    private static UUID uuid(String id){return UUID.fromString("0000"+id+"-0000-1000-8000-00805f9b34fb");}
    private final Activity activity;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic write;
    private byte[] frame;
    private int offset,phase,automaticFrames;
    private boolean testing,following,ready,stopping,layoutTest;
    private Jp1LedQueue palette=new Jp1LedQueue();
    private long nextAttempt,lastFrameAt,lastRefresh;
    private boolean queryFrame,lightsEnabled=true;
    private final Jp1Status.Poll bankPoll=new Jp1Status.Poll();
    private final Jp1Status bankStatus;
    private final Runnable flush=()->flushLatest();
    void follow(BluetoothDevice device){if(gatt!=null||android.os.SystemClock.uptimeMillis()<nextAttempt)return;connect(device,false,true);}
    void update(int[] colors,boolean repaint,boolean enabled){
        if(enabled)palette.update(colors,repaint);else if(lightsEnabled)palette.update(new int[18],false);
        lightsEnabled=enabled;flushLatest();
    }
    void stop(){if(following&&ready){stopping=true;palette.update(new int[18],false);flushLatest();BluetoothGatt pending=gatt;handler.postDelayed(()->{if(gatt==pending)close();},1500);}else close();}
    private void flushLatest(){
        if(!following||!ready||frame!=null)return;
        long now=android.os.SystemClock.uptimeMillis();
        handler.removeCallbacks(flush);
        if(!stopping&&lightsEnabled&&now-lastRefresh>=5000){palette.refresh();lastRefresh=now;}
        if(!stopping&&bankPoll.due(now)){
            bankStatus.reset();bankPoll.sent(now);queryFrame=true;startFrame(Jp1Status.query());return;
        }
        if(!stopping&&!bankPoll.live())((MainActivity)activity).controllerBankStatus(false);
        if(!palette.pending()){
            if(stopping)close();else handler.postDelayed(flush,bankPoll.delay(now));return;
        }
        long delay=Math.max(0,50-(now-lastFrameAt));
        if(delay>0){handler.postDelayed(flush,delay);return;}
        queryFrame=false;lastFrameAt=now;startFrame(palette.begin());
    }
    private void startFrame(byte[] bytes){
        frame=bytes;offset=0;
        BluetoothGatt pendingLink=gatt;handler.postDelayed(()->{if(gatt==pendingLink&&frame==bytes)fail("JP-1 write timed out");},3000);
        writeChunk();
    }
    private Runnable finished;
    Jp1BleLights(Activity activity){
        this.activity=activity;
        bankStatus=new Jp1Status((pad,knob)->{
            if(!following||stopping)return;
            boolean first=!bankPoll.live();bankPoll.received();
            if(first)Log.d(TAG,"Live bank status ready · reply "+(android.os.SystemClock.uptimeMillis()-bankPoll.requestedAt())+" ms");
            ((MainActivity)activity).controllerBanks(pad,knob,bankPoll.requestedAt());
            flushLatest();
        });
    }
    void close(){handler.removeCallbacksAndMessages(null);if(gatt!=null){gatt.close();gatt=null;}write=null;frame=null;queryFrame=false;bankStatus.reset();bankPoll.reset();((MainActivity)activity).controllerBankStatus(false);testing=false;following=false;ready=false;stopping=false;palette=new Jp1LedQueue();Runnable done=finished;finished=null;if(done!=null)done.run();}
    private void fail(String reason){boolean show=testing;Log.e(TAG,reason);nextAttempt=android.os.SystemClock.uptimeMillis()+5000;close();if(show)Toast.makeText(activity,reason,Toast.LENGTH_LONG).show();}
    void inspect(BluetoothDevice device){connect(device,false,false);}
    void test(BluetoothDevice device,Runnable done,boolean layout){connect(device,true,false);layoutTest=layout;if(gatt!=null)finished=done;else done.run();}
    private void connect(BluetoothDevice device,boolean test,boolean auto){
        close();testing=test;following=auto;phase=0;Log.d(TAG,test?"Starting vendor RGB test":auto?"Starting automatic pad feedback":"Inspecting JP-1 services (read-only)");
        try{gatt=device.connectGatt(activity,false,new BluetoothGattCallback(){
            @Override public void onCharacteristicChanged(BluetoothGatt link,BluetoothGattCharacteristic c){
                if(!c.getUuid().equals(uuid("ae42")))return;
                byte[] value=c.getValue();byte[] bytes=value==null?null:value.clone();
                handler.post(()->{if(link==gatt)bankStatus.feed(bytes);});
            }
            @Override public void onConnectionStateChange(BluetoothGatt link,int status,int state){handler.post(()->{
                if(link!=gatt)return;Log.d(TAG,"connection status="+status+" state="+state);
                if(status==BluetoothGatt.GATT_SUCCESS&&state==BluetoothProfile.STATE_CONNECTED){if(!link.discoverServices())fail("JP-1 service discovery could not start");}
                else if(state==BluetoothProfile.STATE_DISCONNECTED||status!=BluetoothGatt.GATT_SUCCESS)fail("JP-1 LED connection closed: "+status);
            });}
            @Override public void onServicesDiscovered(BluetoothGatt link,int status){handler.post(()->{
                if(link!=gatt)return;
                if(status!=BluetoothGatt.GATT_SUCCESS){fail("JP-1 service discovery failed: "+status);return;}
                BluetoothGattService vendor=link.getService(uuid("ae40"));
                Log.d(TAG,"AE40 vendor service "+(vendor==null?"absent":"present"));
                if(!testing&&!following){close();return;}
                write=vendor==null?null:vendor.getCharacteristic(uuid("ae41"));
                if(write==null||(write.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)==0){fail("JP-1 vendor LED write port unavailable");return;}
                BluetoothGattCharacteristic notify=vendor.getCharacteristic(uuid("ae42"));
                BluetoothGattDescriptor ccc=notify==null?null:notify.getDescriptor(uuid("2902"));
                if(ccc!=null){
                    if(!link.setCharacteristicNotification(notify,true)){fail("JP-1 notifications unavailable");return;}
                    ccc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    if(!link.writeDescriptor(ccc))fail("JP-1 notification setup could not start");
                }else connected();
            });}
            @Override public void onDescriptorWrite(BluetoothGatt link,BluetoothGattDescriptor descriptor,int status){handler.post(()->{
                if(link!=gatt)return;
                if(status!=BluetoothGatt.GATT_SUCCESS){fail("JP-1 notification setup failed: "+status);return;}
                connected();
            });}
            @Override public void onCharacteristicWrite(BluetoothGatt link,BluetoothGattCharacteristic c,int status){handler.post(()->{
                if(link!=gatt||c!=write||frame==null)return;
                if(status!=BluetoothGatt.GATT_SUCCESS){fail("JP-1 LED write failed: "+status);return;}
                if(offset<frame.length)handler.post(()->{if(link==gatt)writeChunk();});
                else{
                    if(following&&queryFrame){queryFrame=false;frame=null;flushLatest();}
                    else if(following){automaticFrames++;if(automaticFrames==1||automaticFrames%50==0)Log.d(TAG,"Automatic RGB frames sent="+automaticFrames);palette.completed();frame=null;flushLatest();}
                    else {Log.d(TAG,"RGB frame queued successfully: phase="+phase+" bytes="+frame.length);frame=null;phase++;handler.postDelayed(()->{if(link==gatt)nextFrame();},layoutTest?500:phase==4?500:3000);}
                }
            });}
        },BluetoothDevice.TRANSPORT_LE);
        }catch(SecurityException e){fail("Bluetooth permission is required");return;}
        if(gatt==null){fail("JP-1 LED connection could not start");return;}
        BluetoothGatt pending=gatt;handler.postDelayed(()->{if(gatt==pending&&!ready)fail("JP-1 LED connection timed out");},25000);
    }
    private void connected(){ready=true;gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);Log.d(TAG,following?"Automatic LED feedback ready":"Vendor RGB test ready");if(following)flushLatest();else nextFrame();}
    private void nextFrame(){
        if(phase==(layoutTest?17:4)){String message=layoutTest?"LED layout test sent · top-left to bottom-right, then off":"LED test sent · red, green, blue, off";Log.d(TAG,message);close();Toast.makeText(activity,message,Toast.LENGTH_LONG).show();return;}
        int color=layoutTest?0x404040:new int[]{0x400000,0x004000,0x000040,0}[phase];
        int[] colors=new int[16];if(layoutTest){if(phase<16)colors[Jp1Rgb.slot(phase)]=color;}else Arrays.fill(colors,color);
        frame=Jp1Rgb.frame(colors);offset=0;
        Log.d(TAG,"Sending RGB phase="+phase+" color="+Integer.toHexString(color));writeChunk();
    }
    private static String hex(byte[] bytes){if(bytes==null)return "null";StringBuilder text=new StringBuilder();for(int i=0;i<Math.min(bytes.length,96);i++)text.append(String.format(java.util.Locale.ROOT,"%02X ",bytes[i]&255));return text.toString();}
    private void writeChunk(){
        if(gatt==null||frame==null)return;
        int end=Math.min(offset+20,frame.length);
        write.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        write.setValue(Arrays.copyOfRange(frame,offset,end));offset=end;
        if(!gatt.writeCharacteristic(write))fail("JP-1 LED write could not start");
    }
}
