/* SPDX-License-Identifier: GPL-3.0-only
 * Reference vectors: padmon, Copyright (c) 2026 Flo (MIT); LICENSES/MIT-padmon.txt.
 */
package org.fm1.studio;
import java.util.Arrays;
public final class Jp1RgbTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    private static byte[] hex(String text){String[] tokens=text.split(" ");byte[] result=new byte[tokens.length];for(int i=0;i<tokens.length;i++)result[i]=(byte)Integer.parseInt(tokens[i],16);return result;}
    public static void main(String[] args){
        int[] colors=new int[16];
        check(Arrays.equals(Jp1Rgb.frame(colors),hex("F0 78 32 45 3A 03 00 40 7F 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 F7")));
        colors[0]=0x0000ff;colors[1]=0x282828;colors[15]=0xff6400;
        check(Arrays.equals(Jp1Rgb.frame(colors),hex("F0 78 32 45 3A 03 00 40 7F 00 00 7C 47 02 05 0A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 20 76 1F 00 00 00 00 00 00 00 40 09 F7")));
        byte[] frame=Jp1Rgb.frame(colors);check(frame.length==73&&frame[0]==(byte)240&&frame[72]==(byte)247);
        for(int i=1;i<frame.length-1;i++)check((frame[i]&255)<128);
        boolean[] occupied=new boolean[16];for(int i=0;i<16;i++){int slot=Jp1Rgb.slot(i);check(!occupied[slot]);occupied[slot]=true;}
        check(Jp1Rgb.slot(0)==12&&Jp1Rgb.slot(3)==15&&Jp1Rgb.slot(12)==0);
        try{Jp1Rgb.frame(new int[18]);throw new AssertionError();}catch(IllegalArgumentException expected){}
        System.out.println("PASS JP RGB exact upstream reference frames, GRB colors, 7-bit framing, checksum and physical slot order");
    }
}
