/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;

/** Raw KuSuite AE42 packets. The 0x66 read request returns 0x50 system settings. */
final class Jp1Status {
    interface Sink { void banks(int padBank,int knobBank); }
    private final Sink sink;
    private final byte[] packet=new byte[1031];
    private int count,expected;
    Jp1Status(Sink sink){this.sink=sink;}
    static byte[] query(){return Jp1Rgb.sysex(new byte[]{0x78,0x59,0x66,0,0,0,(byte)0xff});}
    void reset(){count=expected=0;}
    /** One outstanding read; fast while responsive, backed off on unsupported/stalled devices. */
    static final class Poll {
        private long next,requested;
        private boolean waiting,live;
        boolean due(long now){
            if(waiting&&now-requested>=500){waiting=false;live=false;next=now+5000;}
            return !waiting&&now>=next;
        }
        void sent(long now){requested=now;waiting=true;next=now+100;}
        void received(){waiting=false;live=true;}
        boolean live(){return live;}
        long requestedAt(){return requested;}
        long delay(long now){return Math.max(1,(waiting?requested+500:next)-now);}
        void reset(){next=requested=0;waiting=live=false;}
    }
    void feed(byte[] bytes){
        if(bytes==null)return;
        for(byte b:bytes){
            int value=b&255;
            if(count==0){if(value==0x78)packet[count++]=b;continue;}
            if(count==1){if(value==0x59)packet[count++]=b;else if(value!=0x78)reset();continue;}
            packet[count++]=b;
            if(count==6){
                int size=(packet[3]&255)|((packet[4]&255)<<8)|((packet[5]&255)<<16);
                if(size>packet.length-7){reset();continue;}
                expected=size+7;
            }
            if(expected>0&&count==expected){
                int sum=0;for(int i=6;i<count;i++)sum+=packet[i]&255;
                boolean valid=(sum&255)==255&&(packet[2]&255)==0x50&&count==39;
                int pad=packet[8]&255,knob=packet[9]&255;
                reset();
                if(valid&&pad<3&&knob<3)sink.banks(pad,knob);
            }
        }
    }
}
