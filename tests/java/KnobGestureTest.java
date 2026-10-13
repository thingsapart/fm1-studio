/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;

public class KnobGestureTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static KnobGesture gesture(){return new KnobGesture(8,30,300);}
    public static void main(String[] args){
        KnobGesture g=gesture();
        // Ordinary drags tune immediately, without a preceding tap.
        g.begin(40,40,0);check(g.capturesDrag());check(g.move(70,40,100));check(g.delta(70,40)==30);check(g.end(70,40,200)==KnobGesture.DRAG);
        g.begin(40,40,300);check(g.move(40,20,350));check(g.delta(40,20)==20);g.cancel();
        g.begin(40,40,400);check(g.move(20,40,450));check(g.delta(20,40)==-20);g.cancel();
        g.begin(40,40,500);check(g.move(40,70,550));check(g.delta(40,70)==-30);g.cancel();
        // Small jitter neither changes a parameter nor stops the stationary hold.
        g.begin(40,40,1000);check(!g.move(43,44,1200));check(!g.hold(1749));check(g.hold(1750));check(g.isScrolling());check(!g.capturesDrag());check(!g.move(40,90,1800));check(g.end(40,90,1900)==KnobGesture.NONE);
        // A late first move scrolls even if the timer callback was delayed.
        g.begin(40,40,2000);check(!g.move(40,90,2750));check(g.isScrolling());g.cancel();
        // Once tuning begins, pausing never switches the same gesture to scrolling.
        g.begin(40,40,3000);check(g.move(60,40,3050));check(!g.hold(4000));check(g.move(60,80,4050));check(g.delta(60,80)==20);check(g.end(60,80,4100)==KnobGesture.DRAG);
        // A hold alone does not open the value editor or change the value.
        g.begin(40,40,5000);check(g.hold(5750));check(g.end(40,40,5800)==KnobGesture.NONE);
        g.begin(40,40,6000);check(g.end(40,40,6800)==KnobGesture.NONE);
        // Double-tap exact entry and quick second-press adjustment remain distinct.
        g.begin(40,40,7000);check(g.end(40,40,7050)==KnobGesture.TAP);
        g.begin(42,42,7100);check(!g.move(44,44,7120));check(g.end(44,44,7150)==KnobGesture.DOUBLE_TAP);
        g.begin(40,40,8000);check(g.end(40,40,8050)==KnobGesture.TAP);
        g.begin(40,40,8100);check(g.move(70,40,8150));check(g.end(70,40,8200)==KnobGesture.DRAG);
        g.begin(40,40,9000);g.cancel();check(!g.hold(9800));check(g.end(40,40,9850)==KnobGesture.NONE);
        // A touch outside the painted control never acquires tuning, even after entering it.
        g.begin(40,5,10000,false);check(!g.capturesDrag());check(!g.hold(10750));check(!g.move(40,40,10800));check(g.end(40,40,10900)==KnobGesture.NONE);
        g.begin(40,5,11000,false);check(!g.move(43,8,11100));check(!g.capturesDrag());g.cancel();
        // Readout taps can still open exact entry without grabbing scroll gestures.
        g.begin(40,100,12000,false);check(g.end(40,100,12050)==KnobGesture.TAP);
        g.begin(40,100,12100,false);check(g.end(40,100,12150)==KnobGesture.DOUBLE_TAP);
        System.out.println("PASS default knob tuning, 750 ms scroll hold, jitter, gesture locking, double-taps and cancellation");
    }
}
