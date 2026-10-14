/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;

public final class ControllerSelectorTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    private static int packet(int cc,int raw){return 11|(0xb0<<8)|(cc<<16)|(raw<<24);}
    public static void main(String[] args){
        // Feed real JP-1 MIDI decoding through repeated slow/fast full sweeps.
        for(int count:new int[]{2,8,25,33,65,128})for(int bank=0;bank<3;bank++){
            final int pages=count;final int[] current={0},nativePage={0};final long[] time={1000};
            final boolean[] seen=new boolean[count];seen[0]=true;
            ControllerValue.Selector selector=new ControllerValue.Selector();
            selector.observe(0,count,"pages",time[0]);
            ControllerValue.Filter filter=new ControllerValue.Filter();
            Jp1Controller controller=new Jp1Controller(new Jp1Controller.Sink(){
                public void button(int action){}public void knob(int index,int delta){}public void note(int channel,int note,int velocity){}
                public void absolute(int index,int raw){
                    int before=selector.observe(nativePage[0],pages,"pages",time[0]);
                    int next=ControllerValue.absolute(filter.accept(raw,time[0]),0,pages-1,0);
                    if(selector.pickup.accept(next,before)&&next!=before){
                        // The production callback rebuilds the View synchronously, before the echo.
                        selector.pickup.rearm();selector.sent(next,time[0]);
                        check(selector.observe(nativePage[0],pages,"pages",time[0])==next);
                        selector.pickup.accept(next,next);
                    }
                    current[0]=selector.observe(nativePage[0],pages,"pages",time[0]);
                    seen[current[0]]=true;
                }
            });
            Jp1Controller.Mapping mapping=Jp1Controller.Mapping.defaults();int cc=new int[]{46,30,38}[bank];
            controller.accept(packet(cc,0),mapping);
            // Acquire at HOME, including the smallest choice bucket (128 choices).
            controller.accept(packet(cc,1),mapping);controller.accept(packet(cc,0),mapping);
            for(int speed:new int[]{2,80})for(int cycle=0;cycle<3;cycle++){
                for(int raw=1;raw<=127;raw++){
                    time[0]+=speed;controller.accept(packet(cc,raw),mapping);
                    if(speed==80||raw%5==0)nativePage[0]=current[0];
                }
                check(current[0]==count-1);
                for(int raw=126;raw>=0;raw--){
                    time[0]+=speed;controller.accept(packet(cc,raw),mapping);
                    if(speed==80||raw%5==0)nativePage[0]=current[0];
                }
                check(current[0]==0);
            }
            for(boolean visited:seen)check(visited);
            // Fast large jumps can cross a pickup target; neither endpoint is stranded.
            selector.reset();selector.observe(count/2,count,"pages",time[0]);
            check(!selector.pickup.accept(0,count/2));
            check(selector.pickup.accept(count-1,count/2));
        }
        ControllerValue.Selector s=new ControllerValue.Selector();
        check(s.observe(3,10,"A/track1",1000)==3);check(s.pickup.accept(3,3));
        s.sent(4,1010);check(s.observe(3,10,"A/track1",1040)==4&&!s.pickup.waiting());
        s.sent(5,1040);check(s.observe(4,10,"A/track1",1080)==5); // Delayed intermediate echo.
        check(s.observe(5,10,"A/track1",1100)==5&&!s.pickup.waiting());
        check(s.observe(2,10,"A/track1",1400)==2&&s.pickup.waiting()); // External page change.
        check(s.pickup.accept(2,2));s.sent(7,1410);
        check(s.observe(1,10,"B/track1",1420)==1&&s.pickup.waiting());
        check(s.pickup.accept(1,1));s.sent(7,1430);
        check(s.observe(0,4,"B/track2",1440)==0&&s.pickup.waiting());
        // Relative bursts advance from the requested page even before any native echo.
        for(int i=1;i<=3;i++){int current=s.observe(0,4,"B/track2",1450+i);s.sent(current+1,1450+i);check(s.observe(0,4,"B/track2",1450+i)==i);}
        for(int i=2;i>=0;i--){int current=s.observe(3,4,"B/track2",1460+i);s.sent(current-1,1460+i);check(s.observe(3,4,"B/track2",1460+i)==i);}
        System.out.println("PASS JP-1 page sweeps both ways, all banks, endpoints, reversals, rebuilds, stale echoes, relative bursts and context changes");
    }
}
