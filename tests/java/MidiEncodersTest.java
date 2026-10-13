/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;
import java.util.*;
public final class MidiEncodersTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    private static int cc(int channel,int cc,int value){return 11|((0xb0+channel-1)<<8)|(cc<<16)|(value<<24);}
    public static void main(String[] args){
        List<String> turns=new ArrayList<>();MidiEncoders decoder=new MidiEncoders((k,d)->turns.add(k+":"+d));
        MidiEncoders.Mapping off=new MidiEncoders.Mapping(0,20,0);
        check(!decoder.accept(cc(1,20,1),off));
        MidiEncoders.Mapping relative=new MidiEncoders.Mapping(2,20,4);
        check(!decoder.accept(cc(1,20,1),relative));check(!decoder.accept(cc(4,19,1),relative));
        check(!decoder.accept(9|(0x93<<8)|(20<<16)|(100<<24),relative));
        for(int i=0;i<7;i++)check(decoder.accept(cc(4,20+i,1),relative));
        for(int i=0;i<7;i++)check(turns.get(i).equals(i+":1"));turns.clear();
        decoder.accept(cc(4,23,127),relative);check(turns.equals(Arrays.asList("3:-1")));
        turns.clear();decoder.accept(cc(4,23,0),relative);decoder.accept(cc(4,23,64),relative);check(turns.isEmpty());
        MidiEncoders.Mapping centered=new MidiEncoders.Mapping(3,20,0);
        decoder.accept(cc(1,26,65),centered);decoder.accept(cc(1,26,63),centered);decoder.accept(cc(1,26,64),centered);
        check(turns.equals(Arrays.asList("6:1","6:-1")));turns.clear();
        MidiEncoders.Mapping signed=new MidiEncoders.Mapping(4,20,0);
        decoder.accept(cc(1,20,1),signed);decoder.accept(cc(1,20,65),signed);
        decoder.accept(cc(1,20,0),signed);decoder.accept(cc(1,20,64),signed);
        check(turns.equals(Arrays.asList("0:1","0:-1")));turns.clear();
        MidiEncoders.Mapping absolute=new MidiEncoders.Mapping(1,20,0);
        check(decoder.accept(cc(1,21,60),absolute));check(turns.isEmpty());
        decoder.accept(cc(1,21,63),absolute);decoder.accept(cc(1,21,62),absolute);
        check(turns.equals(Arrays.asList("1:3","1:-1")));turns.clear();
        decoder.accept(cc(2,21,100),absolute);check(turns.isEmpty()); // independent channel baseline
        decoder.accept(cc(1,21,127),absolute);check(turns.equals(Arrays.asList("1:32","1:32","1:1")));turns.clear();
        decoder.reset();decoder.accept(cc(1,21,10),absolute);check(turns.isEmpty());
        decoder.accept(cc(1,21,100),new MidiEncoders.Mapping(1,20,0));check(turns.isEmpty()); // configuration reset
        MidiEncoders other=new MidiEncoders((k,d)->turns.add(k+":"+d));other.accept(cc(1,21,120),absolute);check(turns.isEmpty());
        System.out.println("PASS MIDI encoder routing, relative formats, absolute movement, source/channel isolation, reset, large deltas");
    }
}
