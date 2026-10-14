/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;
public final class ControllerValueTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        check(ControllerValue.absolute(0,22,100,0)==22&&ControllerValue.absolute(127,22,100,0)==100);
        check(ControllerValue.absolute(0,0,63,0)==0&&ControllerValue.absolute(127,0,63,0)==63);
        check(ControllerValue.absolute(127,40,240,0)==240&&ControllerValue.absolute(0,40,240,0)==40);
        check(ControllerValue.absolute(127,0,99,4)==3);
        int previous=-1;for(int i=0;i<128;i++){int n=ControllerValue.absolute(i,-64,63,0);check(n>=previous||previous==-1);previous=n;}
        ControllerValue.Filter f=new ControllerValue.Filter();check(f.accept(20,100)==20&&f.accept(21,110)==21&&f.accept(20,120)==21&&f.accept(19,130)==19&&f.accept(20,250)==20&&f.accept(127,260)==127&&f.accept(0,270)==0);
        ControllerValue.Pending p=new ControllerValue.Pending();p.sent(40,100);check(!p.accepts(35,120)&&p.accepts(40,130)&&p.accepts(35,350));
        ControllerValue.Pickup pickup=new ControllerValue.Pickup();
        check(!pickup.accept(10,80)&&pickup.direction(80)==1);
        check(!pickup.accept(79,80)&&pickup.accept(82,80));
        check(pickup.accept(20,82)&&pickup.direction(82)==0);
        pickup.rearm();check(!pickup.accept(100,40)&&pickup.direction(40)==-1);
        check(pickup.accept(39,40));
        pickup.rearm();check(!pickup.accept(10,40));
        pickup.rearm();check(!pickup.accept(100,40)); // A stale position must not cross a new assignment.
        check(pickup.accept(40,40));
        pickup.rearm();check(pickup.accept(ControllerValue.absolute(70,0,3,4),2));
        pickup.rearm();check(pickup.accept(0,0));pickup.rearm();check(pickup.accept(127,127));
        System.out.println("PASS full-range absolute control mapping, discrete choices, brief reversal filter and stale snapshot protection");
    }
}
