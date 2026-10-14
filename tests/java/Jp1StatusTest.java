/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;
import java.util.*;
public final class Jp1StatusTest {
    private static void check(boolean value){if(!value)throw new AssertionError();}
    private static byte[] settings(int pad,int knob){
        byte[] packet=new byte[39];packet[0]=0x78;packet[1]=0x59;packet[2]=0x50;packet[3]=32;
        packet[6]=120;packet[8]=(byte)pad;packet[9]=(byte)knob;
        int sum=0;for(int i=6;i<38;i++)sum+=packet[i]&255;packet[38]=(byte)~sum;return packet;
    }
    public static void main(String[] args){
        check(Arrays.equals(Jp1Status.query(),new byte[]{(byte)0xf0,0x78,0x32,0x19,0x03,0,0,0x40,0x7f,(byte)0xf7}));
        List<String> events=new ArrayList<>();Jp1Status status=new Jp1Status((p,k)->events.add(p+":"+k));
        byte[] good=settings(2,1);
        for(int split=0;split<=good.length;split++){
            events.clear();status.feed(Arrays.copyOfRange(good,0,split));status.feed(Arrays.copyOfRange(good,split,good.length));
            check(events.equals(Arrays.asList("2:1")));
        }
        events.clear();for(byte b:good)status.feed(new byte[]{b});check(events.size()==1);
        events.clear();byte[] bad=good.clone();bad[9]^=1;status.feed(bad);check(events.isEmpty());
        status.feed(settings(3,0));status.feed(settings(0,255));check(events.isEmpty());
        status.feed(new byte[]{0x78,0x59,0x50,(byte)255,(byte)255,(byte)255});status.feed(good);check(events.size()==1);
        events.clear();status.feed(new byte[]{0,0x78,0x78});status.feed(Arrays.copyOfRange(good,1,good.length));check(events.size()==1);
        events.clear();status.feed(Arrays.copyOf(good,12));status.reset();status.feed(good);status.feed(settings(0,2));
        check(events.equals(Arrays.asList("2:1","0:2")));
        events.clear();bad=good.clone();bad[2]=0x67;status.feed(bad);check(events.isEmpty());
        events.clear();
        String capture="78 59 50 20 00 00 78 00 00 00 01 00 00 32 32 01 6E 2D 2D 2D 2D 2D 2D 2D 2D 2D 2D 2D 2D 2D 2D 2D 2D 01 7F 22 9A 56 51";
        String[] hex=capture.split(" ");byte[] live=new byte[hex.length];
        for(int i=0;i<hex.length;i++)live[i]=(byte)Integer.parseInt(hex[i],16);
        status.feed(Arrays.copyOf(live,29));status.feed(Arrays.copyOfRange(live,29,live.length));
        check(events.equals(Arrays.asList("0:0")));
        Jp1Status.Poll poll=new Jp1Status.Poll();
        check(poll.due(1000));poll.sent(1000);check(!poll.due(1050));check(poll.delay(1050)==450);
        poll.received();check(poll.live());check(!poll.due(1099));check(poll.due(1100));
        poll.sent(1100);check(poll.requestedAt()==1100);check(!poll.due(1599));
        check(!poll.due(1600));check(!poll.live());check(poll.delay(1600)==5000);
        check(!poll.due(6599));check(poll.due(6600));poll.sent(6600);poll.received();check(poll.live());
        poll.reset();check(!poll.live());check(poll.due(6700));
        System.out.println("PASS JP-1 bank query, fragmented/coalesced status frames, checksum, bounds, invalid banks and reconnect reset");
    }
}
