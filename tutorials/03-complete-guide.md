# Complete SLOOP guide: Android navigation

Original source: [Complete SLOOP guide: Android navigation](https://github.com/isod89/sloop-fm1/blob/main/GUIDE.md).
Original author / creator: [isod89](https://github.com/isod89).

This is only a translation/adaptation for the **Android app** in FM1 Studio. Credit for the original material and musical ideas belongs to the creators above. Wrapper-specific substitutions are described explicitly; this is not an official replacement for their work.

Applies to: **SLOOP**. See [the shared control map](CONTROLS.md) before starting.

## How to use the complete original

Keep the original guide open for engine theory, value ranges and musical details. The table below maps every major section to this wrapper's actual entry points. Most Android pages are selected with the **/ PAGES** button in PANEL. The shared [control map](CONTROLS.md) explains held combinations and gestures.

| Original topic | Android route / action |
| --- | --- |
| 1. Install/update | Install the wrapper APK; select SLOOP in INSTRUMENT SETTINGS |
| 2. Panel | MENU → HARDWARE PANEL |
| 3. Tracks, colours, tap/hold | Numbered track controls; native layers in HARDWARE PANEL |
| 4. First beat | [Translated walkthrough](02-first-beat.md) |
| 5. Buttons | Shared map; top PLAY/REC and MENU actions |
| 6. Playing keys | Swipe up on tabs/bottom; keyboard OCT and KEY BANK |
| 7. Drums | DRUMS track → STEPS; sound/kit picker |
| 8. Recording | Top REC; armed recording sheet controls |
| 9. Layers | HARDWARE PANEL for exact combinations; PERFORM for direct pads |
| 10. Steps, nudge, locks, fill | STEPS, hold cell → details; PERFORM → REPEAT for fill tools |
| 11. Key/scale/chords | PANEL → SEL / SEL 2; MENU → KEY / SCALE FOR ALL TRACKS |
| 12. Levels/mute/solo | MIX track strips; PERFORM tools |
| 13. Undo/clear/save | PATTERN ACTIONS / UNDO; CLEAR SELECTED TRACK; PROJECTS / SECTIONS |
| 14. Song/sections/chains | SONG, STORE/PLAY A–D, CHAIN, ADD SECTION, SAVE ARRANGEMENT |
| 15. Screens | Native display beside workspaces; MENU → HOME / TRACK CONTROLS |
| 16. Pages/knobs | PANEL → / PAGES; drag each descriptor-backed control; double-tap for exact entry |
| 17. Sounds/engines | Track ▾; ALL FACTORY SOUNDS; USER SOUNDS |
| 18. Effects | FX/FILTER/INSERT/DLY/REV/CHO groups; PERFORM → FX |
| 19. Visualiser | HARDWARE PANEL → HOME on native TRACKS; SELECT changes style |
| 20. HOME menu/lights | Native held HOME in HARDWARE PANEL; host display settings are INSTRUMENT SETTINGS |
| 21. MIDI | MIDI / AUDIO / FILES; INSTRUMENT SETTINGS → MIDI CHANNELS / SYNC |
| 22. USB audio | Android host audio output/capture, independent of FM-1 USB-audio firmware |
| 23. Samples/kits | SAMPLE SLOTS / FILES, DRUM SYNTH, FM6 OPERATORS / BANKS |
| 24. Web editor | Built-in Android Studio controls; browser mock is a separate tool |
| 25. Backup/rescue/stock | Export/Restore Android backup; reinstall APK; separate engine saves |
| 26. Cheat sheet | [Translated manual](04-manual.md), shared control map |

## Four practical editing paths

**Record:** select a track, reveal keys, REC. Check FREE/TEMPO and NOTE/COUNT before the first note. Overdub happens while playback continues; ending REC does not stop PLAY. Start from a blank project for a free take that determines tempo.

**Program:** choose STEPS, LEN and a 16-step bank. Hold a note/hit for its detail sheet. Synth note length is counted in steps; ratchet repeats occur within one step. Parameter-lock controls appear only for engines with that capability.

**Arrange:** save actual section snapshots, not just a file name. In SONG store A, change the loop and store B, add those sections with bar counts, select song mode and PLAY. Existing empty slots cannot play a section. Save the arrangement as well as the section projects.

**Design/import:** choose the sound first, then its native parameter group. FM6 patch operations are SLOOP's FM6 format, not sloopDX's engine. Samples, kits, user sounds and projects have different store/import operations. A full Android backup covers the selected engine's native image; changing firmware does not convert it.

## Version and hardware boundaries

Current upstream material includes features from SLOOP 2.6; older videos may show 2.2–2.5. Use parameter names published by the compiled engine rather than assuming old numeric page positions. The native HOME menu is distinct from Android's MENU. Android storage is app-private; FM-1 bootloader calibration, rescue, TRS jacks and flashing instructions apply to the physical FM-1. Do not run an FM-1 installer to install the Android app.
