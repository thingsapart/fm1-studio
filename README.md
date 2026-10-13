# FM1 Studio

Run FM-1 firmwares **SLOOP, FM1 MOVE, Felucca and sloopDX** on Android, with
native firmware displays, touch controls, a piano roll, sound editors.

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

## Screenshots

<table>
  <tr>
    <td align="center"><a href="docs/screenshots/01-sloop-native.png"><img src="docs/screenshots/01-sloop-native.png" width="190" alt="Native display" /></a><br /><sub>Native display</sub></td>
    <td align="center"><a href="docs/screenshots/02-sound-editor.png"><img src="docs/screenshots/02-sound-editor.png" width="190" alt="Sound editor" /></a><br /><sub>Sound editor</sub></td>
    <td align="center"><a href="docs/screenshots/03-piano-roll.png"><img src="docs/screenshots/03-piano-roll.png" width="190" alt="Piano roll" /></a><br /><sub>Piano roll</sub></td>
    <td align="center"><a href="docs/screenshots/04-chord-editor.png"><img src="docs/screenshots/04-chord-editor.png" width="190" alt="Chord editor" /></a><br /><sub>Chord editor</sub></td>
  </tr>
  <tr>
    <td align="center"><a href="docs/screenshots/05-drum-grid.png"><img src="docs/screenshots/05-drum-grid.png" width="190" alt="Drum sequencer" /></a><br /><sub>Drum sequencer</sub></td>
    <td align="center"><a href="docs/screenshots/06-drum-sound.png"><img src="docs/screenshots/06-drum-sound.png" width="190" alt="Drum sound editor" /></a><br /><sub>Drum sound editor</sub></td>
    <td align="center"><a href="docs/screenshots/07-mixer.png"><img src="docs/screenshots/07-mixer.png" width="190" alt="Mixer" /></a><br /><sub>Mixer</sub></td>
    <td align="center"><a href="docs/screenshots/08-performance.png"><img src="docs/screenshots/08-performance.png" width="190" alt="Performance pads" /></a><br /><sub>Performance pads</sub></td>
  </tr>
</table>

<a href="docs/screenshots/09-landscape.png"><img src="docs/screenshots/09-landscape.png" width="800" alt="Landscape: SLOOP native display, mixer and keyboard" /></a>

| SLOOP | FM1 MOVE | Felucca | sloopDX |
| --- | --- | --- | --- |
| <a href="docs/screenshots/10-firmware-sloop.png"><img src="docs/screenshots/10-firmware-sloop.png" width="190" alt="SLOOP running a demo loop" /></a> | <a href="docs/screenshots/11-firmware-move.png"><img src="docs/screenshots/11-firmware-move.png" width="190" alt="FM1 MOVE running a demo loop" /></a> | <a href="docs/screenshots/12-firmware-felucca.png"><img src="docs/screenshots/12-firmware-felucca.png" width="190" alt="Felucca running a demo loop" /></a> | <a href="docs/screenshots/13-firmware-sloopdx.png"><img src="docs/screenshots/13-firmware-sloopdx.png" width="190" alt="sloopDX running a demo loop" /></a> |

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
