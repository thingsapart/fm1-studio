# Credits and licenses

FM1 Studio's code and adapted bridges are licensed under **GPL-3.0-only**
([LICENSE](LICENSE)). The original firmware, libraries, fonts and samples retain
their authors' copyright notices and the licenses listed below.

## Original firmware

The musical engines, sequencing and native interfaces are the work of the
original firmware creators and their contributors. Felucca is the foundation of
this family; SLOOP builds on Felucca, and MOVE and sloopDX build on SLOOP/Felucca. Ghoulbox builds on Felucca; zp12
uses the SLOOP/Felucca platform and sloopDX effects.
FM1 Studio supplies the Android host, firmware adapters and touch editing workflow.

| Firmware | Creator | Code license and upstream terms |
| --- | --- | --- |
| [Felucca](https://github.com/hugelton/Felucca) | **Leo Kuroshita (@kurogedelic), Hügelton Instruments** | GPL-3.0-only; [component and asset terms](fw-ext/felucca/LICENSING.md) |
| [SLOOP](https://github.com/isod89/sloop-fm1) | **isod89 and contributors** | GPL-3.0-only; [component and asset terms](fw-ext/fm1-sloop/LICENSING.md) |
| [FM1 MOVE](https://github.com/Nicodesy06/FM1-MOVE) | **Nicodesy06 and contributors** | GPL-3.0-only; [component and asset terms](fw-ext/fm1-move/LICENSING.md) |
| [sloopDX](https://github.com/zvenson/dxsloop) | **Sven Trogus and contributors** | GPL-3.0-only; [component and asset terms](fw-ext/dxsloop/LICENSING.md) |

| [Ghoulbox](https://github.com/jasonpersinger/ghoulbox-fm1-dungeon-synth) | **Jason Persinger**, with Felucca by **Leo Kuroshita / Hügelton Instruments** | GPL-3.0-only; [component and asset terms](fw-ext/ghoulbox/LICENSING.md), [author credits](fw-ext/ghoulbox/README.md#credits-and-licence) |
| [zp12](https://github.com/zvenson/zp12) | **Sven Trogus (@zvenson) and contributors** | GPL-3.0-only; [component and asset terms](fw-ext/zp12/LICENSING.md) |

Each firmware is pinned by its submodule. Individual source headers preserve the
credits and terms for third-party code and the firmware authors' adaptations.

## Reverse engineering and hardware research

**[FM-1-RE by AL-255 and contributors](https://github.com/AL-255/FM-1-RE)** documents
the M-VAVE FM-1's firmware and hardware. **[Dinghy by Sven Trogus and
contributors](https://github.com/zvenson/dinghy)** documents the independent
firmware plumbing. This research helped make the upstream firmware family
possible. These projects are credited as research references.

## Synthesis, fonts and samples

| Work and use | Authors | License |
| --- | --- | --- |
| Dexed msfa core, used for FM6 and DX7 synthesis | Google Inc. and Pascal Gauthier; integer-C ports by Leo Kuroshita and Sven Trogus | Apache-2.0; sloopDX's port/integration also GPL-3.0-only. [Apache text](LICENSES/Apache-2.0.txt) and source notices are included in Android packages. |
| DaisySP models used by the PHYS engine | Electrosmith and Emilie Gillet; fixed-point port by Leo Kuroshita | MIT; upstream `phys_dsp.c` retains the notice. |
| Rings sympathetic strings used by PHYS | Emilie Gillet; fixed-point port by Leo Kuroshita | MIT; upstream `phys_symp.c` retains the notice. |
| CrispyZebra oscillator waveforms used by PHASE | Leo Kuroshita / Hügelton Instruments | GPL-3.0; credited in upstream `eng_phase.c`. |
| klattsch, a design reference for the VOICE engine | Tony Gies | MIT upstream; identified by the firmware authors as a design reference with no code copied. |
| Terminus, used by native screens and converted for Android controls | Dimitar Toshkov Zhekov | SIL OFL 1.1; [full notice](android/assets/Terminus-LICENSE.txt). The generated Android font retains its OFL notice. |
| Inter and Inter Tight, used by native firmware interfaces | The Inter Project Authors | SIL OFL 1.1; notices remain in the respective firmware assets and license directories. |
| Fukiai icon font, rasterised for current Felucca's native interface | Hügelton Instruments | MIT; Felucca's `LICENSES/MIT-Fukiai.txt` and source notices apply. |
| VT323, rasterised for Ghoulbox's native dungeon interface | The VT323 Project Authors (Peter Hull) | SIL OFL 1.1; [full notice](fw-ext/ghoulbox/LICENSES/OFL-VT323.txt). |
| Pirata One, rasterised for Ghoulbox's wordmark | Rodrigo Fuenzalida and Nicolas Massi | SIL OFL 1.1; [full notice](fw-ext/ghoulbox/LICENSES/OFL-PirataOne.txt). The font is unmodified; wordmark bitmaps are pictures of text. |
| VSCO-2 Community Edition and VCSL instrument samples | Versilian Studios | CC0 1.0; upstream sample attribution files identify the recordings. |
| Sonic Pi sample material included by SLOOP/MOVE | Sonic Pi contributors, as credited upstream | CC0 1.0; provenance remains in upstream sample attribution files. |
| Current Felucca drum voices and Hügelton Sample Pack | Leo Kuroshita / Hügelton Instruments | GPL-3.0-only under the pinned Felucca terms; the sample pack is not CC0. |

SLOOP, MOVE and sloopDX terms separately identify their inherited
`assets/icons.png` atlas as **Copyright (C) 2026 Hügelton Instruments, all rights
reserved**, with asset terms to be published later. Their GPL section-7 permission
allows combining the assets with the GPL code subject to the assets' terms.
Those atlases are not covered by the code's GPL license. Current Felucca's
Fukiai-based icons have the MIT terms listed above.

M-VAVE, FM-1, Yamaha/DX7 and upstream project names belong to their respective
owners. FM1 Studio is independent; crediting these authors does not imply endorsement.

## Controller protocol

JP-1 experimental Bluetooth RGB encoding is adapted from the
[padmon / JP-Mini protocol by Flo](https://tangled.org/faz.ms/padmon/),
Copyright (c) 2026 Flo, under the MIT License. The full notice is included in
[LICENSES/MIT-padmon.txt](LICENSES/MIT-padmon.txt) and Android packages.

## Android runtime and project assets

The statically linked Android NDK C++ runtime carries **Apache-2.0 with LLVM
exceptions** and the permissive notices in the NDK toolchain's `NOTICE` file.
That notice is included in the APK. Android's platform APIs and system libraries
are provided by the operating system.

User-imported samples, projects and voice banks
retain their own rights and are not included in the app's factory assets.

The [tutorials](tutorials/README.md) credit the authors of each original guide,
recipe and performance. Referenced material retains its source-specific terms;
video links do not grant rights to copy the videos. Tutorial files are not bundled
in the APK.

