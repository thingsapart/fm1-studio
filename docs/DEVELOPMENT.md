# FM1 Studio controls and implementation

## Use it

**MENU → PANEL LAYOUT → FM-1 / Studio** selects the shared hardware surface or Studio controls. In FM-1, JP-1 Performance, or the JP-1 controller panel, the existing PANEL tab reads **< STUDIO** and returns directly to the standard Studio panel. **BACK TO STUDIO** is also the first MENU entry in these views. Portrait uses two 2 × 2 knob clusters (Master/Select/Presets/Algo and K1–K4), followed by two rows of six firmware-defined buttons. Landscape keeps K1–K4 in one row. The playing keyboard/pads dock across the bottom, with held octave buttons for piano keys and a collapse/reopen control in navigation. Drum pads hide the entire octave/key-bank row. The layout preference is shared across firmwares; keyboard visibility is remembered separately for FM-1 mode. zp12 defaults to this surface unless a layout has already been chosen. Hardware encoders use relative native events and optional firmware-provided labels/values; Master uses the existing volume preference. The panel reuses Studio knobs, buttons, gestures and generated firmware control bindings. Duplicate transport and track selectors are hidden; page navigation and MENU stay in a fixed bottom dock on every page. The navigation dock uses flat tabs, a selected-page underline, and a separating gutter instead of instrument-style raised buttons. FM-1 has no extra top toolbar. Knob dials remain circular at compact heights. PLAY pulses red once per second during playback. Pinch-to-zoom retains a separate FM-1 display size; native-display track selection uses the generated per-firmware hit areas, including zp12’s eight channel faders.

The full SLOOP Android instrument surface is shared across the four engines:
native display and display zoom, track and sound selectors, parameter knobs,
piano roll/drum grid, step details, mixer/solo/mutes, performance tools, projects,
user sounds, FM6 operators, sample building/chopping, MIDI routing/USB/BLE/output,
file import/export, backups, and instrument settings. The original styling,
sheets, gestures, multitouch keyboard and sample editor are retained.

Android **PANEL → SOUND** presents a common instrument overview from native parameter metadata, including a live normalized ADSR sketch where available. **ALL PAGES** retains the full native groups. Start a drag inside the painted circular dial (including its rim) or rounded choice box to tune it immediately, horizontally or vertically. Labels, numeric readouts and surrounding space scroll normally; entering a dial after starting outside does not acquire tuning. Hold still for 300 ms, allowing small finger jitter, then drag to scroll; a blue glow and SCROLL READY cue mark the handoff. A tuning gesture stays in tuning mode until release. Double-tap for exact entry. Operator numeric controls retain their direct sliders. On SLOOP/MOVE drum tracks, **DRUM SOUNDS** opens a separate editor with fixed kit/lane selectors, ▶ audition, tone/noise envelope sketches and controls for the selected sound. Its heading distinguishes the editing kit from the playing kit; kit copy, assignment and storage are explicit actions. The general SOUND overview remains available and labels shared controls. Each firmware can override `Studio.sound_pages` to select and order its overview groups; missing groups are omitted and duplicate parameter identities are shown once.

**STEPS** keeps row and column headers visible while scrolling. The synth piano roll draws each onset as a separate block with an attack mark, and held notes/chords as continuous bars across tied steps. Bars continue cleanly across 16-step bank boundaries. On synth tracks, the small stacked-note **chord icon** in the step toolbar opens a visual editor with direct root/chord-type buttons, a lit keyboard preview, octave/voicing arrows, ▶ audition and shared duration. **PLACE ON GRID** readies one chord. Tap a step number or any cell in its column to stamp the chosen pitches once; the pressed column and notes preview the target, and placement then returns to normal note editing. The icon cancels pending placement, and the chosen chord is remembered. Insertion uses empty steps with playback and recording stopped and can be undone as one gesture. External MIDI recording remains available through the existing MIDI input; this chord sheet does not capture a held MIDI chord.

The display remains visible beside the controls in landscape and above them in
portrait. **Menu → FM-1 Studio Settings → Firmware** switches engines; there is
no firmware selector in the main instrument surface. Display layout, zoom and
MIDI preferences are kept separately for each firmware.

### Jamjum JP-1 over Bluetooth

Pair the JP-1 in Android first, then use **Menu → MIDI / AUDIO / FILES → Connect Bluetooth LE MIDI**. Paired MIDI devices appear immediately even when they no longer advertise; pairing its keyboard service alone does not open its MIDI service. FM1 Studio opens a paired JP-1 automatically when Bluetooth permission is already granted, remembers the chosen Bluetooth MIDI device per firmware, reconnects on returning to the foreground, and closes its ports on leaving. **Disconnect MIDI** disables automatic reconnection until another Bluetooth connection is selected.

The JP-1 profile was checked against the connected Pixel 9 Pro and its current controller preset. Buttons send CC 19–24 on channel 1: **rewind → previous track**, **BT → mute/unmute selected track**, **forward → next track**, **play → play/pause**, **stop → stop and release notes**, **rec → record/finish the selected track**. Rewind and forward alternate values on successive presses; BT sends a momentary press/release pair; Play, Stop and Rec send the same positive CC value on every press. Press-only messages must not be suppressed as held buttons. Standard MMC start/stop/record commands are also accepted.

The tested knob banks send absolute CCs **A: 46–53, B: 30–37, C: 38–45**, all on channel 1. Positions follow 1L, 1R, 2L, 2R, 3L, 3R, 4L, 4R. The upper four retain **SELECT/page, TRACK, PRESET, TEMPO** in every bank. The lower four use the native metadata for **A: current page's first four controls (Home: Swing, Level, Steps, Pan); B: selected Sound page’s first four controls; C: Pattern controls (Length, Division, Swing, Gate)**. The first knob movement reveals eight compact, touch-editable controls in reading order. The pad-layout sheet also provides **Show knob controls**, so the last observed bank can be opened without changing a parameter. Bank buttons emit no MIDI or HID message, but the vendor status query described below now updates bank selection without moving a knob or playing a pad. Each bank has an independent baseline; its first absolute message establishes position without a parameter jump. Subsequent absolute messages map the controller's 0–127 position to the assigned control's full native range, including both endpoints. Absolute parameter controls use pickup (SELECT/page and TRACK navigation are exempt): no edit is sent until the mapped physical position reaches or crosses the current value. Bank, track, page and preset changes re-arm pickup, as do touch edits and external parameter changes. The control shows ↑ PICKUP or ↓ PICKUP while waiting; discrete choices pick up anywhere in their mapped choice range. This avoids the previous delta-conversion drift that could strand a parameter at a random value when the controller saturated. Discrete controls map to their declared choices. The SELECT/page knob keeps its requested page across control-view rebuilds. Delayed native snapshots cannot roll navigation back during a turn; relative page messages also advance from the requested page. Changing bank, track, engine or the available page list resets the pending page context. SELECT responds to the next changed absolute position without pickup, so changing tracks cannot leave page navigation waiting for a crossing. HOME and virtual KIT pages remain distinct even when their native page IDs match. A brief one-unit direction reversal is filtered, and stale native snapshots cannot overwrite a just-sent knob value for 250 ms. Hardware edits retain the formatted label until the native echo arrives, rather than briefly showing an internal raw number as if it were the displayed percentage or unit. Relative formats remain available for controllers actually programmed to send relative messages; selector roles advance one item per relative message. The settings can learn the first CC of banks B/C for other controller presets.

The controller pane names the selected track and associated page. **A → Pages** chooses the native editor context; **B → Pages** chooses an available Sound section for the current firmware/track without changing A’s native editor context; **C → Steps** opens the grid. B remembers its selected section while it remains available; track/engine changes fall back to the first available Sound section. Eight knobs remain in controller reading order, with the lower four bound to the displayed section. The two-line JP-1 status labels the live knob bank (or LAST KNOBS when vendor status is unavailable) and shows both observed bank letters and the active pad action: step range with FOLLOW/FIXED, drum layout/lane, or playable piano pitch range and octave offset. Tap it for a labeled 4×4 pad layout, shortcuts to the step grid or piano/drum surface and knob controls, and a direct pad-mode chooser. Empty fixed step banks say **NO STEPS IN THIS BANK** instead of showing a reversed step range. Octave-control corners are labeled explicitly. Repeat/Erase status retains its tap-to-disable behavior. Bank-button-only changes now update the controls and pad layout through vendor status replies.

Pad mode is selectable in **JP-1 controller mapping → Pad mode**. **Auto** plays notes/drums when the bottom keyboard is open, and plays individual drum sounds on the KIT overview even with the keyboard hidden; elsewhere it toggles steps with the keyboard hidden; **Play** and **Steps** lock the behavior. Holding STOP opens/closes the keyboard. Physical pads in reading order toggle steps 1–16 in bank A, 17–32 in B, and 33–48 in C; steps outside the current length are ignored. Drum step edits target the last auditioned lane (or the explicitly selected drum-editor lane), and synth steps use the last played pitch. Pattern edits are disabled during recording. In normal synth Play mode the bottom-left pad transposes all banks down an octave, and the top-right pad transposes all banks up; those two pads are reserved for octave control. Other pads keep their chromatic MIDI pitch plus that offset. Out-of-range incoming pitches temporarily reveal their octave in the piano and update OCT; after held notes release, the normal firmware keyboard returns. Matching incoming notes light their keys. Held notes remember their destination through track changes; changing pad mode or keyboard visibility releases them.

On drums, the pull-up surface uses a **4 × 4 grid matching the JP-1's physical reading order**. Consecutive pad-note banks reuse this layout rather than choosing the nearest GM note. The tested bank sends notes 52–67 on channel 10; the top row sends 64–67 and the bottom row 52–55. Both MIDI and touch pads use the firmware's declared drum notes, and incoming held pads light the same cells. SLOOP's common layout is:

| Pad row | Left | Centre left | Centre right | Right |
| --- | --- | --- | --- | --- |
| Top | Kick | Kick 2 | Snare | Clap |
| Second | Closed hat | Open hat | Pedal hat | Rim |
| Third | Snare 2 | Low tom | High tom | Crash |
| Bottom | Ride | Shaker | Conga | Cowbell |

Other firmware profiles use their declared lanes; Felucca exposes eight lanes, so the lower eight JP-1 positions do not trigger duplicate lanes. **JP-1 controller mapping** supports learning each button/knob or pad-base note, changing absolute/relative knob formats and button styles, and resetting to the tested preset. JP-1 assignments are shared across firmware choices. Controller-local **FULL LEVEL**, **16 LEVEL**, and **NOTE REPEAT** remain effective because received note messages are preserved: FULL LEVEL forces velocity, 16 LEVEL makes the pads play one note at different velocities, and NOTE REPEAT emits repeated on/off pairs. The normal 4×4 drum layout applies outside the controller's special 16 LEVEL mode. The tested pad banks use A: 52–67, B: 68–83, C: 36–51; all share the same drum-cell reading order and retain different chromatic pitches on synth tracks.

Long presses send distinct messages: **CC 25/26** browse previous/next editor pages repeatedly while held, **CC 27** sets FM-1 Repeat where supported, **CC 28** shows/hides the bottom keyboard, and **CC 29** undoes the last track edit when playback and recording are stopped. Releasing a page-browse button or disconnecting the controller cancels navigation. SHIFT did not expose an independent MIDI or HID modifier in the live tests; shifted controls either retained their ordinary messages or were handled locally by the controller. No software Shift state is inferred from those indistinguishable events.

**Bluetooth LEDs:** Standard channel-10 Note On/Off pulses did not visibly change this JP-1's pads. The Pixel exposes a separate vendor BLE service `0000ae40-0000-1000-8000-00805f9b34fb`, with write-without-response characteristic `AE41` and notification characteristic `AE42` (same UUID base). **JP-1 controller mapping → Test Bluetooth pad LEDs → Vendor RGB** sends red, green, blue, then off, holding each color for three seconds. It uses the KuSuite RGB command documented in [padmon's JP-Mini implementation](https://tangled.org/faz.ms/padmon/blob/main/jpmini.py), with its MIT notice retained in `LICENSES/MIT-padmon.txt`. On 2026-10-13, the Pixel 9 Pro successfully queued all four frames and the user confirmed visible red, green, then blue/purple-ish pads on the connected JP-1. This verifies programmatic LED activation over the vendor service. The final all-off frame also completed at the transport level. Per-pad physical ordering and all three banks have not yet been visually checked on this JP-1. **Vendor RGB · pad order** lights one white pad at a time, left to right from the top row to the bottom row, then clears the pads; this gives a reproducible nine-second physical-order check. Automatic mode-aware feedback is enabled by default; **JP-1 controller mapping → Automatic pad LED feedback** switches it off.

`Jp1Rgb.frame(int[16])` encodes independent `0xRRGGBB` colors in hardware slot order (bottom-left first, left to right, then upward according to the JP-Mini reference). `Jp1Rgb.slot(readingOrder)` maps top-left reading order to that slot order. The packet is `78 59 51 37 00 00`, followed by a 55-byte payload (`FF` plus 18 green/red/blue triplets; the last two triplets are zero), then `~sum(payload) & 255`. Encode the complete packet as a continuous least-significant-bit-first 7-bit stream, wrapped in `F0` / `F7` (73 bytes total). Send on **AE41 in 20-byte chunks, without BLE MIDI timestamps**. `Jp1BleLights` enables AE42 notifications, serializes writes through Android callbacks, and submits each subsequent chunk immediately after its callback. The vendor connection coexists with Android MIDI input. Automatic feedback keeps it open in the foreground, retries failed connections at most every five seconds, and clears addressed pad slots before closing on suspension or disconnect (best effort with a 1500 ms limit). Manual tests suspend automatic updates and normal feedback resumes after the test. Connection setup and stalled frame writes have timeouts. GATT write callbacks confirm local transport progress, not physical LED activation. The reference-vector tests cover framing, checksum, color byte order, and slot mapping.

**Follow playhead**, enabled by default in the JP-1 mapping menu, shows the active 16-step window while playing and maps step-pad edits to that same window, regardless of the hardware pad bank. It retains the last window when playback stops; an incoming pad-bank change while stopped selects its fixed window. Switching Follow off restores A:1–16/B:17–32/C:33–48. On-screen step grids follow the same window. The window base colors are green (1–16), cyan (17–32), purple (33–48), and amber (49–64); the playhead remains white.

Automatic colors describe the same pad actions as Auto/Play/Steps, including the KIT overview’s Auto play behavior: **Steps:** green enabled hits, blue synth ties, amber automation on an otherwise empty step, white playhead, red armed/recording feedback; pads beyond the pattern length are off. Drum steps follow the selected drum sound, and synth steps reflect the existing whole-step toggle action. **Drum Play:** dim colors by percussion family, amber selected lane, bright played sounds; individual muted lanes and muted/solo-excluded tracks use red idle colors. Unused lanes stay off. **Piano Play:** white/black pitch classes, green C notes, bright played notes, blue bottom-left octave down and amber top-right octave up (dim at limits). Live controller/touch/native keyboard highlights persist for at least 140 ms. Routing, layout labels and LED feedback share the same effective pad mode. Entering or leaving the KIT overview releases held notes when Auto changes between Play and Steps; selecting explicit Play or Steps still locks that behavior. In fixed-window mode, vendor bank-status replies update the pad window and feedback without a pad press.

Updates now retain one latest **bulk palette**: sixteen pad colors plus two tracked bank indicators, sent atomically as index FF in one 73-byte frame. This replaces the slower per-pad updates. The default slot assignment is 16: knob bank / 17: pad bank; **Bank LED slots** in the mapping menu swaps them for hardware verification. Bank colors are A:red, B:green, C:blue. The latest knob/pad bank is persisted and refreshed from live vendor status. MIDI inference remains the fallback when status is unavailable. Writes request high Bluetooth connection priority, submit chunks immediately after callbacks, cap frame starts at twenty per second, and coalesce obsolete pending state. A five-second repaint recovers local lighting. Pausing/disabling clears all RGB slots within a 1500 ms best-effort deadline.

**Live bank status (verified 2026-10-13):** KuSuite 1.0 (`org.qtproject.kuwee.kusuite`, version code 1), installed by the user on the Pixel, exposed a read-only system-settings query. Inspection of its native `CJMP1` status handling identified the fields; button-only captures confirmed both independently. Send raw command `78 59 66 00 00 00 FF` through the same continuous 7-bit encoder as RGB: the AE41 write is **`F0 78 32 19 03 00 00 40 7F F7`**. AE42 returns a **raw**, potentially fragmented packet: `78 59 50 20 00 00`, 32 payload bytes, and `~sum(payload) & 255`. Payload offsets **2 = pad bank, 3 = knob bank**, each 0/1/2 for A/B/C. The initial capture changed knob 0→1 at 21:53:16.934 and pad 0→1 at 21:53:19.961 without intervening MIDI. The user subsequently confirmed that the integrated display and bank lights follow bank-button presses. AE10 only returned four zeros and is no longer polled.

The app requests status every 100 ms, serialized between complete RGB frames, with only one outstanding query. Observed replies took 23–55 ms; this is polling, not an unsolicited button notification or a measured worst-case latency bound. A 500 ms reply timeout marks status unavailable and backs off for five seconds. Checksums, packet lengths and bank ranges are validated before applying replies. Newer MIDI bank observations take precedence over an older query snapshot. While vendor status is live, neither knob CC ranges nor pad-note ranges override its bank selection: programmed controls can overlap another bank’s assumed range. Knob edits and value filters use the reported bank plus the physical knob slot. MIDI-based bank inference resumes if status times out. A follow-up knob-turn test exposed repeated MIDI/status contention; the same live-status authority is therefore applied to both knobs and pads. Status tracking stays active with automatic lighting disabled. No system-settings write is sent. `Jp1StatusTest` covers the captured packet, fragmentation, malformed input, reconnection reset, query bytes and polling/backoff.

The response-path review also removed forced LED repaints for unchanged pad status and note feedback, repeated status text assignments, and duplicate absolute-knob edits. Selector values that still map to the current page/track/preset no longer retrigger navigation or release touch notes. Bank selection is processed before the parameter update, including the first baseline-only message. Knob edits pause while a modal sheet is open; after a bank change behind a sheet, the next knob message rebuilds the matching controls before editing, preventing edits through stale bank bindings. The latest-palette queue and twenty-frame-per-second limit remain; this is a transport budget, not a measured input-to-sound latency guarantee. Bulk RGB includes the two bank indicators, now refreshed from live status. Selective writes remain unvalidated.

Remaining latency work: JP-1 routing still goes through the Android main thread; the audio worker requests AAudio low-latency mode but buffers six hardware bursts, publishes editor snapshots every 50 ms, and the view polls them every 33 ms. LED frames start at most every 50 ms. These separate queues mean the current implementation is not proven minimum-latency. Profile event-to-audio and event-to-LED timing under load before reducing the audio buffer or moving routing off the UI thread; smaller buffers must be checked for underruns across firmware engines. Absolute-knob pickup is another useful follow-up: current full-range mapping can jump on the first edit after the baseline or after a track/context change.

The track down arrow reveals the engine/instrument (or drum kit) selector in its own panel above the workspace, including while the keyboard is open. It hides after six seconds of inactivity; interacting with the selector refreshes the timer and open picker sheets postpone hiding. The arrow has a 44 dp wide touch target when space permits; in the compact rail it shrinks to reserve the track number’s measured width at maximum display zoom.

Swipe up from the PANEL / STEPS / MIX / … tab row or bottom edge to reveal the
keyboard/pads; swipe down from that row or the keyboard area to hide them.
Landscape gives the native display the full available height, including space
for camera clearance. MOVE's compact track rail starts at the top of its safe
area with small gaps between the eight buttons. Track selection on the native display uses source-defined
horizontal rows for SLOOP and vertical columns for MOVE. MOVE clip-grid bounds override the play-screen bounds in the same declaration.
Compact SLOOP buttons share those exact row boundaries; MOVE buttons divide the available rail evenly.

MOVE has eight evenly spaced track buttons and scrollable mixer strips, six clips per track, scene launch,
groove templates, two project slots and three sample slots. Its native fourth
sample region contains project storage and is never exposed for sample import.
sloopDX runs its own Dexed-derived DX7 engine, 16-lane FM drums, native voice/
operator editor, eight 32-voice banks and patterns up to 128 steps with a 256-step
shared pool. Android exposes **Menu → DX7 Voice / Banks** for native voice editing
and DX7 patch/cartridge import/export. Its storage has no sample engine; SLOOP's
sample/SYN/FM6 tools are not offered for it. P4 retains the native editor/bank
selector but has no Android file-picker UI.

Felucca has four interchangeable instruments, eight drum pads/lanes, step
probability, its native nudge range and motion-backed parameter locks. Its song
workspace edits the native per-track project chain. SLOOP retains its song
arranger and dedicated drum/synth-kit editors. Firmware capabilities control
which native operations appear: retired Felucca patch-bank and SLOOP-specific
drum-synth operations are not offered for that engine.

Firmware selection stops audio, releases input, checkpoints native storage and
loads the next engine. Android unloads the previous library, giving each engine
fresh static state on its next load. A rejected checkpoint, missing library,
invalid ABI, unreadable save or save failure prevents the switch and preserves
the current engine. The selection preference changes only after a successful
load. Returning to an engine restores its working project with transport stopped.

Android saves are separate app-private `sloop.flash`, `move.flash` and
`felucca.flash`, `sloopdx.flash`, `ghoulbox.flash` and `zp12.flash` images. File replacement uses a temporary file, flush and fsync.
Native object CRCs and formats remain the firmware's own. The Android selector
does not convert projects between firmwares or import the old SLOOP app's data.
Normal suspension checkpoints; an abrupt process kill can still lose unsaved edits.

## Define controls in one source file

Each firmware has one declarative UI definition:

- `firmwares/sloop.py`
- `firmwares/move.py`
- `firmwares/felucca.py`
- `firmwares/sloopdx.py`
- `firmwares/ghoulbox.py` (Felucca adapter with older-base compatibility and dungeon assets)
- `firmwares/zp12.py` (native panel, eight-channel mixer, MIDI and full-flash saves)

The `Studio` declaration controls the complete Android surface: ordered layout
components, tabs, menu routes, parameter pages and their field order, sheet
control order, labels, semantic event/operation bindings, firmware capabilities,
track names, track hit axes/bounds and button spacing, project/sample counts and
drum-note mappings. Shared defaults live
in `firmwares/studio.py`; each firmware source selects or overrides those defaults.
The text inventory is in `firmwares/studio_labels.py`.

For example, in a firmware definition:

```python
from dataclasses import replace
from .studio import SLOOP_STUDIO, ParameterPage

UI = replace(SLOOP_STUDIO,
    tabs=(("SOUND", "PANEL"), ("PATTERN", "STEPS"), ("MIX", "MIX"),
          ("LIVE", "PERFORM"), ("SONG", "SONG")),
    pages=(ParameterPage("ENV", "ENVELOPE", ("ATK", "DEC", "SUS", "REL")),
           ParameterPage("*")),
)
# Pass studio=UI to this file's Firmware(...).
```

Parameter values, choices, ranges and identities come from native descriptors;
the definition selects, orders and labels them without mirroring changing DSP
state. `ParameterPage.enter` provides explicit native display navigation where
needed, including MOVE's screen/page mapping. Menu routes choose shared editors;
reordering a sheet retains each control's original action. `Studio.bindings`
remaps semantic operation targets for defined derivatives.

The same source also defines hardware controls for the small P4 surface.
A `Screen` contains an ordered tuple of shared `Control` objects and an optional
`enter` event list. Controls declare their label, event kind and native target.
The renderers handle button/key ownership and relative encoder gestures. Java
and plain C descriptors are generated from exactly the same definitions.

```python
Screen('Envelope',
       (button('ENV', 2), button('HOME', 6)) + ENCODERS,
       tap(2))
```

Changing grouping, order, labels or bindings requires editing that definition
and rebuilding. This is build-time source configuration, not runtime code
execution. The firmware still renders its parameter labels/values and owns its
musical interactions and native page behavior. Android renders the declared
Studio components; P4 currently renders the declared hardware `Screen` controls.

To add a defined derivative, add a definition file and register it in
`firmwares/__init__.py`. It can reuse an adapter when its actual source ABI is
compatible: the plugin id is taken from the definition. Otherwise add an adapter
module in `tools/adapters/` implementing `verify(root)` and
`stage(root, output, definition)`. No Android shell changes are needed.

Adapters expose `native/firmware.h`: initialization, arbitrary-size stereo PCM,
events, ticks, pixels, transport state, native storage/checkpoint and MIDI output.
Firmware calls are serialized on one worker during playback; selection and
initialization can transfer ownership after that worker stops. An adapter must
return failure if its native checkpoint cannot accept a save. The package loader validates identity,
ABI and function pointers before replacing the active engine. The optional
`native/studio.h` extension supplies rich snapshots and validated native file
operations. Semantic editor events use a separate wire namespace from physical
button/encoder events. Global parameter identities start at 8192, keeping them
separate from all eight MOVE tracks. JSON serialization uses a published snapshot;
Java never reads live firmware globals.

## ESP32-P4 — internal and unfinished

This port is internal and unfinished. The following notes describe its current implementation.

See [ESP32-P4 build and integration](../esp32p4/README.md). The same source definitions
generate LVGL control layouts and a static firmware registry. The board host uses
the existing pinned Klickwerk panel, audio, SD and Psion keyboard drivers.

All compiled engines remain in flash/PSRAM. P4 switching saves the current native
image into an independent CRC/generation-checked SD pair, stores the next id in
its own NVS namespace and reboots. Rebooting gives the next engine clean state.
Live P4 switching is deliberately not enabled; a verified full reset for every
engine would be required. Failed saving leaves the current engine selected.

The built four-engine image is **3,143,440 bytes**, below the existing **6 MiB**
factory partition. Its ELF contains **229,644 bytes** of static internal DRAM
data/BSS and **6,149,632 bytes** of external BSS. `tools/p4_size.py` counts both
DRAM banks; PlatformIO's headline omits DRAM1 BSS. These counts exclude runtime
heaps, framebuffers, task stacks, IRAM code, TCM and RTC sections, and do not
establish audio timing or board behavior.

### JP-1 performance screen

Open **Menu → JP-1 Performance Screen** (or the controller pad-layout sheet). This view uses the full workspace for a large track/sound heading, transport and recording status, knob/pad bank letters, eight fixed knob positions (1L through 4R), and the four-by-four pad layout. Hardware adjustments briefly enlarge their control without changing its position. The display stays awake while this screen is open, and a disconnected controller is shown as **JP-1 UNAVAILABLE**. Step-pad edits and controller page/bank changes keep this screen open. Native display and other editors remain accessible through the tabs.

This is the first pickup/performance pass. The complete sound → record → overdub → undo → duplicate → pattern-switch hardware loop, bar-boundary pattern queues, and pad-to-audio latency/jitter measurements still require implementation and device validation. Existing bank-feedback timing does not measure audio latency.

### Controller page regression on Android

After building the app, `python3 tests/android_controller_build.py` builds a separate instrumentation APK. For a release app, pass `--release` to the test builder too so both APKs use the same certificate. Install `build/android/controller-tests.apk` alongside the current app, then run `adb shell am instrument -w org.fm1.studio.tests/.ControllerPageInstrumentation`. The test temporarily disconnects MIDI input and sweeps the actual SELECT control forward/backward in every bank, with slow and burst input, delayed native snapshots, and relative turns. It navigates pages in the current project without editing sounds and restores the original bank/page on success. Restart the app after instrumentation to reconnect the controller. The host suite also covers MIDI decoding, all selector sizes, pickup, stale echoes and context changes.

The Steps view reveals an edited step and its drum lane or note octave, including JP-1 edits. During playback it follows the selected track’s playhead across 16-step banks and scrolls horizontally only when the playhead leaves the visible cells. Sticky row/column labels are excluded from the usable viewport. Following pauses during grid touches and open editor sheets; playback does not change the selected drum lane or note octave.

For the Steps viewport device checks, run the same instrumentation command with `-e viewport true`. It checks actual scroll positions for offscreen edits, the bottom drum lane, playhead bank transitions, loop wrap and touch suppression without changing pattern data.

With a drum track selected, the controller instrumentation also accepts `-e pads true` to check every physical pad’s drum-note destination on KIT, no sequence-view navigation with the keyboard hidden, explicit Steps override, and leaving the overview.

JP-1 track-to-page regression: run `adb shell am instrument -w -e tracks true org.fm1.studio.tests/.ControllerPageInstrumentation` after building and installing the regression APK. It starts knob 2 away from the current track without acquiring pickup, traverses every visible track both ways and jumps between endpoints in every bank, then verifies knob 1 immediately reaches both page endpoints without crossing the new track’s current page. Track selection bypasses pickup so each track-change rebuild cannot interrupt the turn. The original track, bank and page are restored.

Firmware MIDI/JP-1 integration checks: after building the app and `tests/android_controller_build.py`, install both APKs and run `adb shell am instrument -w -e engine zp12 org.fm1.studio.tests/.FirmwareMidiInstrumentation`, then repeat with `-e engine ghoulbox`. The runner feeds the actual JP-1 decoder into the Activity and JNI, checks all three knob banks and pad destinations, and isolates/restores the firmware flash and JP-1 preferences. This verifies the software path on-device; physical BLE LED rendering still requires the connected controller.
