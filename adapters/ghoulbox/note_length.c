/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Leo Kuroshita (@kurogedelic), Hügelton Instruments
 * Note-duration helpers from Felucca ui.c, for the older Ghoulbox base. */
static uint32_t note_len(const track_t *t, uint32_t i)
{
    uint32_t len = (uint32_t)clamp(t->p[P_SLEN], 1, NSTEP), n = 1;
    while (i + n < len && t->step[i + n].time == ST_TIE)
        n++;
    return n;
}
static uint32_t note_set_len(track_t *t, uint32_t i, uint32_t want)
{
    uint32_t len = (uint32_t)clamp(t->p[P_SLEN], 1, NSTEP), n = note_len(t, i);
    for (; n > want && n > 1u; n--)
        step_clear(&t->step[i + n - 1u]);
    for (; n < want && i + n < len && !t->step[i + n].n && !t->step[i + n].hit; n++) {
        step_clear(&t->step[i + n]);
        t->step[i + n].time = ST_TIE;
    }
    return n;
}

