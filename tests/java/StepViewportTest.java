/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;
public final class StepViewportTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        check(StepViewport.reveal(0,360,68,68,98,548)==0);
        check(StepViewport.reveal(0,360,68,518,548,548)==188);
        check(StepViewport.reveal(188,360,68,68,98,548)==0); // Loop wraps or earlier edit.
        check(StepViewport.reveal(100,360,68,158,188,548)==90); // Hidden under sticky labels.
        check(StepViewport.reveal(100,360,68,200,230,548)==100); // Visible: no movement.
        check(StepViewport.reveal(0,200,28,388,412,412)==212); // Last drum lane.
        check(StepViewport.reveal(212,200,28,28,52,412)==0);
        check(StepViewport.reveal(0,700,68,518,548,548)==0); // Wide landscape viewport.
        check(StepViewport.reveal(0,0,68,518,548,548)==0); // Before layout.
        System.out.println("PASS step viewport: offscreen edits/playhead, sticky headers, wraparound, drum lanes and wide screens");
    }
}
