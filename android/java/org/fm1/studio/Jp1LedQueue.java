/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;
import java.util.Arrays;
/** One latest bulk palette, including tracked bank indicators. */
final class Jp1LedQueue {
    private int[] desired=new int[18],sent,inFlight;
    private boolean repaint=true;
    void update(int[] colors,boolean force){if(colors.length!=18)throw new IllegalArgumentException();desired=colors.clone();repaint|=force;}
    void refresh(){repaint=true;}
    boolean pending(){return repaint||!Arrays.equals(desired,sent);}
    byte[] begin(){inFlight=desired.clone();repaint=false;return Jp1Rgb.bulk(inFlight);}
    void completed(){sent=inFlight;inFlight=null;}
}
