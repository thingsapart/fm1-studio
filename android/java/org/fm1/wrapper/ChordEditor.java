/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.wrapper;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Direct chord choices, always-visible pitch preview, and one-shot grid placement. */
final class ChordEditor extends LinearLayout {
    final StudioView ui;
    final int track;
    final SharedPreferences preferences;
    final String preferencePrefix;
    int root,octave,quality,inversion,length;
    final Button[] roots=new Button[12],shapes=new Button[7],lengths=new Button[4];
    final int[] durations={1,2,4,8};
    final TextView name,notes,octaveLabel,voicingLabel;
    final Button octaveDown,octaveUp,voicingDown,voicingUp;
    final PreviewKeyboard keyboard;
    int[] sounding=new int[0];
    Dialog dialog;
    final Runnable release;

    ChordEditor(StudioView ui){
        super(ui.getContext());this.ui=ui;track=ui.selected;release=()->{for(int pitch:sounding)Native.event(Native.NOTE,track*128+pitch,0);sounding=new int[0];};setOrientation(VERTICAL);
        preferences=((MainActivity)ui.getContext()).getPreferences(0);preferencePrefix=Definitions.active.id+".chord.";
        root=clamp(preferences.getInt(preferencePrefix+"root",0),0,11);octave=clamp(preferences.getInt(preferencePrefix+"octave",4),-1,7);quality=clamp(preferences.getInt(preferencePrefix+"quality",0),0,6);inversion=clamp(preferences.getInt(preferencePrefix+"inversion",0),0,ChordNotes.INTERVALS[quality].length-1);length=preferences.getInt(preferencePrefix+"length",2);if(length!=1&&length!=2&&length!=4&&length!=8)length=2;
        LinearLayout preview=ui.row();name=ui.label("",20,StudioView.GREEN);preview.addView(name,new LinearLayout.LayoutParams(0,ui.dp(36),1));Button hear=ui.button("▶",v->audition());hear.setContentDescription("Audition this chord");preview.addView(hear,ui.buttonSize(44,36));addView(preview);
        notes=ui.label("",12,StudioView.TEXT);notes.setSingleLine(false);addView(notes,ui.lpFill(32));keyboard=new PreviewKeyboard();addView(keyboard,ui.lpFill(72));
        ScrollView scroll=new ScrollView(ui.getContext());LinearLayout controls=ui.column();scroll.addView(controls);addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        controls.addView(ui.label("ROOT NOTE",12,StudioView.DIM),ui.lpFill(26));
        for(int start=0;start<12;start+=6){LinearLayout row=ui.row();for(int i=start;i<start+6;i++){final int choice=i;roots[i]=chip(ChordNotes.ROOTS[i],()->{stopPreview();root=choice;refresh();});row.addView(roots[i],ui.weight(40));}controls.addView(row);}
        controls.addView(ui.label("CHORD TYPE",12,StudioView.DIM),ui.lpFill(26));String[] labels={"MAJOR","MINOR","MAJ7","MIN7","7","SUS2","SUS4"};
        for(int start=0;start<7;start+=4){LinearLayout row=ui.row();for(int i=start;i<start+4;i++){if(i<7){final int choice=i;shapes[i]=chip(labels[i],()->{stopPreview();quality=choice;inversion=Math.min(inversion,ChordNotes.INTERVALS[quality].length-1);refresh();});shapes[i].setContentDescription(ChordNotes.QUALITIES[i]);row.addView(shapes[i],ui.weight(40));}else row.addView(new View(ui.getContext()),ui.weight(40));}controls.addView(row);}
        LinearLayout register=ui.row();octaveDown=chip("−",()->{stopPreview();octave=Math.max(-1,octave-1);refresh();});octaveDown.setContentDescription("Chord one octave lower");register.addView(octaveDown,ui.buttonSize(32,40));octaveLabel=ui.label("",11,StudioView.TEXT);register.addView(octaveLabel,new LinearLayout.LayoutParams(0,ui.dp(44),1));octaveUp=chip("+",()->{stopPreview();octave=Math.min(7,octave+1);refresh();});octaveUp.setContentDescription("Chord one octave higher");register.addView(octaveUp,ui.buttonSize(32,40));controls.addView(register);
        LinearLayout voicing=ui.row();voicingDown=chip("‹",()->{stopPreview();inversion=Math.max(0,inversion-1);refresh();});voicingDown.setContentDescription("Previous chord voicing");voicing.addView(voicingDown,ui.buttonSize(32,40));voicingLabel=ui.label("",11,StudioView.TEXT);voicing.addView(voicingLabel,new LinearLayout.LayoutParams(0,ui.dp(44),1));voicingUp=chip("›",()->{stopPreview();inversion=Math.min(ChordNotes.INTERVALS[quality].length-1,inversion+1);refresh();});voicingUp.setContentDescription("Next chord voicing");voicing.addView(voicingUp,ui.buttonSize(32,40));controls.addView(voicing);
        controls.addView(ui.label("LENGTH IN SEQUENCER STEPS",12,StudioView.DIM),ui.lpFill(26));LinearLayout duration=ui.row();for(int i=0;i<4;i++){final int choice=durations[i];lengths[i]=chip(Integer.toString(choice),()->{length=choice;refresh();});lengths[i].setContentDescription(choice+" sequencer steps");duration.addView(lengths[i],ui.weight(40));}controls.addView(duration);
        TextView hint=ui.label("Voicing rearranges the same chord's notes. Use a POLY voice to hear every note.",11,StudioView.DIM);hint.setSingleLine(false);controls.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        addView(ui.button("PLACE ON GRID",v->{
            if(ui.selected!=track||ui.isDrum()){Toast.makeText(getContext(),"Choose a synth track first",Toast.LENGTH_SHORT).show();return;}
            preferences.edit().putInt(preferencePrefix+"root",root).putInt(preferencePrefix+"octave",octave).putInt(preferencePrefix+"quality",quality).putInt(preferencePrefix+"inversion",inversion).putInt(preferencePrefix+"length",length).apply();
            ChordPlacement chord=new ChordPlacement(track,pitches(),length,ChordNotes.chordName(root,quality));stopPreview();if(dialog!=null)dialog.dismiss();ui.armChord(chord);
        }),ui.buttonFill(44));refresh();
    }
    static int clamp(int n,int min,int max){return Math.max(min,Math.min(max,n));}
    Button chip(String text,Runnable action){Button button=ui.button(text,v->action.run());button.setTextSize(11);return button;}
    int[] pitches(){return ChordNotes.build(root,octave,quality,inversion);}
    void refresh(){
        name.setText(ChordNotes.chordName(root,quality));notes.setText(ChordNotes.format(pitches()));octaveLabel.setText("ROOT: "+ChordNotes.name((octave+1)*12+root));voicingLabel.setText(inversion==0?"VOICING: ROOT POSITION":"VOICING: INVERSION "+inversion);octaveDown.setEnabled(octave>-1);octaveUp.setEnabled(octave<7);voicingDown.setEnabled(inversion>0);voicingUp.setEnabled(inversion<ChordNotes.INTERVALS[quality].length-1);
        for(int i=0;i<roots.length;i++)select(roots[i],i==root);for(int i=0;i<shapes.length;i++)select(shapes[i],i==quality);for(int i=0;i<lengths.length;i++)select(lengths[i],durations[i]==length);keyboard.invalidate();
    }
    void select(Button button,boolean selected){button.setSelected(selected);button.setTextColor(selected?StudioView.GREEN:StudioView.DIM);button.setBackground(ui.tint(selected?0xff153a2a:StudioView.CARD));}
    void audition(){stopPreview();if(ui.selected!=track||ui.state.optInt("recordState")!=0){Toast.makeText(getContext(),"Stop recording before previewing a chord",Toast.LENGTH_SHORT).show();return;}sounding=pitches();for(int pitch:sounding)Native.event(Native.NOTE,track*128+pitch,100);postDelayed(release,350);}
    void stopPreview(){removeCallbacks(release);release.run();}
    final class PreviewKeyboard extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        final int[] white={0,2,4,5,7,9,11,12,14,16,17,19,21,23};
        PreviewKeyboard(){super(ChordEditor.this.getContext());}
        boolean selected(int pitch,int[] chord){for(int note:chord)if(note==pitch)return true;return false;}
        @Override protected void onDraw(Canvas c){
            int[] chord=pitches();int first=chord[0]/12*12;float width=getWidth()/14f;paint.setTypeface(InstrumentStyle.font(getContext()));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(ui.dp(9));
            for(int i=0;i<14;i++){float left=i*width;paint.setColor(first+white[i]>127?0xff303733:selected(first+white[i],chord)?StudioView.GREEN:0xffdce3dd);c.drawRect(left+1,ui.dp(3),left+width-1,getHeight()-ui.dp(4),paint);paint.setColor(0xff344539);if(first+white[i]<=127)c.drawText(ChordNotes.name(first+white[i]),left+width/2,getHeight()-ui.dp(10),paint);}
            for(int i=0;i<13;i++)if(white[i+1]-white[i]==2){float x=(i+1)*width;paint.setColor(selected(first+white[i]+1,chord)?StudioView.GREEN:0xff101612);c.drawRect(x-width*.28f,ui.dp(3),x+width*.28f,getHeight()*.61f,paint);}
            setContentDescription("Chord preview keyboard: "+ChordNotes.format(chord));
        }
    }
}
