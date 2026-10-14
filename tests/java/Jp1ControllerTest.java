/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;
import java.util.*;
public final class Jp1ControllerTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    private static int packet(int status,int number,int value){return (status>>4)|(status<<8)|(number<<16)|(value<<24);}
    public static void main(String[] args){
        List<String> events=new ArrayList<>();
        Jp1Controller controller=new Jp1Controller(new Jp1Controller.Sink(){
            public void button(int action){events.add("b"+action);}public void knob(int knob,int delta){events.add("k"+knob+":"+delta);}
            public void note(int channel,int note,int velocity){events.add("n"+channel+":"+note+":"+velocity);}
            public void held(int control,boolean down){events.add("h"+control+":"+down);}
        });
        int[] bindings=Jp1Controller.Mapping.defaults().bindings.clone();bindings[3]=0x913c;
        for(int i=0;i<8;i++)bindings[6+i]=0xb000+46+i;
        Jp1Controller.Mapping mapping=new Jp1Controller.Mapping(bindings,1,52);
        controller.accept(packet(0xb0,19,127),mapping);controller.accept(packet(0xb0,19,127),mapping);
        controller.accept(packet(0xb0,19,0),mapping);controller.accept(packet(0xb0,19,127),mapping);
        check(events.equals(Arrays.asList("b3","b3","b3")));events.clear();
        controller.accept(packet(0xb0,20,127),mapping);controller.accept(packet(0xb0,20,0),mapping);
        check(events.equals(Arrays.asList("b5")));events.clear();
        controller.accept(packet(0xb0,21,0),mapping);controller.accept(packet(0xb0,21,127),mapping);
        controller.accept(packet(0xb0,23,127),mapping);controller.accept(packet(0xb0,24,127),mapping);
        check(events.equals(Arrays.asList("b4","b4","b1","b2")));events.clear();
        controller.accept(packet(0xb0,24,127),mapping);controller.accept(packet(0xb0,24,127),mapping);
        Jp1Controller.Mapping pressOnly=Jp1Controller.Mapping.defaults();controller.accept(packet(0xb0,22,127),pressOnly);controller.accept(packet(0xb0,22,127),pressOnly);
        check(events.equals(Arrays.asList("b2","b2","b0","b0")));events.clear();
        controller.accept(packet(0x91,60,100),mapping);controller.accept(packet(0x81,60,100),mapping);controller.accept(packet(0x91,60,100),mapping);
        check(events.equals(Arrays.asList("b0","b0")));events.clear();
        for(int bank=0;bank<3;bank++)for(int pad=0;pad<16;pad++)check(Jp1Controller.padLane(36+bank*16+pad,mapping)==(3-pad/4)*4+pad%4);
        check(Jp1Controller.padLane(35,mapping)==-1);check(Jp1Controller.padLane(84,mapping)==-1);
        controller.accept(packet(0x90,36,99),mapping);controller.accept(packet(0x80,36,64),mapping);
        check(events.equals(Arrays.asList("n0:36:99","n0:36:0")));events.clear();
        controller.accept(packet(0xb0,49,10),mapping);check(events.isEmpty());controller.accept(packet(0xb0,49,12),mapping);
        controller.accept(packet(0xb0,53,90),mapping);check(events.equals(Arrays.asList("k3:2")));events.clear();
        mapping=new Jp1Controller.Mapping(bindings,2,36);
        controller.accept(packet(0xb0,46,127),mapping);controller.accept(packet(0xb0,53,1),mapping);
        controller.accept(packet(0xb0,53,127),mapping);
        check(events.equals(Arrays.asList("k0:-1","k7:1","k7:-1")));events.clear();
        mapping=Jp1Controller.Mapping.defaults();
        controller.accept(packet(0xb0,46,40),mapping);controller.accept(packet(0xb0,30,90),mapping);controller.accept(packet(0xb0,38,10),mapping);check(events.isEmpty());
        controller.accept(packet(0xb0,46,41),mapping);controller.accept(packet(0xb0,30,91),mapping);controller.accept(packet(0xb0,38,9),mapping);
        check(events.equals(Arrays.asList("k0:1","k8:1","k16:-1")));events.clear();
        for(int cc=25;cc<=29;cc++){controller.accept(packet(0xb0,cc,127),mapping);controller.accept(packet(0xb0,cc,0),mapping);}
        check(events.equals(Arrays.asList("h0:true","h0:false","h1:true","h1:false","h2:true","h2:false","h3:true","h3:false","h4:true","h4:false")));events.clear();
        MidiParser parser=new MidiParser(p->controller.accept(p,Jp1Controller.Mapping.defaults()),controller::mmc);
        parser.feed(new byte[]{(byte)240,127,127,6},0,4);check(events.isEmpty());
        parser.feed(new byte[]{2,(byte)248,(byte)247},0,3);check(events.equals(Arrays.asList("b6")));events.clear();
        parser.feed(new byte[]{(byte)240,127,0,6,1,(byte)247},0,6);check(events.equals(Arrays.asList("b1")));events.clear();
        parser.feed(new byte[]{(byte)240,127,127,6,6,(byte)247},0,6);check(events.equals(Arrays.asList("b2")));events.clear();
        check(!controller.mmc(new byte[]{(byte)240,126,127,6,6,(byte)247}));
        byte[] oversized=new byte[140];oversized[0]=(byte)240;oversized[139]=(byte)247;parser.feed(oversized,0,oversized.length);check(events.isEmpty());
        check(!controller.accept(packet(0xb2,19,127),Jp1Controller.Mapping.defaults()));
        check(Jp1Controller.binding(packet(0xb2,19,127))==0xb213);
        List<String> absolute=new ArrayList<>();Jp1Controller positions=new Jp1Controller(new Jp1Controller.Sink(){
            public void button(int action){}public void knob(int knob,int delta){}public void note(int channel,int note,int velocity){}
            public void absolute(int knob,int value){absolute.add(knob+":"+value);}
        });
        Jp1Controller.Mapping defaults=Jp1Controller.Mapping.defaults();positions.accept(packet(0xb0,46,30),defaults);check(absolute.isEmpty());
        positions.accept(packet(0xb0,46,30),defaults);check(absolute.isEmpty());
        positions.accept(packet(0xb0,46,31),defaults);positions.accept(packet(0xb0,46,31),defaults);positions.accept(packet(0xb0,46,127),defaults);
        positions.accept(packet(0xb0,30,40),defaults);positions.accept(packet(0xb0,30,41),defaults);
        check(absolute.equals(Arrays.asList("0:31","0:127","8:41")));
        positions.accept(packet(0xb0,46,1),new Jp1Controller.Mapping(defaults.bindings,2,52));check(absolute.size()==3);
        List<String> ordered=new ArrayList<>();Jp1Controller banks=new Jp1Controller(new Jp1Controller.Sink(){
            public void button(int action){}public void knob(int knob,int delta){}public void note(int channel,int note,int velocity){}
            public void bank(int bank){ordered.add("bank:"+bank);}public void absolute(int knob,int value){ordered.add("value:"+knob+":"+value);}
        });
        banks.accept(packet(0xb0,30,50),defaults);check(ordered.equals(Arrays.asList("bank:1")));ordered.clear();
        banks.accept(packet(0xb0,38,50),defaults);banks.accept(packet(0xb0,30,51),defaults);
        check(ordered.equals(Arrays.asList("bank:2","bank:1","value:8:51")));
        System.out.println("PASS JP-1 buttons, channel matching, note releases, eight knobs, MMC fragmentation/realtime, bounded SysEx");
    }
}
