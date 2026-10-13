/* SPDX-License-Identifier: GPL-3.0-only */
#include "registry.h"
#include <string.h>
#include "engines.h"
unsigned fm1_firmware_count(void) { return sizeof(fm1_engines)/sizeof(fm1_engines[0]); }
const fm1_definition *fm1_firmware_at(unsigned index) {
    return index < fm1_firmware_count() ? &fm1_engines[index] : 0;
}
const fm1_definition *fm1_firmware_find(const char *id) {
    if (!id) return 0;
    for(unsigned i=0;i<fm1_firmware_count();i++) if(!strcmp(id,fm1_engines[i].id)) return &fm1_engines[i];
    return 0;
}
