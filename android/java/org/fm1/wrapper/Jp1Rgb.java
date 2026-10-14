/* SPDX-License-Identifier: GPL-3.0-only
 * Protocol encoding adapted from padmon, Copyright (c) 2026 Flo (MIT).
 * See LICENSES/MIT-padmon.txt and https://tangled.org/faz.ms/padmon/.
 */
package org.fm1.wrapper;
import java.io.ByteArrayOutputStream;

/** KuSuite command 0x51; experimentally verified per device before enabling feedback. */
final class Jp1Rgb {
    static byte[] frame(int[] colors){return frame(colors,255);}
    static byte[] frame(int[] colors,int index){
        if(index!=255&&(index<0||index>=16))throw new IllegalArgumentException("Only pad slots can be addressed");
        if(colors.length!=16)throw new IllegalArgumentException("16 physical pad colors required");
        return encode(colors,index);
    }
    static int bankColor(int bank){return bank<0?0:new int[]{0x400000,0x004000,0x000040}[Math.floorMod(bank,3)];}
    static int[] withBanks(int[] pads,int knobBank,int padBank,boolean swap){if(pads.length!=16)throw new IllegalArgumentException();int[] all=java.util.Arrays.copyOf(pads,18);all[swap?17:16]=bankColor(knobBank);all[swap?16:17]=bankColor(padBank);return all;}
    static byte[] bulk(int[] colors){if(colors.length!=18)throw new IllegalArgumentException();return encode(colors,255);}
    private static byte[] encode(int[] colors,int index){
        byte[] payload=new byte[55];payload[0]=(byte)index;
        for(int i=0;i<colors.length;i++){int color=colors[i];payload[1+3*i]=(byte)(color>>8);payload[2+3*i]=(byte)(color>>16);payload[3+3*i]=(byte)color;}
        byte[] packet=new byte[62];packet[0]=0x78;packet[1]=0x59;packet[2]=0x51;packet[3]=55;
        System.arraycopy(payload,0,packet,6,payload.length);int sum=0;for(byte b:payload)sum+=b&255;packet[61]=(byte)~sum;
        return sysex(packet);
    }
    static byte[] sysex(byte[] packet){
        ByteArrayOutputStream result=new ByteArrayOutputStream();result.write(240);int acc=0,bits=0;
        for(byte b:packet){acc|=(b&255)<<bits;bits+=8;while(bits>=7){result.write(acc&127);acc>>>=7;bits-=7;}}
        if(bits>0)result.write(acc&127);result.write(247);return result.toByteArray();
    }
    static int slot(int readingOrder){if(readingOrder<0||readingOrder>=16)throw new IllegalArgumentException();return (3-readingOrder/4)*4+readingOrder%4;}
}
