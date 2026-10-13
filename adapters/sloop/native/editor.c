/* SPDX-License-Identifier: GPL-3.0-only */
/* Included after the unchanged firmware by the Android wrapper. No USB, OTA,
 * JSON or JNI dependency enters the firmware. Match editor.c's safe mutations. */
#include "interactions.c"
static void editor_event(int kind, int id, int value)
{
    unsigned t = (unsigned)id;
    if (android_extra_event(kind,t,value)) {}
    else if (kind == SLOOP_SELECT && t < NTRK) { if(!ft_on)track_select(t); }
    else if (kind == SLOOP_PARAM && id >= 0 && t / 256 < NTRK && t % 256 < P_COUNT) {
        track_t *track = &trk[t / 256]; unsigned p = t % 256;
        const param_desc_t *d = track_desc(track, p);
        if (d && d->max > d->min) { if(p==P_SLEN){if(!android_editable())return;android_mark(track);} track->p[p] = clamp(value, d->min, d->max); if(mlu.on)ml_knob(track,p); }
    } else if (kind == SLOOP_GLOBAL && t < G_COUNT) {
        /* Actions and device settings stay in the original UI. */
        if (t == G_BPM || t == G_SWING || t == G_CLOCK || t == G_TUNE ||
            (t >= G_DTIME && t <= G_CDEPTH) || (t >= G_DRLVL && t <= G_ROLL) ||
            t == G_DRDLY || (t >= G_TXSRC && t <= G_TXDUCK) || t==G_SYNC || t==G_ROUTE || t==G_CHOTH || t==G_BEND || (t>=G_CH1 && t<=G_CH3) || t==G_DRCH || t==G_MIDI)
            { song.g[t] = clamp(value, GP[t].min, GP[t].max); lights_sync=song.g[G_SYNC];lights_min=song.g[G_ROUTE];lights_mout=song.g[G_MIDI];settings_later=1; }
    } else if (kind == SLOOP_ENGINE && t < NPART && value >= 0 && value < NENGINES) {
        set_engine_of(&trk[t], value);
    } else if (kind == SLOOP_PRESET && t < NPART && value >= 0 && value < ENGINES[trk[t].eng_req % NENGINES]->npresets) {
        apply_preset_to(&trk[t], value);
    } else if (kind == SLOOP_MIX && t < NTRK) {
        if (t == TRK_DRUM) song.g[G_DRLVL] = clamp(value, 0, 127);
        else trk[t].p[P_LEVEL] = clamp(value, 0, 127);
    } else if (kind == SLOOP_MUTE && t < NTRK) trk[t].p[P_MUTE] = value != 0;
    else if (kind == SLOOP_RECORD && t < NTRK) {
        if (ft_on) {
            if (!value) ft_close();
        } else {
            track_select(t);
            if ((value != 0) != (song.rec != 0 || rec_wait != 0)) rec_toggle();
        }
    } else if (kind == SLOOP_EDITOR_HOME) { go_home(); song.seq_mode = 0; }
    else if (kind == SLOOP_PAGE && t < NPAGES && page_shown(&PAGES[t]) &&
             (!is_drum(TSEL) || page_for_drum(&PAGES[t]))) {
        ui.home = 0; ui.page = t; ui.fam_last[PAGES[t].fam] = t; page_entered();
        // Direct editor pages should never turn the performance keys into step-entry keys.
        song.seq_mode = 0;
        if(PAGES[t].scope==SC_DRUM){drum_page=1;kb_grid=0;}
    }
    else if (kind == SLOOP_TRANSPORT) transport_req = ft_on ? 2 : value ? 1 : 2;
    else if (kind == SLOOP_NOTE && id >= 0 && t / 128 < NTRK) {
        track_t *track = &trk[t / 128];
        if (value > 0) input_on(track, t % 128, clamp(value, 1, 127));
        else input_off(track, t % 128);
    } else if (kind == SLOOP_STEP && id >= 0 && t / 8192 < NTRK) {
        if(!android_editable())return;
        track_t *track = &trk[t / 8192]; android_mark(track); unsigned step = (t % 8192) / 128, note = t % 128;
        if (is_drum(track)) {
            if (note >= DRUM_LANES) return;
            if (value) dstep_set(&track->dstep[step], note, LV_NORM, 0);
            else { dstep_clr(&track->dstep[step], note); if(!dstep_mask(&track->dstep[step]))android_clear_step(track,step); }
        } else {
            step_t *s = &track->step[step]; unsigned k;
            for (k = 0; k < s->n && k < 4; k++) if (s->note[k] == note) break;
            if (value && k == s->n && s->n < 4) {
                if (!s->n) { memset(s, 0, sizeof *s); s->vel = 100; }
                s->note[s->n++] = note; s->time = ST_NOTE;
            } else if (!value && k < s->n && k < 4) {
                for (unsigned j = k; j + 1 < s->n; j++) {
                    s->note[j] = s->note[j+1];
                    unsigned mask = 3u << (2*j);
                    s->lvl = (s->lvl & ~mask) | (((s->lvl >> (2*(j+1))) & 3u) << (2*j));
                    s->rat = (s->rat & ~mask) | (((s->rat >> (2*(j+1))) & 3u) << (2*j));
                }
                s->n--; s->note[s->n] = 0;
                s->lvl &= ~(3u << (2*s->n)); s->rat &= ~(3u << (2*s->n));
                if (!s->n) android_clear_step(track,step);
            }
        }
    }
    ui_input_ms = fm1_ms; ui.force = 1;
}
static void editor_param(struct sloop_parameter *out, int id, const param_desc_t *d, int value)
{
    const char *unit; char text[32];
    out->id = id; out->value = value; out->min = d->min; out->max = d->max;
    str_cpy(out->label, d->label, sizeof out->label);
    if (d->fmt == F_ENUM && d->names) {
        unsigned used = 0;
        for (int i = 0; i <= d->max - d->min; i++) {
            unsigned n = strlen(d->names[i]);
            if (used + n + 2 >= sizeof out->choices) { out->choices[0] = 0; break; }
            if (i) out->choices[used++] = '\t';
            str_cpy(out->choices + used, d->names[i], sizeof out->choices - used); used += n;
        }
    }
    if (d->fmt == F_ONOFF) str_cpy(out->choices, "OFF\tON", sizeof out->choices);
    if(d->fmt==F_ENUM||d->fmt==F_ONOFF){out->choice_count=d->max-d->min+1;for(int i=0;i<out->choice_count&&i<128;i++)out->choice_values[i]=d->min+i;}
    else if(d->names){int count=0;while(count<128&&d->names[count])count++;unsigned used=0;for(int i=0;i<count;i++){int v=d->min+(i*(d->max-d->min+1)+count-1)/count;out->choice_values[i]=v;unsigned n=strlen(d->names[i]);if(used+n+2>=sizeof out->choices)break;if(i)out->choices[used++]='\t';str_cpy(out->choices+used,d->names[i],sizeof out->choices-used);used+=n;out->choice_count++;}}

    param_format(d, value, text, &unit);
    str_cpy(out->text, text, sizeof out->text);
    unsigned n = strlen(out->text);
    if (unit && n < sizeof out->text-1) str_cpy(out->text+n, unit, sizeof out->text-n);
}
void sloop_editor_snapshot(struct sloop_editor *out)
{
    memset(out, 0, sizeof *out);
    out->version = SLOOP_EDITOR_VERSION; out->selected = song.sel;
    out->playing = song.playing;
    out->recording = song.rec | (rec_wait ? 1u << song.sel : 0u) | (ft_on ? 1u << ft_trk : 0u);
    out->bpm = song.g[G_BPM];
    out->length_parameter = song.sel*256 + P_SLEN;
    out->length = trk_len(TSEL); out->position = song.playing ? TSEL->seq_idx : -1;
    out->engine = TSEL->eng_req % NENGINES; out->preset = TSEL->preset;
    for (unsigned i = 0; i < NENGINES; i++) str_cpy(out->engines[i], ENGINES[i]->name, 16);
    const engine_t *engine = ENGINES[out->engine];
    out->preset_count = engine->npresets < SLOOP_EDITOR_PRESETS ? engine->npresets : SLOOP_EDITOR_PRESETS;
    for (int i = 0; i < out->preset_count; i++) str_cpy(out->presets[i], engine->presets[i].name, 24);
    for (unsigned i = 0; i < NTRK; i++) {
        out->tracks[i].level = i == TRK_DRUM ? song.g[G_DRLVL] : trk[i].p[P_LEVEL];
        out->tracks[i].mute = trk[i].p[P_MUTE]; out->tracks[i].drum=is_drum(&trk[i]);out->tracks[i].channel=trk_midi_ch(i);
        str_cpy(out->tracks[i].name, i == TRK_DRUM ? "DRUMS" : ENGINES[trk[i].eng_req % NENGINES]->name, 32);
    }
    /* Global + master first, followed by the firmware's parameter groups. */
    for (unsigned pass = 0; pass < 3; pass++)
    for (unsigned i = 0; i < NPAGES && out->group_count < SLOOP_EDITOR_GROUPS; i++) {
        const page_t *pg = &PAGES[i];
        if (pg->scope != SC_TRACK && pg->scope != SC_ENGINE && pg->scope != SC_GLOBAL) continue;
        if (pg->fam == FAM_SAVE || !page_shown(pg)) continue;
        int global = pg->scope == SC_GLOBAL;
        unsigned first = pg->id[0];
        unsigned order = global && first == G_BPM ? 0 : global && first == G_DUST ? 1 : 2;
        if (order != pass) continue;
        if (global && pg->fam==FAM_SAVE) continue;
        if (is_drum(TSEL) && !page_for_drum(pg) && pg->id[0]!=P_TFLT) continue;
        struct sloop_group *g = &out->groups[out->group_count];
        str_cpy(g->title, pg->title, sizeof g->title); g->page = is_drum(TSEL)&&pg->id[0]==P_TFLT?-3:(int)i;
        for (unsigned k = 0; k < 4; k++) {
            unsigned id = pg->id[k]; if (id == 255) continue;
            int global = pg->scope == SC_GLOBAL;

            const param_desc_t *d = global ? &GP[id] : track_desc(TSEL, id);
            if (!d || d->max <= d->min) continue;
            editor_param(&g->param[g->count++], global ? FM1_GLOBAL_PARAMETER_BASE + id : song.sel*256 + id,
                         d, global ? song.g[id] : TSEL->p[id]);
        }
        if (g->count) out->group_count++;
    }
    if (is_drum(TSEL) && out->group_count < SLOOP_EDITOR_GROUPS) {
        struct sloop_group *g = &out->groups[out->group_count++];
        str_cpy(g->title, "KIT", sizeof g->title); g->count = 1; g->page = -1;
        for (unsigned i=0;i<NPAGES;i++) if (PAGES[i].scope==SC_DRUM) { g->page=i; break; }
        editor_param(&g->param[0], song.sel*256+P_E0, &DRUM_KIT_DESC, TSEL->p[P_E0]);
    }
    if(!is_drum(TSEL)&&out->group_count<SLOOP_EDITOR_GROUPS){struct sloop_group *g=&out->groups[out->group_count++];str_cpy(g->title,"TRIM",sizeof g->title);g->page=-2;g->count=1;editor_param(&g->param[0],song.sel*256+P_ED_FX,&TP[P_ED_FX],TSEL->p[P_ED_FX]);}
    out->octave = song.octave;
    out->current.page = ui.home ? -1 : ui.page;
    str_cpy(out->current.title, ui.home ? "HOME" : cur_page()->title, sizeof out->current.title);
    for (int i = 0; i < out->group_count; i++) {
        if (out->groups[i].page == out->current.page && out->current.page >= 0) {
            out->current = out->groups[i]; break;
        }
    }
    if (ui.home || cur_page()->scope == SC_TRK) {
        // Match the four visible TRACKS controls, rather than inventing a second context.
        param_desc_t level = is_drum(TSEL) ? GP[G_DRLVL] : TP[P_LEVEL];
        level.label = "LEVEL"; level.fmt = F_PCT;
        param_desc_t steps = TP[P_SLEN]; steps.label = "STEPS";
        param_desc_t pan = TP[P_PAN]; pan.fmt = F_INT;
        editor_param(&out->current.param[0],FM1_GLOBAL_PARAMETER_BASE+G_SWING,&GP[G_SWING],song.g[G_SWING]);
        editor_param(&out->current.param[1],is_drum(TSEL) ? FM1_GLOBAL_PARAMETER_BASE+G_DRLVL : song.sel*256+P_LEVEL,
                     &level,is_drum(TSEL) ? song.g[G_DRLVL] : TSEL->p[P_LEVEL]);
        editor_param(&out->current.param[2],song.sel*256+P_SLEN,&steps,TSEL->p[P_SLEN]);
        editor_param(&out->current.param[3],song.sel*256+P_PAN,&pan,TSEL->p[P_PAN]);
        out->current.count = 4;
    }
    for (unsigned i = 0; i < 64; i++) {
        for (unsigned k = 0; k < 4; k++) out->steps[i][k] = -1;
        if (is_drum(TSEL)) out->steps[i][0] = dstep_mask(&TSEL->dstep[i]);
        else if (TSEL->step[i].time == ST_NOTE)
            for (unsigned k = 0; k < TSEL->step[i].n && k < 4; k++) out->steps[i][k] = TSEL->step[i].note[k];
    }
    for (unsigned i = 0; i < 16; i++) str_cpy(out->lanes[i], LANE_NAME[i], 16);
    android_json(out->extra,sizeof out->extra);
}
