/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;
final class ControllerValue {
    /** Compare mapped values so every position in a discrete choice can pick up. */
    static final class Pickup {
        private boolean caught;
        private Integer previous;
        void rearm(){caught=false;previous=null;}
        boolean accept(int position,int target){
            if(!caught&&(position==target||previous!=null&&
                ((previous<target&&position>target)||(previous>target&&position<target))))caught=true;
            previous=position;return caught;
        }
        int direction(int target){return caught||previous==null?0:Integer.compare(target,previous);}
        boolean waiting(){return !caught;}
    }
    static int absolute(int raw,int min,int max,int choices){int value=Math.max(0,Math.min(127,raw));return choices>0?Math.round(value*(choices-1)/127f):min+Math.round(value*(max-(float)min)/127f);}
    static Filter[] filters(int count){Filter[] result=new Filter[count];for(int i=0;i<count;i++)result[i]=new Filter();return result;}
    static final class Filter {
        private int last=-1,direction;private long at;
        int accept(int raw,long now){
            int delta=raw-last,sign=Integer.signum(delta);
            if(last>=0&&raw>0&&raw<127&&sign!=0&&sign!=direction&&Math.abs(delta)==1&&now-at<80)return last;
            if(delta!=0){last=raw;direction=sign;at=now;}return raw;
        }
    }
    static final class Pending {
        private int value;private long until;
        void sent(int value,long now){this.value=value;until=now+250;}
        boolean accepts(int observed,long now){return now>=until||observed==value;}
    }
}
