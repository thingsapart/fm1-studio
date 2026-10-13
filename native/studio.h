/* SPDX-License-Identifier: GPL-3.0-only */
#pragma once
#include <stdint.h>
/* Port-owned, versioned snapshot. Only the engine worker calls these functions.
 * Commands encode the target track explicitly; delayed edits never follow selection. */
#define SLOOP_EDITOR_VERSION 1
#define SLOOP_EDITOR_GROUPS 128
#define SLOOP_EDITOR_PRESETS 128
struct sloop_parameter { int id, value, min, max, choice_count, choice_values[128]; char label[16], text[32], choices[768]; };
struct sloop_group { char title[24]; int count, page; struct sloop_parameter param[4]; };
struct sloop_editor {
    int version, selected, playing, recording, bpm, length, position, engine, preset;
    int group_count, preset_count, length_parameter;
    char engines[16][16], presets[SLOOP_EDITOR_PRESETS][24];
    struct { int level, mute, drum, channel; char name[32]; } tracks[8];
    struct sloop_group groups[SLOOP_EDITOR_GROUPS];
    struct sloop_group current;
    int octave;
    /* Drum lane mask, or four MIDI notes (-1 for unused), per step. */
    int steps[128][4];
    char lanes[16][16];
    char extra[65536];
    /* Zero means the original 64-step snapshot; extended engines publish 128. */
    int step_count;
};
/* ids: PARAM = track * 256 + P_*; GLOBAL = G_*; NOTE = track * 128 + MIDI note;
 * STEP = track * 8192 + step * 128 + note/lane, value = on/off.
 * ENGINE/PRESET/MIX/MUTE/RECORD target track in id. */
enum { SLOOP_SELECT = 8, SLOOP_PARAM, SLOOP_GLOBAL, SLOOP_ENGINE, SLOOP_PRESET,
       SLOOP_MIX, SLOOP_MUTE, SLOOP_STEP, SLOOP_NOTE, SLOOP_RECORD, SLOOP_TRANSPORT, SLOOP_EDITOR_HOME, SLOOP_PAGE, SLOOP_ACTION, SLOOP_DETAIL, SLOOP_LOCK, SLOOP_PERFORM, SLOOP_SONG, SLOOP_CONFIG, SLOOP_ADVANCED };
void sloop_editor_snapshot(struct sloop_editor *out);

#define FM1_STUDIO_ABI 2u
#define FM1_STUDIO_EVENT_BASE 64
#define FM1_GLOBAL_PARAMETER_BASE 8192
struct fm1_studio_api {
    unsigned abi, size;
    void (*snapshot)(struct sloop_editor *out);
    int (*import_object)(int kind,int id,const uint8_t *data,unsigned size);
    unsigned (*export_object)(int kind,int id,uint8_t *out,unsigned capacity);
    int (*import_sample)(unsigned slot,const uint8_t *data,unsigned size);
    int (*validate_image)(const uint8_t *data,unsigned size);
};
