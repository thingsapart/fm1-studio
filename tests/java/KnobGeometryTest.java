/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;

public class KnobGeometryTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        // Circle includes its painted rim but excludes square corners and the label/readout/footer.
        check(KnobGeometry.dial(50,57,50,57,29.5f));
        check(KnobGeometry.dial(79.5f,57,50,57,29.5f));
        check(!KnobGeometry.dial(79.6f,57,50,57,29.5f));
        check(!KnobGeometry.dial(77,84,50,57,29.5f));
        for(float y:new float[]{19,103,123})check(!KnobGeometry.dial(50,y,50,57,29.5f));
        // Dropdown/choice hit area follows its rounded box, not the surrounding widget.
        check(KnobGeometry.choice(50,60,5,35,95,84,6));
        check(KnobGeometry.choice(5,41,5,35,95,84,6));
        check(!KnobGeometry.choice(5,35,5,35,95,84,6));
        check(!KnobGeometry.choice(50,34,5,35,95,84,6));
        check(!KnobGeometry.choice(50,85,5,35,95,84,6));
        check(KnobGeometry.choice(50,35,5,35,95,84,6));
        // Compressed knob cards map screen Y back into the same painted coordinate system.
        float y=57*116f/130f;check(KnobGeometry.dial(50,y*130/116,50,57,29.5f));
        check(!KnobGeometry.choice(0,0,5,35,5,84,6));
        System.out.println("PASS exact circular knob and rounded choice hit regions, margins and scaled cards");
    }
}
