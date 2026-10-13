# Soft-house beat (SLOOP 2.5)

Original source: [Soft-house beat (SLOOP 2.5)](https://www.reddit.com/r/MVaveFM1/comments/1x1ffol/tip_learning_using_new_mvave_fm1_firmware_fast/).
Original author / creator: [u/Tihci_01](https://www.reddit.com/user/Tihci_01/).

This is only a translation/adaptation for the **Android app** in FM1 Studio. Credit for the original material and musical ideas belongs to the creators above. Wrapper-specific substitutions are described explicitly; this is not an official replacement for their work.

Applies to: **SLOOP; sloopDX substitutions noted**. See [the shared control map](CONTROLS.md) before starting.

## Recipe and preparation

The source is u/Tihci_01's post, which discloses an AI-assisted recipe. Its musical target is a 116 BPM soft-house loop. The source's note/step data below is retained with credit; the wrapper instructions are translated here.

Select **SLOOP**, stop playback, and save existing work. Android: **MENU → MIDI / AUDIO / FILES → Export Android backup**, then **MENU → NEW PROJECT** and confirm. Tap **BPM → 116**. **PANEL → / PAGES → GLOBAL**: SWING 0, CLICK OFF; **SYSTEM**: SYNC INT.

REC stays off while programming the grid.

## 1. Softer kit

Android: select **DRUMS → PANEL → DRUM SOUNDS → CHOOSE EDITABLE KIT → SYN1**. Under **WHOLE KIT**, use **COPY FACTORY KIT**, confirm replacing the slot, and choose **D.HOUSE**. Use **NAME KIT** to enter **SOFTHOUS**. Select each sound in the fixed lane row: lower KICK's CLICK, shorten KICK's DECAY, shorten CLAP's NOISE DECAY, lower HAT's OUT LEVEL and shorten OPEN HAT's NOISE DECAY. Drag a dial to adjust; hold still for 750 ms and then drag to scroll. Double-tap for exact entry. Tap **▶** to audition the selected sound. These are adjustments by ear, not required numeric values. Choose **STORE ALL SYN KITS** and **USE THIS KIT ON DRUM TRACK**. Stop playback before storing.

## 2. Drum pattern

Android: select **DRUMS → STEPS → LEN 16**. Choose **PATTERN** in PANEL's page picker for DIV **1/16** and SWG **12**. Return to STEPS. Tap the following hits; hold each cell to change **LEVEL**. Keep RATCHET ×1.

The source's drum data:

> | Lane | Steps | Level |
> | --- | --- | --- |
> | KICK | 1, 5, 9, 13 | NORM |
> | CLAP | 5, 13 | SOFT |
> | HAT | 3, 7, 11 | SOFT |
> | OPEN HAT | 15 | SOFT |
> | SHAKER | 2, 4, 6, 8, 10, 12, 14, 16 | GHOST |
> | RIM | 12 | GHOST |

## 3. Chords

Stop playback. Select track 2 and **RHODES**. Android: track **▾ → preset** or **MENU → ALL FACTORY SOUNDS**. Set native **VOICE → VCE POLY**, **SEL → CHORD OFF**, **ARP → MODE OFF**, **SEL → TRN 0**. Set LEN 64, DIV 1/16, SWG 0.

In Android **STEPS → chord icon**, use the direct root/chord-type buttons, octave arrows and voicing arrows to make each chord. Set **LENGTH 2**. **▶** auditions it, and the lit keyboard and note names show its exact pitches. Choose **PLACE ON GRID**, then tap the numbered header or any cell in the column for the starting step. This places one chord and returns to normal note editing. Open the chord icon again for each placement; it remembers the previous chord. Two sixteenth-note steps match the source's eighth note. Insertion requires stopped playback and empty steps for the whole duration.

Use these visual-editor settings for the source's voicings:

| Chord | Root / octave | Type | Voicing |
| --- | --- | --- | --- |
| Am7 | A3 | MIN7 | Root position |
| Fmaj7 | F3 | MAJ7 | Inversion 1 |
| Cmaj7 | C3 | MAJ7 | Inversion 2 |
| G7 | G3 | 7 | Root position |

The source's chord data:

> | Chord | Notes | Starting steps |
> | --- | --- | --- |
> | Am7 | A3, C4, E4, G4 | 3, 11 |
> | Fmaj7 | A3, C4, E4, F4 | 19, 27 |
> | Cmaj7 | G3, B3, C4, E4 | 35, 43 |
> | G7 | G3, B3, D4, F4 | 51, 59 |

Set the Rhodes' distortion to zero, add quiet chorus/reverb, and shorten ENV REL if chords overlap too much. In Android use **PANEL → SOUND** for the envelope, voice, filter and effect controls; drag a dial to adjust; hold still for 750 ms before dragging to scroll. Use **ALL PAGES** for additional native groups. Use the track filter's low-pass side gently.

## 4. Bass and melody

Select track 1, **SUB BASS**, CHORD OFF, ARP OFF, TRN 0; LEN 64, DIV 1/16, SWG 0. Enter the source's bass notes with length 2:

> | Note | Steps |
> | --- | --- |
> | A3 | 3, 7, 11, 15 |
> | F3 | 19, 23, 27, 31 |
> | C4 | 35, 39, 43, 47 |
> | G3 | 51, 55, 59, 63 |

Do not add the extra two-octave transposition warned about in the source. Keep bass delay/reverb sends at zero initially.

Track 3 uses **MUTED GTR** in the PHYS engine, with the same 64-step settings and length 2. The source's melody:

> | Step | Note |
> | --- | --- |
> | 7 | E5 |
> | 15 | G5 |
> | 23 | A5 |
> | 31 | G5 |
> | 39 | E5 |
> | 47 | D5 |
> | 55 | B4 |
> | 63 | D5 |

Use STEPS again. Lower PHYS brightness if needed and keep this track quieter than the chords.

## 5. Space, pump, save

Android: choose **DLY** in PANEL's page picker; set TIME **1/8D**, with low FDBK, dark COLR and enough MIX to audition. Raise track 3's delay send gently. Choose **DRUMS → DLY** for a very quiet drum send. This echoes the entire drum track. Under **MASTER**, start DUCK near 25% and DUST near 6%, FILT neutral. These displayed percentages are preferable to treating percent values as raw integers. Balance levels under MIX.

Stop. Android: **MENU → PROJECTS / SECTIONS → SECTION A → SAVE CURRENT LOOP**, then export a fresh Android backup. Select each track, **MENU → PATTERN ACTIONS / UNDO → EXPORT MIDI FILE**. MIDI files retain notes, not the exact sound; capture the host audio separately if needed.

## If using sloopDX instead

This is a substitution, not the source's exact recipe. Select **sloopDX**; choose **808 FM** or edit **MY KIT** through its native drum lane editor. Use **EPIANO 1/2**, **SUB BASS**, and a DX7 **PLUCK/FLUTE** in place of RHODES, SUB BASS and PHYS MUTED GTR. It has no PHYS engine or SLOOP SYN slots. The step/chord data still fits its 256-step shared pool (64 + 64 + 64 + 16 = 208). Android's DX7 banks live under **MENU → DX7 VOICE / BANKS**. See [the sloopDX control sheet](17-sloopdx-cheat-sheet.md).
