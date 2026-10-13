/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;

/** The SLOOP/MOVE editable drum-synth wire fields, shared by both editor routes. */
final class DrumSound {
    static final String[] LANES={"KICK","SNARE","CLAP","HAT","OPEN HAT","LOW TOM","HIGH TOM","CRASH","RIDE","SHAKER","CONGA","RIM","COWBELL","CLAVE","KICK 2","SNARE 2"};
    static final int[] MAX={5,20,127,15,96,127,255,127,127,255,127,127,127,255,127,255,127,127,127,127,127,255};
    static final String[] SECTIONS={"TONE / PITCH","TONE ENVELOPE / CLICK","NOISE / CLAP","FILTER / OUTPUT"};
    static final int[][] FIELDS={{0,2,3,4,5,9,10},{6,7,8,11},{1,12,13,14,18,19},{15,16,17,20,21}};
    static final String[] WAVES={"OFF","SINE","TRIANGLE","SQUARE","FM","BELL"};
    static final String[] NOISE={"OFF","WHITE","METAL","CYMBAL","CHIP","CLAP","CLAP + WHITE","CLAP + METAL","CLAP + CYMBAL","CLAP + CHIP"};
    static final int[] NOISE_VALUES={0,1,2,3,4,16,17,18,19,20};
    static int min(int field){return field==17?-128:0;}
    static int parameterId(int kit,int lane,int field){if(kit<0||kit>=4||lane<0||lane>=16||field<0||field>=22)throw new IllegalArgumentException("Invalid drum sound field");return 4096+kit*512+lane*32+field;}
}
