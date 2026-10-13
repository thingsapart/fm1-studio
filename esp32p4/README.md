# ESP32-P4 port — internal and unfinished

This port is **internal and unfinished**. The instructions below are internal
development notes; hardware qualification and release work remain incomplete.

This includes a reusable ESP-IDF component and a generated standalone Klickwerk board image. It uses
the same C engine ABI and generated screen descriptors as Android. The existing
Klickwerk/SLOOP board port remains untouched.

## Build the Klickwerk board image

Use Python with Pillow/fontTools and PlatformIO with the existing pinned ESP-IDF
5.4.2/P4 platform. Set `KLICKWERK_SOURCE` to the board-driver checkout; in this
workspace it defaults to `../../klickwerk2` relative to the wrapper project.

Run `python3 esp32p4/build.py --setup` for installation commands tailored to your
host, or `--check-deps` to check prerequisites without starting a build. Missing
prerequisites also print these instructions automatically. PlatformIO downloads
the pinned ESP-IDF platform and toolchain during its first build; OS packages,
Python packages and the content-locked board sources are supplied separately.

```sh
export SLOOP_SOURCE=/path/to/sloop-fm1  # optional; defaults to fw-ext/fm1-sloop
export KLICKWERK_SOURCE=/path/to/klickwerk2
python3 esp32p4/build.py
```

`--firmwares move` builds only MOVE; the default includes all four. `--pio`
accepts an explicit PlatformIO executable. The generated project is
`build/p4-board/`; the image is
`build/p4-board/.pio/build/fm1_wrapper_p4/firmware.bin`.
`tools/p4_size.py` checks the actual 6 MiB factory limit and records all DRAM banks,
including DRAM1 which PlatformIO's headline size report omits.

The board template and storage helpers are included in this repository. The
original SLOOP P4 port is only an optional source of cached managed components
for offline builds, not a required source dependency. The board-driver content
lock is checked before generating the host.

The top firmware dropdown selects an engine, commits its own SD image, writes
the next id to NVS and reboots. Stop transport before saving/switching. SD images
use `fm1-<id>-a.bin` / `fm1-<id>-b.bin`, separate from the existing SLOOP files.
No internal filesystem is formatted as a fallback. Touch buttons/relative
encoders and white/black keys use the same generated profiles as Android; the
Psion keyboard uses the physical FM-1 button ABI and Fn note/wheel mappings.

## Use the component in another board host

Run `python tools/prepare_p4.py --firmwares sloop` from the wrapper project and add
`esp32p4/components/fm1_wrapper` to the board's `EXTRA_COMPONENT_DIRS`.
Enable `CONFIG_SPIRAM_ALLOW_BSS_SEG_EXTERNAL_MEMORY=y` in the board configuration.
Generated source overlays place the original large `.pool` arrays in PSRAM.
Every public bridge function has a firmware prefix, allowing distinct engines to
link without colliding. Unknown or unverified adapters fail preparation.

The board's single engine worker uses the registry:

```c
#include "registry.h"
const fm1_definition *definition = fm1_firmware_find("sloop");
const fm1_firmware *engine = definition->entry();
engine->init(saved_image);  /* NULL for a new image */
engine->event(FM1_ENCODER, 3, 1);
engine->render(stereo_float, frame_count);
engine->tick(monotonic_milliseconds);
engine->pixels(argb_240_by_240);
```

LVGL widgets can be built directly from `definition->screens`: each control has
a label, event kind and target; encoders send relative detents, buttons and keys
send press/release. Screen navigation sends its `enter` event list. The board
owns audio conversion/output, framebuffer scaling, keyboard ownership and its
storage task. Those hardware integrations are still required.

For selection in a combined image, persist a firmware id and reboot into it.
Each statically linked engine retains its own globals; a reboot provides the
clean initialization that Android gets by unloading its native library.
Do not enable live P4 switching until each adapter's full reset has been tested.
Keep separate CRC/generation-checked SD images per firmware id and do not
initialize the next engine until the previous checkpoint has committed.

The combined four-engine image has been built and fits the existing 6 MiB factory partition. The linker map remains the authority for each build:
all compiled engines occupy flash and their static state occupies RAM, even when
inactive. Android plugin unload behavior does not apply to ESP-IDF. The current
four-engine image is 3,143,440 bytes with 229,644 bytes of static internal DRAM
data/BSS and 6,149,632 bytes of external BSS. Runtime heaps and stacks are
additional. No flash, audible audio, touch, keyboard or power-loss qualification
has been performed.

The four ids are `sloop,move,felucca,sloopdx`. sloopDX uses the native DX7 voice
editor (EDIT / SELECT / ALGO / PRESETS / HOME / SAVE), FM drum macros and its own
eight-bank storage. P4 exposes native controls, not Android's DX7 file pickers.
The translated workflows and source credits are in [tutorials](../tutorials/README.md).

If a global PlatformIO installation has incompatible Python/SCons packages, use
a project-local environment and select it with `--pio`. The recorded four-engine
build used Python 3.13, PlatformIO 6.1.19 and the existing project-local cache:

```sh
python3.13 -m venv build/pio-env
build/pio-env/bin/python -m pip install 'platformio==6.1.19'
PLATFORMIO_CORE_DIR="$PWD/build/pio-core" python3 esp32p4/build.py --pio build/pio-env/bin/pio
```
