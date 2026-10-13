# sloopDX cheat sheet: Android controls

Original source: [sloopDX cheat sheet: Android controls](https://dx7.designburgapps.com/cheatsheet.html).
Original author / creator: [Sven Trogus (zvenson)](https://github.com/zvenson).

This is only a translation/adaptation for the **Android app** in FM1 Studio. Credit for the original material and musical ideas belongs to the creators above. Wrapper-specific substitutions are described explicitly; this is not an official replacement for their work.

Applies to: **sloopDX**. See [the shared control map](CONTROLS.md) before starting.

## Core controls

| Original / musical operation | Android |
| --- | --- |
| Select track | Numbered tracks 1–3 / DRUMS |
| Sound / kit | Track ▾ → sound picker |
| Tempo | Top BPM |
| PLAY / REC | Top buttons; armed recording controls |
| Envelope/LFO/effect/arp pages | PANEL → / PAGES |
| Mixer | MIX |
| Step grid | STEPS, bank arrows, LEN, held-cell details |
| Undo / redo | MENU → PATTERN ACTIONS / UNDO |
| Sections/song | SONG / PROJECTS / SECTIONS |
| Full backup | Export/Restore Android backup |

sloopDX patterns extend to **128 steps per track**, with **256 steps shared across all four tracks**. Android publishes the actual remaining length budget; requesting a longer pattern clamps to the room left. The eight 16-step banks are pages of one pattern, not clips.

## DX7 voice editor

Android: select a synth track, **MENU → DX7 VOICE / BANKS → EDIT DX7 VOICE / FM DRUMS**.

1. **SELECT** highlights a row. **ALGO** changes its value. On the Algorithm row, the first turn only opens the diagram; the next turns change the algorithm.
2. **EDIT** opens an OP1–OP6 group or an action. The native screen asks for confirmation for actions that require it. **HOME** backs out one level.
3. **PRESETS** moves between operators. **On / Solo** auditions operators without replacing their saved levels. K1 **CUT**, K2 **RESO** shape the voice's post-FM filter.
4. A change to a factory voice copies it into a user slot. **SAVE** stores the selected bank. Stop transport before storing to persistent storage.

The native list contains algorithm, feedback, oscillator sync, transpose, six operators, pitch envelope, LFO, name, copy/init/store and more pages. Operators include output, coarse/fine/detune, frequency mode, rates/levels and keyboard/velocity scaling. Use these lists rather than SLOOP's FM6 sheet: the two engines are distinct.

## Eight DX7 banks on Android

**MENU → DX7 VOICE / BANKS → BANK 1–8** offers **SELECT BANK**, **IMPORT 32-VOICE CARTRIDGE**, and **EXPORT CARTRIDGE**. The importer accepts a valid 32-voice DX7 SysEx cartridge or packed bank; exported cartridges include a Yamaha-format SysEx header/checksum. The active bank's voices follow the factory sounds in the sound picker. A separate **IMPORT DX7 PATCH / EXPORT DX7 PATCH** operates on the selected synth track's voice.

## FM drums and OMNI

Track 4 kits: **DX KIT, 808 FM, ELECTRO, METAL, MY KIT**. SLOOP's sampled kits and SYN1–SYN4 editor are not included.

For native lane editing, select track 4, HOME, SEQ to the drum grid, then EDIT through KIT into LANE. A played drum selects the lane. K1–K4 on the lane page control TUNE/DECAY/SWEEP/BRIGHT; PRESETS selects its additional macro pages. The native dice and MY KIT store actions remain in these pages. SAVE twice from KIT/LANE stores MY KIT. On Android this is reached through HARDWARE PANEL; STEPS also supplies hit level/ratchet editing. Generic SLOOP nudge/fill/synth-lock editors are not offered for this engine. Its native drum tune/decay locks remain reachable through held SEQ + step, K4/PRESETS.

Set **ARP MODE OMNI** on a synth track for black-key chords and white-key strings. **FLW** makes another track's pattern follow the OMNI root. Use the native keyboard in Android. Keyboard swipes/glissandi here play strings; Android's hide/reveal gesture starts on the tab row or bottom gesture area.

## Native layers and differences

The familiar held FX/EDIT/ARP/SEQ/SEL/GLO/SAVE layers work through HARDWARE PANEL on Android. The hosted cheat sheet calls the scale button **SCL**; this wrapper labels its verified physical binding **SEL**. FX provides **sixteen** punch effects, not current SLOOP's 27. sloopDX has no SLOOP PHYS/NOISE/sample engines, generic fills or SLOOP FM6 bank. Native MIDI SYSTEM settings are present; Android supplies actual USB/BLE MIDI host routing.

The hosted cheat sheet inspected was labelled 3.3; this wrapper pins the repository checkout, whose native source can include later changes. Use the current native screen/parameter labels when they differ.
