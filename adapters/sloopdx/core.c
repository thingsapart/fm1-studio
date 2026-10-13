/* SPDX-License-Identifier: GPL-3.0-only */
/* Additive platform wrapper. The unchanged firmware runs on a single worker:
 * no firmware state, queues or interrupt guards are shared between threads. */
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include "core.h"
#ifdef __APPLE__
#define __attribute__(x)
#endif
#undef memset
#undef memcpy
#define memset sloop_memset
#define memcpy sloop_memcpy
#define memcmp sloop_memcmp
#include "libc.c"
#undef memset
#undef memcpy
#undef memcmp
#define FELUCCA_ARRANGER 1
#define FELUCCA_FLASH 1
#define FELUCCA_OTA 0
#define FELUCCA_CDC 0
#define FELUCCA_UAC 0
#define FELUCCA_ICONS 1
static uint8_t android_flash[SLOOP_STORAGE_SIZE] __attribute__((aligned(16)));
static int storage_dirty;
static void fm1_irq_off(void) {}
static void fm1_irq_on(void) {}
static uint32_t irq_save(void) { return 0; }
static void irq_restore(uint32_t f) { (void)f; }
static void fm1_delay_ms(uint32_t ms) { (void)ms; }
static struct { uint32_t notes, buttons; } fm1_in;
#include "felucca_tables.h"
#include "../build/firmware/src/core.h"
#include "engines.c"
#include "drums.c"
#include "params.c"
#include "voice.c"
#include "slicer.c"
#include "fx.c"
#define MQ 256u
static uint32_t midi_in_q[MQ], mi_w, mi_r;
static struct { uint8_t config, suspended, up; uint32_t setups, sof_seen; } usb;
static uint32_t android_midi_out[512],android_mo_w,android_mo_r;
static void midi_out_event(uint32_t packet) { if(android_mo_w-android_mo_r>=512){android_mo_r=android_mo_w;for(unsigned ch=0;ch<16;ch++){android_midi_out[android_mo_w++%512]=11u|((0xb0u+ch)<<8)|(123u<<16);android_midi_out[android_mo_w++%512]=11u|((0xb0u+ch)<<8)|(64u<<16);}}android_midi_out[android_mo_w++%512]=packet; }
unsigned sloop_take_midi(uint32_t *out,unsigned max){unsigned n=0;while(n<max&&android_mo_r!=android_mo_w)out[n++]=android_midi_out[android_mo_r++%512];return n;}
static int usb_cdc_now(void) { return 0; }
#include "arranger.c"
#include "seq.c"
static uint16_t screen[240 * 240];
static void lcd_sync(void) {}
static void lcd_blit(uint32_t x, uint32_t y, uint32_t w, uint32_t h, const uint16_t *p)
{
    for (unsigned j = 0; j < h && y + j < 240; j++)
        for (unsigned i = 0; i < w && x + i < 240; i++) screen[(y+j)*240+x+i] = p[j*w+i];
}
#include "gfx.c"
static void lcd_fill(uint32_t x, uint32_t y, uint32_t w, uint32_t h, uint16_t c)
{
    for (unsigned j = 0; j < h && y+j < 240; j++)
        for (unsigned i = 0; i < w && x+i < 240; i++) screen[(y+j)*240+x+i] = swap16(c);
}
static int32_t encs[7];
#define FM1_TICKS_PER_US 24u
static uint32_t fm1_ticks(void) { return fm1_ms * 24000u; }
static int32_t fm1_enc_take(uint32_t e) { int32_t v = encs[e]; encs[e] = 0; return v; }
static uint8_t fm1_led[16], fm1_led_dim[16], fm1_led_bg[16];
static volatile uint16_t fm1_led_bg_ns;
#define FM1_NCOL 16u
static const int8_t FM1_KEYMAP[5][16];
static void fm1_led_key(uint32_t id, int on) { (void)id; (void)on; }
static uint32_t edges_btn, notes_seen;
static uint32_t fm1_input_edges(int n) { (void)n; uint32_t v = edges_btn; edges_btn = 0; return v; }
static uint32_t fm1_input_note_edges(void) { uint32_t v = fm1_in.notes & ~notes_seen; notes_seen = fm1_in.notes; return v; }
static void fm1_wdt_feed(void) { fm1_ms++; }
static int32_t fm1_adc_read(int c) { (void)c; return -1; }
static struct { uint32_t magic, stage, page, home, ui_frames; } felucca_dbg;
#define SCOPE_N 512u
static int16_t scope_buf[SCOPE_N], scope_bufr[SCOPE_N];
static uint32_t scope_w;
#include "panel.c"
#include "ui.c"
#include "ui_song.c"
#include "ui_studio.c"
#include "ui_voice.c"
#include "icons.c"
#include "ui_draw.c"

#include "ui_layers.c"
static int st_wipe_all(void);
static int dx_reboot_requested;
static void fm1_reboot(void){dx_reboot_requested=1;}
#include "ui_menu.c"
#include "ui_input.c"
static uint8_t flash_ok;
static int st_read(uint32_t off, void *dst, uint32_t n)
{
    if (off > sizeof android_flash || n > sizeof android_flash - off) return -1;
    memcpy(dst, android_flash + off, n); return 0;
}
static int st_erase(uint32_t off)
{
    if (off > sizeof android_flash - 4096u || (off & 4095u)) return -1;
    memset(android_flash + off, 255, 4096); storage_dirty = 1; return 0;
}
static int st_prog(uint32_t off, const void *data, uint32_t n)
{
    if (off > sizeof android_flash || n > sizeof android_flash - off) return -1;
    memcpy(android_flash + off, data, n); storage_dirty = 1; return 0;
}
#include "storage.c"
#include "upreset.c"
#define FL_FAR(f) f
static uint32_t fl_jedec_ram(void) { return 0x856014u; }
static void fl_plain_window_init(void) {}
#include "project.c"



static void dx_perform_apply(void);
static void dx_perform_reset(void);
static float pcm[CTL*2];
static unsigned pcm_pos = CTL;
void sloop_init(const uint8_t *image)
{
    dx_perform_reset();
    memset(&song, 0, sizeof song); memset(trk, 0, sizeof trk);
    memset(proj_slot, 0, sizeof proj_slot); memset(&persist_saved, 0, sizeof persist_saved);
    memset(&settings, 0, sizeof settings); memset(&ui, 0, sizeof ui);
    memset(DXV, 0, sizeof DXV); memset(dx_opmute, 0, sizeof dx_opmute);
    memset(&drums, 0, sizeof drums); memset(&dext, 0, sizeof dext);
    memset(&ve, 0, sizeof ve); memset(&fx, 0, sizeof fx);
    memset(dly_buf, 0, sizeof dly_buf); memset(cho_buf, 0, sizeof cho_buf);
    memset(rev_line, 0, sizeof rev_line); memset(rev_ap, 0, sizeof rev_ap);
    memset(pre_buf, 0, sizeof pre_buf); memset(punch_ring, 0, sizeof punch_ring);
    memset(&punch, 0, sizeof punch); punch.req=punch.cur=-1;
    lights_lvl=lights_keys=lights_notes=lights_sync=lights_mout=lights_min=usb_serial=0;
    rec_wait=rec_tempo=rec_count=ci_on=rec_go=ft_on=ft_bars=0;
    ft_n=ft_t=ft_btn_prev=ci_u=0; transport_req=panic_req=0;
    lk_w=lk_r=0; chain_n=srec=0; live_sec=live_req=-1;
    memset(roll,0,sizeof roll); memset(kb_kind,0,sizeof kb_kind);
    memset(midi_sel_on,0,sizeof midi_sel_on); memset(midi_pc,0,sizeof midi_pc);
    master_cur=-1;lim_env=LIM_T;
    fm1_in.notes = fm1_in.buttons = edges_btn = notes_seen = kb_prev = 0;
    mi_r = mi_w = 0;android_mo_r=android_mo_w=0;undo.valid=0; clk_beat = clk_pos = 0; pcm_pos = CTL;
    if (image) memcpy(android_flash, image, sizeof android_flash);
    else memset(android_flash, 255, sizeof android_flash);
    panel = PANEL_DEFAULT;
    persist_boot(); panel_init(); settings_init();
    for (unsigned i = 0; i < G_COUNT; i++) song.g[i] = GP[i].def;
    for (unsigned i = 0; i < NTRK; i++) {
        track_defaults(&trk[i]);
        if (i < NPART) { set_engine_of(&trk[i], TRK_DEF[i][0]); apply_preset_to(&trk[i], TRK_DEF[i][1]); trk[i].engine = trk[i].eng_req; }
        track_defaults_steps(&trk[i]);
    }
    TDRUM->p[P_E0] = DRUM_DEFAULT_KIT;
    song.master_q12 = 2048; song.batt_raw = 620;
    autosave_resume();
    song.g[G_SYNC] = lights_sync; song.g[G_MIDI] = lights_mout; song.g[G_ROUTE] = lights_min;
    layers_init(); go_home(); ui.force = 1; ui_draw(); storage_dirty = 0;
    song.playing=song.rec=rec_wait=ft_on=ci_on=0;transport_req=0;
}
void sloop_render(float *out, unsigned frames)
{
    static int32_t block[CTL*2];
    dx_perform_apply();
    for (unsigned f = 0; f < frames; f++) {
        if (pcm_pos == CTL) {
            mix_block(block, CTL);
            for (unsigned i = 0; i < CTL*2; i++) pcm[i] = clamp(block[i], -32768, 32767) / 32768.0f;
            for (unsigned i = 1; i < CTL; i += 2) {
                scope_bufr[scope_w & (SCOPE_N-1)] = block[2*i+1];
                scope_buf[scope_w++ & (SCOPE_N-1)] = block[2*i];
            }
            pcm_pos = 0;
        }
        out[2*f] = pcm[2*pcm_pos]; out[2*f+1] = pcm[2*pcm_pos+1]; pcm_pos++;
    }
}
static void editor_event(int kind, int id, int value);
void sloop_event(int kind, int id, int value)
{
    if (kind == SLOOP_BUTTON && id >= 0 && id < NB) {
        uint32_t bit = 1u << panel.btn[id];
        if (value) { if (!(fm1_in.buttons & bit)) edges_btn |= bit; fm1_in.buttons |= bit; }
        else fm1_in.buttons &= ~bit;
        keyboard_block(); ui_input();
    } else if (kind == SLOOP_ENCODER && id >= 0 && id < NE) {
        encs[panel.enc[id]] += clamp(value, -32, 32); ui_input();
    } else if (kind == SLOOP_KEY && id >= 0 && id < 27) {
        if (value) fm1_in.notes |= 1u << id; else fm1_in.notes &= ~(1u << id);
        keyboard_block(); ui_input();
    } else if (kind == SLOOP_MIDI) {
        if (mi_w - mi_r < MQ) midi_in_q[mi_w++ % MQ] = (uint32_t)value;
        else sloop_event(SLOOP_PANIC, 0, 0);
    } else if (kind == SLOOP_PANIC) {
            dx_perform_reset();
        fm1_in.notes = fm1_in.buttons = edges_btn = 0;
        mi_r = mi_w; memset(midi_sel_on, 0, sizeof midi_sel_on);
        panic_req = (1u << NTRK)-1u; keyboard_block(); ui_input();
    } else if (kind == SLOOP_MASTER) song.master_q12 = clamp(value, 0, 4096);
    else if (kind == SLOOP_STOP) transport_req = 2;
    else if (kind == SLOOP_CONNECTION) usb.config = usb.up = value != 0;
    else editor_event(kind, id, value);
}
void sloop_tick(uint32_t milliseconds)
{
    if(dx_reboot_requested){dx_reboot_requested=0;sloop_init(NULL);}
    fm1_ms = milliseconds; ui_input(); ui_leds(); ui_draw(); autosave_tick(); sections_flush();
}
void sloop_pixels(uint32_t *argb)
{
    for (unsigned i = 0; i < 240*240; i++) {
        uint16_t p = swap16(screen[i]);
        argb[i] = 0xFF000000u | (((p>>11)*255u/31u)<<16) | ((((p>>5)&63u)*255u/63u)<<8) | ((p&31u)*255u/31u);
    }
}
int sloop_playing(void) { return song.playing != 0; }
int sloop_checkpoint(void) {
    persist_flush_now();
    persist_t prefs; persist_fill(&prefs);
    if(st_save(OBJ_SETTINGS,&prefs,sizeof prefs) || dx_bank_store() || ukit_store())return 0;
    proj_capture(&autosave_buf);
    return st_save(OBJ_AUTOSAVE,&autosave_buf,sizeof autosave_buf)==0;
}
const uint8_t *sloop_storage(void) { return android_flash; }
int sloop_storage_dirty(void) { return storage_dirty; }
void sloop_storage_clean(void) { storage_dirty = 0; }
#include "studio.c"
