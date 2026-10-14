/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;
import java.util.Arrays;

/** JP-1 profile. Bindings can be learned rather than assuming a factory preset. */
final class Jp1Controller {
    static final String[] CONTROLS={"Rewind · Previous track","BT · Mute selected track","Forward · Next track",
        "Play · Play / pause","Stop · Stop playback","Rec · Record selected track",
        "1L · SELECT / page","1R · ALGO / track","2L · PRESETS","2R · Tempo",
        "3L · K1","3R · K2","4L · K3","4R · K4","Pad bank · First note"};
    interface Sink { void button(int action);void knob(int knob,int delta);void note(int channel,int note,int velocity);default void held(int control,boolean down){} default void bank(int bank){} default void absolute(int knob,int value){} }
    static final class Mapping {
        final int[] bindings,bankFirst;
        final int mode,padBase,toggleButtons,pulseButtons;
        Mapping(int[] bindings,int mode,int padBase){this(bindings,mode,padBase,5,56);}
        Mapping(int[] bindings,int mode,int padBase,int toggleButtons){
            this(bindings,mode,padBase,toggleButtons,56);
        }
        Mapping(int[] bindings,int mode,int padBase,int toggleButtons,int pulseButtons){
            this(bindings,mode,padBase,toggleButtons,pulseButtons,new int[]{0xb02e,0xb01e,0xb026});
        }
        Mapping(int[] bindings,int mode,int padBase,int toggleButtons,int pulseButtons,int[] bankFirst){
            if(bindings.length!=14||mode<1||mode>4||padBase<0||padBase>112||toggleButtons<0||toggleButtons>63||pulseButtons<0||pulseButtons>63)throw new IllegalArgumentException();
            if(bankFirst.length!=3)throw new IllegalArgumentException();for(int first:bankFirst)if(first<0||(first>>>12)!=11||(first&255)>120)throw new IllegalArgumentException();
            for(int b:bindings)if(b!=-1&&((b>>>16)!=0||((b>>>12)!=9&&(b>>>12)!=11)))throw new IllegalArgumentException();
            this.bindings=bindings.clone();this.bankFirst=bankFirst.clone();this.mode=mode;this.padBase=padBase;this.toggleButtons=toggleButtons;this.pulseButtons=pulseButtons;
        }
        static Mapping defaults(){int[] b=new int[14];for(int i=0;i<6;i++)b[i]=0xb000+19+i;for(int i=0;i<8;i++)b[6+i]=0xb000+46+i;return new Mapping(b,1,52);}
    }
    static int binding(int packet){int status=(packet>>>8)&255;return ((status&240)<<8)|((status&15)<<8)|((packet>>>16)&127);}
    static boolean learnable(int packet,boolean knob){int status=(packet>>>8)&240;return status==176||(!knob&&status==144&&((packet>>>24)&127)>0);}
    static int padLane(int note,Mapping mapping){return Jp1Pads.lane(note,mapping.padBase);}
    private final Sink sink;
    private Mapping current;
    private final int[] previous=new int[24];
    private final int[] pressed=new int[6];
    Jp1Controller(Sink sink){this.sink=sink;reset();}
    void reset(){Arrays.fill(previous,-1);Arrays.fill(pressed,-1);current=null;}
    boolean accept(int packet,Mapping mapping){
        if(mapping!=current){reset();current=mapping;}
        int status=(packet>>>8)&255,type=status&240,note=(packet>>>16)&127,value=(packet>>>24)&127;
        int key=binding(packet);
        if(type==128)key=(key&0x0fff)|0x9000;
        if(type==176||type==144||type==128)for(int i=0;i<14;i++)if(mapping.bindings[i]==key){
            if(i<6){int down=type!=128&&value>0?1:0;boolean toggle=type==176&&(mapping.toggleButtons&(1<<i))!=0;boolean pulse=type==176&&!toggle&&(mapping.pulseButtons&(1<<i))!=0;if(pulse?down==1:down!=pressed[i]&&(toggle||down==1))sink.button(new int[]{3,5,4,0,1,2}[i]);pressed[i]=down;}
            else if(type==176)turn(i-6,value,mapping);
            return true;
        }
        if(type==176)for(int bank=1;bank<3;bank++){int knob=key-mapping.bankFirst[bank];if(knob>=0&&knob<8){turn(bank*8+knob,value,mapping);return true;}}
        if(type==176&&(status&15)==0&&note>=25&&note<=29){sink.held(note-25,value>0);return true;}
        if(type==144||type==128){sink.note(status&15,note,type==128?0:value);return true;}
        // JP-1 buttons can be programmed as realtime transport or standard MMC.
        if(status==0xfa||status==0xfb){sink.button(6);return true;}
        if(status==0xfc){sink.button(1);return true;}
        return false;
    }
    private void turn(int k,int value,Mapping mapping){sink.bank(k/8);int delta=0;if(mapping.mode==1){int last=previous[k];previous[k]=value;if(last>=0&&value!=last){delta=value-last;sink.absolute(k,value);}}
        else if(mapping.mode==2)delta=value==64?0:value<64?value:value-128;
        else if(mapping.mode==3)delta=value-64;
        else delta=(value&64)!=0?-(value&63):value&63;
        while(delta!=0){int step=Math.max(-32,Math.min(32,delta));sink.knob(k,step);delta-=step;}
    }
    boolean mmc(byte[] bytes){
        if(bytes.length!=6||(bytes[0]&255)!=240||(bytes[1]&255)!=127||(bytes[3]&255)!=6||(bytes[5]&255)!=247)return false;
        int cmd=bytes[4]&255;
        if(cmd==1)sink.button(1);else if(cmd==2||cmd==3)sink.button(6);else if(cmd==6)sink.button(2);else if(cmd==7)sink.button(7);else return false;
        return true;
    }
}
