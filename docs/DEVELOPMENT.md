# FM1 Studio controls and implementation

## Use it

The full SLOOP Android instrument surface is shared across the four engines:
native display and display zoom, track and sound selectors, parameter knobs,
piano roll/drum grid, step details, mixer/solo/mutes, performance tools, projects,
user sounds, FM6 operators, sample building/chopping, MIDI routing/USB/BLE/output,
file import/export, backups, and instrument settings. The original styling,
sheets, gestures, multitouch keyboard and sample editor are retained.

Android **PANEL → SOUND** presents a common instrument overview from native parameter metadata, including a live normalized ADSR sketch where available. **ALL PAGES** retains the full native groups. Start a drag inside the painted circular dial (including its rim) or rounded choice box to tune it immediately, horizontally or vertically. Labels, numeric readouts and surrounding space scroll normally; entering a dial after starting outside does not acquire tuning. Hold still for 750 ms, allowing small finger jitter, then drag to scroll; a blue glow and SCROLL READY cue mark the handoff. A tuning gesture stays in tuning mode until release. Double-tap for exact entry. Operator numeric controls retain their direct sliders. On SLOOP/MOVE drum tracks, **DRUM SOUNDS** opens a separate editor with fixed kit/lane selectors, ▶ audition, tone/noise envelope sketches and controls for the selected sound. Its heading distinguishes the editing kit from the playing kit; kit copy, assignment and storage are explicit actions. The general SOUND overview remains available and labels shared controls. Each firmware can override `Studio.sound_pages` to select and order its overview groups; missing groups are omitted and duplicate parameter identities are shown once.

**STEPS** keeps row and column headers visible while scrolling. The synth piano roll draws each onset as a separate block with an attack mark, and held notes/chords as continuous bars across tied steps. Bars continue cleanly across 16-step bank boundaries. On synth tracks, the small stacked-note **chord icon** in the step toolbar opens a visual editor with direct root/chord-type buttons, a lit keyboard preview, octave/voicing arrows, ▶ audition and shared duration. **PLACE ON GRID** readies one chord. Tap a step number or any cell in its column to stamp the chosen pitches once; the pressed column and notes preview the target, and placement then returns to normal note editing. The icon cancels pending placement, and the chosen chord is remembered. Insertion uses empty steps with playback and recording stopped and can be undone as one gesture. External MIDI recording remains available through the existing MIDI input; this chord sheet does not capture a held MIDI chord.

The display remains visible beside the controls in landscape and above them in
portrait. **Menu → Instrument Settings → Firmware** switches engines; there is
no firmware selector in the main instrument surface. Display layout, zoom and
MIDI preferences are kept separately for each firmware.

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
`felucca.flash` and `sloopdx.flash` images. File replacement uses a temporary file, flush and fsync.
Native object CRCs and formats remain the firmware's own. The Android selector
does not convert projects between firmwares or import the old SLOOP app's data.
Normal suspension checkpoints; an abrupt process kill can still lose unsaved edits.

## Define controls in one source file

Each firmware has one declarative UI definition:

- `firmwares/sloop.py`
- `firmwares/move.py`
- `firmwares/felucca.py`
- `firmwares/sloopdx.py`

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
