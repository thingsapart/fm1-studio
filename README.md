# FM1 Studio

Play **SLOOP, FM1 MOVE, Felucca and sloopDX** on Android, with
native firmware displays, touch controls, a piano roll, sound editors and MIDI.

## Credits

The synthesis engines, sequencing and native interfaces are the work of the
original firmware creators and their contributors:

- **[Felucca](https://github.com/hugelton/Felucca)** — **Leo Kuroshita (@kurogedelic), Hügelton Instruments**, the foundation of this firmware family.
- **[SLOOP](https://github.com/isod89/sloop-fm1)** — **isod89 and contributors**, building on Felucca.
- **[FM1 MOVE](https://github.com/Nicodesy06/FM1-MOVE)** — **Nicodesy06 and contributors**, building on SLOOP/Felucca.
- **[sloopDX](https://github.com/zvenson/dxsloop)** — **Sven Trogus**, building on SLOOP/Felucca, with the Dexed msfa core by **Google and Pascal Gauthier**.
- **[FM-1-RE](https://github.com/AL-255/FM-1-RE)** — **AL-255 and contributors**, whose M-VAVE FM-1 reverse-engineering research helped make independent firmware possible.
- **[Dinghy](https://github.com/zvenson/dinghy)** — **Sven Trogus and contributors**, documenting the hardware/firmware plumbing.

FM1 Studio adds the Android host, adapters and touch editing workflow.
Upstream sources and notices are preserved. See [credits and licenses](LICENSING.md)
for DSP, font, sample and platform dependencies. This project is independent of
M-VAVE and the upstream firmware authors; no endorsement is implied.

## Build

Python 3.10+ is required. Both build scripts print OS-specific installation
commands and check dependencies before starting; they do not install OS or
Python packages automatically.

```sh
git submodule update --init --recursive
python3 android/build.py --setup
# Run the printed setup commands, then:
python3 android/build.py --check-deps
python3 android/build.py
```

Android needs Pillow/fontTools, JDK 17 or 21, SDK platform 35, build-tools 35.0.0
and NDK 27.2.12479018. The setup commands use JDK 21. Set `JAVA_HOME` and
`ANDROID_SDK_ROOT` for custom installations. All four firmware sources are pinned
submodules under `fw-ext`; `SLOOP_SOURCE`, `MOVE_SOURCE`, `FELUCCA_SOURCE` and
`SLOOPDX_SOURCE` override them. Generated files stay under `build/`.

```sh
adb install -r build/android/fm1-wrapper.apk
python3 android/build.py --firmwares sloop  # optional single-engine build
python3 android/build.py --abi x86_64      # optional emulator build
python3 tests/run.py
```

The **ESP32-P4 port is internal and unfinished**. Its implementation notes are
kept in the [internal port documentation](esp32p4/README.md).

## Play and learn

Choose an engine in **Menu → Instrument Settings → Firmware**. Each engine keeps
its own projects and settings. Switching firmware loads the selected engine library.
Use **PANEL → SOUND** for instrument controls, **DRUM SOUNDS** for individual
SLOOP/MOVE drum voices, and **STEPS** for notes and one-shot chord placement.

- [Tutorials, with original creators credited](tutorials/README.md)
- [Control reference](tutorials/CONTROLS.md)
- [Controls, firmware definitions and implementation](docs/DEVELOPMENT.md)
- [Internal, unfinished ESP32-P4 port](esp32p4/README.md)

## License

Project code is **GPL-3.0-only** ([LICENSE](LICENSE)); third-party components and
assets retain their own terms. See [credits and licenses](LICENSING.md) for
component authors and applicable code, font and asset terms.
Binary releases must include applicable notices and complete corresponding source.
