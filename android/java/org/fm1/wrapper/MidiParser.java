/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;
/** Each source has its own parser. Android already removes USB/BLE transport framing. */
final class MidiParser {
    interface Sink { void packet(int packet); }
    private final Sink sink;
    private final java.util.function.Consumer<byte[]> systemExclusive;
    private final byte[] exclusive=new byte[128];
    private int exclusiveCount;
    private int status, first, count, needed;
    private boolean sysex;
    MidiParser(Sink sink) { this(sink,bytes->{}); }
    MidiParser(Sink sink,java.util.function.Consumer<byte[]> systemExclusive) { this.sink = sink;this.systemExclusive=systemExclusive; }
    synchronized void reset() { status = first = count = needed = exclusiveCount = 0; sysex = false; }
    synchronized void feed(byte[] data, int offset, int length) {
        for (int i = offset; i < offset + length; i++) {
            int b = data[i] & 255;
            if (b >= 0xf8) {
                if (b == 0xf8 || b == 0xfa || b == 0xfb || b == 0xfc) sink.packet(15 | (b << 8));
                continue;
            }
            if ((b & 128) != 0) {
                count = 0;
                if(b==0xf7&&sysex){if(exclusiveCount<exclusive.length){exclusive[exclusiveCount++]=(byte)b;systemExclusive.accept(java.util.Arrays.copyOf(exclusive,exclusiveCount));}sysex=false;status=0;continue;}
                if (b >= 0xf0) { status = 0; sysex = b == 0xf0;exclusiveCount=0;if(sysex)exclusive[exclusiveCount++]=(byte)b;continue; }
                sysex = false; status = b;
                needed = (b & 0xe0) == 0xc0 ? 1 : 2;
                continue;
            }
            if(sysex){if(exclusiveCount<exclusive.length)exclusive[exclusiveCount++]=(byte)b;continue;}
            if (status == 0) continue;
            if (needed == 2 && count == 0) { first = b; count = 1; continue; }
            sink.packet((status >> 4) | (status << 8) | ((needed == 2 ? first : b) << 16) | ((needed == 2 ? b : 0) << 24));
            count = 0;
        }
    }
}
