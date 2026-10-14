/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;
import java.util.Arrays;

/** Per-source CC movement decoder. Emits the same detents as the physical FM-1 encoders. */
final class MidiEncoders {
    interface Sink { void turn(int knob, int delta); }
    static final class Mapping {
        final int mode, base, channel; // channel 0 = any, otherwise 1..16
        Mapping(int mode,int base,int channel) {
            if(mode<0 || mode>4 || base<0 || base>121 || channel<0 || channel>16)
                throw new IllegalArgumentException("Invalid encoder mapping");
            this.mode=mode;this.base=base;this.channel=channel;
        }
    }
    private final Sink sink;
    private final int[] previous=new int[16*7];
    private Mapping current;
    MidiEncoders(Sink sink) { this.sink=sink; reset(); }
    void reset() { Arrays.fill(previous,-1); current=null; }
    boolean accept(int packet,Mapping mapping) {
        if(current!=mapping) { reset();current=mapping; }
        int status=(packet>>>8)&255, channel=(status&15)+1;
        int knob=((packet>>>16)&127)-mapping.base;
        if(mapping.mode==0 || (status&240)!=176 || knob<0 || knob>=7 ||
                (mapping.channel!=0 && mapping.channel!=channel)) return false;
        int value=(packet>>>24)&127, delta=0;
        if(mapping.mode==1) {
            int index=(channel-1)*7+knob, last=previous[index];previous[index]=value;
            if(last>=0)delta=value-last; // First message establishes position without a jump.
        } else if(mapping.mode==2) {
            delta=value==64?0:(value<64?value:value-128); // 1=+1, 127=-1
        } else if(mapping.mode==3) {
            delta=value-64; // 65=+1, 63=-1, 64=neutral
        } else {
            delta=(value&64)!=0?-(value&63):(value&63); // 1=+1, 65=-1
        }
        // Native input bounds each event to 32 detents; preserve larger movement in chunks.
        while(delta!=0) {
            int step=Math.max(-32,Math.min(32,delta));sink.turn(knob,step);delta-=step;
        }
        return true;
    }
}
