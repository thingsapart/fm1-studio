/* SPDX-License-Identifier: GPL-3.0-only */
#pragma once
#include "firmware.h"
#include "screens.h"
#ifdef __cplusplus
extern "C" {
#endif
typedef struct {
    const char *id, *name;
    fm1_entry entry;
    const fm1_screen *screens;
    unsigned screen_count;
} fm1_definition;
unsigned fm1_firmware_count(void);
const fm1_definition *fm1_firmware_at(unsigned index);
const fm1_definition *fm1_firmware_find(const char *id);
#ifdef __cplusplus
}
#endif
