/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;
import java.util.Arrays;
public final class Jp1LedQueueTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    private static byte[] unpack(byte[] frame){byte[] result=new byte[62];int acc=0,bits=0,count=0;for(int i=1;i<frame.length-1;i++){acc|=(frame[i]&127)<<bits;bits+=7;while(bits>=8){result[count++]=(byte)acc;acc>>>=8;bits-=8;}}return result;}
    public static void main(String[] args){
        Jp1LedQueue queue=new Jp1LedQueue();check(queue.pending());check(unpack(queue.begin())[6]==(byte)255);queue.completed();check(!queue.pending());
        int[] pads=new int[16];pads[12]=Jp1Feedback.ACTIVE;
        int[] old=Jp1Rgb.withBanks(pads,1,2,false);queue.update(old,false);byte[] packet=unpack(queue.begin());check(packet[55]==0x40&&packet[56]==0&&packet[57]==0&&packet[60]==0x40);
        pads[12]=0;pads[0]=Jp1Feedback.ACTIVE;int[] latest=Jp1Rgb.withBanks(pads,2,0,false);queue.update(latest,false);queue.completed();check(queue.pending());queue.begin();queue.completed();check(!queue.pending());
        int[] swapped=Jp1Rgb.withBanks(pads,2,0,true);check(swapped[16]==latest[17]&&swapped[17]==latest[16]);
        queue.update(latest,true);check(queue.pending());queue.begin();queue.completed();check(!queue.pending());
        check(Jp1Rgb.bankColor(-1)==0&&Jp1Rgb.bankColor(0)!=Jp1Rgb.bankColor(1)&&Jp1Rgb.bankColor(1)!=Jp1Rgb.bankColor(2));
        System.out.println("PASS bulk latest-state coalescing, in-flight supersession, indicator GRB encoding, bank colors and slot swapping");
    }
}
