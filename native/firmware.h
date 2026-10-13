/* SPDX-License-Identifier: GPL-3.0-only */
#pragma once
#include <stddef.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
#define FM1_ABI 1u
enum fm1_event { FM1_BUTTON, FM1_ENCODER, FM1_KEY, FM1_MIDI, FM1_PANIC,
                 FM1_MASTER, FM1_CONNECTION, FM1_STOP, FM1_SCREEN };
/* Firmware calls are serialized. Active calls belong to one worker; selection
 * and initialization may transfer ownership while that worker is stopped.
 * The platform owns audio, files and MIDI.
 * Each Android plugin has private firmware globals and is unloaded on a switch.
 * Static hosts must provide an adapter which fully resets its own state. */
typedef struct fm1_firmware {
    uint32_t abi, struct_size;
    const char *id;
    uint32_t sample_rate, width, height, storage_size;
    void (*init)(const uint8_t *image);
    void (*render)(float *stereo, unsigned frames);
    void (*event)(int kind, int id, int value);
    void (*tick)(uint32_t milliseconds);
    void (*pixels)(uint32_t *argb);
    int (*playing)(void);
    int (*checkpoint)(void); /* Nonzero only after native storage accepted the save. */
    const uint8_t *(*storage)(void);
    int (*dirty)(void);
    void (*clean)(void);
    unsigned (*midi_out)(uint32_t *out, unsigned capacity);
} fm1_firmware;
typedef const fm1_firmware *(*fm1_entry)(void);
#ifdef __cplusplus
}
#endif
