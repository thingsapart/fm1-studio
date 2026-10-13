/* SPDX-License-Identifier: GPL-3.0-only */
/* Lifecycle fixture, deliberately not a substitute for a real firmware adapter. */
#include "firmware.h"
#include <string.h>
#ifndef FIXTURE_ID
#define FIXTURE_ID "fixture"
#endif
static uint8_t flash[32];
static int changed, held;
static void init(const uint8_t *image) { memset(flash,0,sizeof flash); if(image)memcpy(flash,image,sizeof flash);changed=held=0; }
static void render(float *out,unsigned n){for(unsigned i=0;i<2*n;i++)out[i]=held?0.25f:0;}
static void event(int kind,int id,int value){(void)id;if(kind==FM1_KEY)held=value;if(kind==FM1_PANIC||kind==FM1_STOP)held=0;if(kind==FM1_MASTER){flash[0]=value;changed=1;}}
static void tick(uint32_t ms){(void)ms;}
static void pixels(uint32_t *out){for(unsigned i=0;i<240*240;i++)out[i]=0xff000000;}
static int playing(void){return 0;}
static int checkpoint(void){return 1;}
static const uint8_t *storage(void){return flash;}
static int dirty(void){return changed;}
static void clean(void){changed=0;}
static unsigned midi(uint32_t *out,unsigned n){(void)out;(void)n;return 0;}
static const fm1_firmware api={FM1_ABI,sizeof api,FIXTURE_ID,44100,240,240,sizeof flash,init,render,event,tick,pixels,playing,checkpoint,storage,dirty,clean,midi};
__attribute__((visibility("default"))) const fm1_firmware *fm1_get_api(void){return &api;}
