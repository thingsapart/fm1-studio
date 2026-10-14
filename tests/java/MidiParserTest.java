/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;
import java.util.*;
public final class MidiParserTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        List<Integer> packets=new ArrayList<>();MidiParser parser=new MidiParser(packets::add);
        parser.feed(new byte[]{(byte)0x99,36},0,2);check(packets.isEmpty());
        parser.feed(new byte[]{100,37,0,(byte)0xf8,38,110},0,6);
        check(packets.size()==4);check(packets.get(0)==(9|(0x99<<8)|(36<<16)|(100<<24)));
        check(packets.get(1)==(9|(0x99<<8)|(37<<16)));check(packets.get(2)==(15|(0xf8<<8)));
        check(packets.get(3)==(9|(0x99<<8)|(38<<16)|(110<<24)));
        parser.feed(new byte[]{(byte)0xf0,1,2,(byte)0xfa,(byte)0xf7,60,100},0,7);
        check(packets.size()==5);check(packets.get(4)==(15|(0xfa<<8)));
        parser.feed(new byte[]{(byte)0xb0,74,127,(byte)0xc0,5,6,(byte)0x90,60,(byte)0xf8,100},0,10);
        check(packets.size()==10);check(packets.get(5)==(11|(0xb0<<8)|(74<<16)|(127<<24)));
        check(packets.get(6)==(12|(0xc0<<8)|(5<<16)));check(packets.get(7)==(12|(0xc0<<8)|(6<<16)));
        parser.reset();parser.feed(new byte[]{60,100},0,2);check(packets.size()==10);
        List<Integer> other=new ArrayList<>();MidiParser second=new MidiParser(other::add);second.feed(new byte[]{61,100},0,2);check(other.isEmpty());
        System.out.println("PASS MIDI fragmentation, running status, realtime interleaving, CC, SysEx isolation, reset, source isolation");
    }
}
