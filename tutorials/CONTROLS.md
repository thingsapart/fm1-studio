# Shared control map

This is FM1 Studio's control translation for the original sources credited in [the tutorial index](README.md). It describes FM1 Studio on Android, checked on 2026-10-12. The musical ideas and original documentation remain their creators' work.

## Choose the right engine first

**MENU → INSTRUMENT SETTINGS → FIRMWARE**. Choose **SLOOP**, **FM1 MOVE**, **Felucca**, or **sloopDX**.

SLOOP tutorials assume three synth tracks and DRUMS on track 4. sloopDX has the same track roles but its own DX7 synths and FM drums. MOVE has eight tracks and clips; Felucca has four interchangeable instruments and eight drum lanes. A firmware switch keeps separate saves; it does not translate a project or sound into another engine's format.

## Android

**PANEL → SOUND** shows a firmware-defined overview of the available envelope, tone, filter, voice, effects and pitch controls. Start inside the circular dial or rounded choice box to tune it horizontally or vertically. Labels, numeric readouts and surrounding space scroll immediately; starting outside never acquires tuning. Hold still for 750 ms, with a small jitter allowance, then drag to scroll. The blue glow and SCROLL READY cue indicate scrolling. Double-tap for exact entry. The ADSR drawing is a normalized parameter sketch, not a measured audio waveform. **ALL PAGES** opens the native page selector used by the instructions below. On SLOOP/MOVE drum tracks, **DRUM SOUNDS** provides fixed kit/sound selectors, a ▶ audition button and individual sound controls. The heading identifies the editing kit and the kit currently playing. FM operator numeric sheets retain direct sliders.

**STEPS → chord icon** on a synth track opens a visual editor with root and chord-type buttons, lit piano keys, octave and voicing arrows, ▶ audition and duration. **PLACE ON GRID** readies one chord; tap a numbered step header or any cell in its column to place those exact pitches once. The pressed column and ghost notes preview its position. Placement returns to normal editing; tap the highlighted chord icon to cancel instead. Middle C is C4. Stop playback and recording before insertion; all affected steps must be empty. Step numbers and pitch/drum labels stay fixed within the grid viewport while scrolling. Separate note blocks and bright attack marks indicate repeated notes; a continuous bar with one attack mark indicates a held note.

The native display stays visible. **PANEL**, **STEPS**, **MIX**, **PERFORM**, and **SONG** are Android workspaces, not FM-1 hardware buttons. MOVE's last tab is **CLIPS**. Scroll the tab row horizontally if needed.

Swipe **up from the tab row or bottom edge** to reveal the piano/pads. Swipe **down from the tab row or keyboard area** to hide them. Tap a tab to change workspace. A swipe on a sequencer cell edits/scrolls that cell; start the reveal/hide gesture on the tab row.

Tap the numbered track button to select it. Tap its **▾** to reveal the engine/sound selectors; holding the track button opens sounds too. Tap the displayed sound name to choose a preset. The sounds row can fold away after inactivity or while the keyboard is open. For precise access use **MENU → SOUND / PRESET** or **ALL FACTORY SOUNDS**.

| Original instruction | Android equivalent |
| --- | --- |
| Turn ALGORITHM to track n | Tap track n. In sloopDX's native voice editor, ALGO edits a value instead; use track buttons to select a track. |
| Turn PRESETS to pick a sound/kit | Track **▾ → sound name**, or **MENU → SOUND / PRESET**. |
| SELECT sets tempo on HOME | Tap **BPM** and enter the value. |
| Tap ENV / LFO / FX / SEL / GLO / ARP | **PANEL → page selector** (the button ending **/ PAGES**), choose the native parameter group. **MENU → PAGES** reaches the same picker. |
| Turn KNOB 1–4 | Drag inside the dial or choice box horizontally or vertically; double-tap for exact entry. Hold still for 750 ms before dragging to scroll. |
| PLAY / REC | Top **PLAY / REC**. REC while stopped arms; the native recording mode determines what the first note does. During a free take the button becomes its completion action. |
| Program steps | **STEPS**. **LEN**, bank arrows and synth octave arrows select the grid area. Tap a cell to toggle a note/hit. Hold a cell for note length, velocity, level, ratchet and supported details. |
| EDIT + OCT− / OCT+ | **MENU → PATTERN ACTIONS / UNDO → UNDO / REDO**. |
| Hold REC to clear | **MENU → CLEAR SELECTED TRACK** and confirm. |
| GLO + keys to mute/solo | **MIX**, the selected engine's track strips and **MUTE / SOLO** controls. MOVE strips scroll horizontally. |
| FX + keys | **PERFORM → FX**, hold a named punch pad. Release restores the unprocessed sound. |
| SAVE + section key | **SONG → STORE / PLAY A–D**, or **MENU → PROJECTS / SECTIONS**. |
| Web editor backup | **MENU → MIDI / AUDIO / FILES → Export Android backup / Restore Android backup**. |
| Import/export project or sound | **MENU → PROJECTS / SECTIONS** or **USER SOUNDS**, choose a slot, then its import/export action. |
| Samples / WAV chopping | SLOOP: **MENU → SAMPLE SLOTS / FILES**. sloopDX has DX7 banks in this area and no sample-import menu. |
| Connect a MIDI keyboard | **MENU → MIDI / AUDIO / FILES → Connect USB / available MIDI** or **Connect Bluetooth LE MIDI**. Routing and outputs are separate choices in that sheet. |

For exact FM-1 combinations, open **MENU → HARDWARE PANEL**. It shows the real button labels plus **SELECT, ALGO, PRESETS, K1–K4**. Hold a button while touching keys or dragging an encoder. Reveal the keyboard first. Locking a layer with its button plus HOME is useful on a small screen. A **PANEL** tab tap is not a HOME button press.

On synth grids, the octave arrows change which note rows are visible; **OCT − / OCT +** under the keyboard change the played octave. Use the grid's octave overview to find occupied registers. Moving to another 16-step bank does not create a new pattern.

## Hardware-panel white keys for SLOOP and sloopDX

Use this table when following original key combinations through **MENU → HARDWARE PANEL**.

| White key | Note at octave 0 | Drum lane |
| --- | --- | --- |
| 1 | F3 | KICK |
| 2 | G3 | KICK 2 |
| 3 | A3 | SNARE |
| 4 | B3 | CLAP |
| 5 | C4 | HAT |
| 6 | D4 | OPEN HAT |
| 7 | E4 | PEDAL HAT |
| 8 | F4 | RIM |
| 9 | G4 | SNARE 2 |
| 10 | A4 | LOW TOM |
| 11 | B4 | HIGH TOM |
| 12 | C5 | CRASH |
| 13 | D5 | RIDE |
| 14 | E5 | SHAKER |
| 15 | F5 | CONGA |
| 16 | G5 | COWBELL |

In drum GRID/SEQ layers the same white keys address steps, not drum sounds. SLOOP kit names can vary; sloopDX kits are **DX KIT, 808 FM, ELECTRO, METAL, MY KIT**.

## Other wrapped engines

| SLOOP concept | MOVE | Felucca |
| --- | --- | --- |
| Four fixed track roles | Eight tracks; configure each track's type | Four interchangeable instruments; select the drum engine for drums |
| SAVE / sections A–D | CLIPS; six clips per track, scene launch; two project slots | Native song chain per track; four project slots |
| SLOOP physical EDIT / SEL / GLO | SOUND / TRACKS / MIXER | Use Felucca's own panel labels and native pages |
| Sixteen drum lanes | Engine-dependent sixteen-lane drum tracks | Eight drum lanes |
| SYN1–SYN4 and SLOOP samples | Capabilities and sample slots differ; three usable sample slots | No SLOOP drum-synth editor; three sample slots, motion-backed locks |

Select the tutorial's named firmware for an exact walkthrough. Porting only the notes to another engine requires choosing its sounds, drum mapping, storage and arrangement again. Use the firmware selector described above to return to the tutorial's engine.
