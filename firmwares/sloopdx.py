"""sloopDX: its actual DX7 engine, FM drums and native voice editor."""
from dataclasses import replace
from .schema import Firmware, Screen, ENCODERS, tap
from .sloop import BUTTONS
from .studio import SLOOP_STUDIO, ParameterPage, MenuEntry

UI = replace(SLOOP_STUDIO,
    sample_slots=0,
    midi_groups=("SYSTEM",),
    nudge_min=0, nudge_max=0,
    features=("free_take", "arrangement", "dx7"),
    menu=tuple(x for x in SLOOP_STUDIO.menu if x.action not in ("samples", "fm6", "drumSynth")) +
         (MenuEntry("DX7 VOICE / BANKS", "dx7"),),
    pages=(ParameterPage("*"),),
)

FIRMWARE = Firmware('sloopdx', 'sloopDX', 'SLOOPDX_SOURCE', 'fw-ext/dxsloop', 'sloopdx', (
    Screen('Panel', BUTTONS + ENCODERS),
    Screen('DX7 voice', tuple(BUTTONS[i] for i in (4, 6, 7, 10, 12, 13)) + ENCODERS, tap(4)),
    Screen('Envelope', tuple(BUTTONS[i] for i in (2, 6, 10)) + ENCODERS, tap(2)),
    Screen('LFO', tuple(BUTTONS[i] for i in (3, 6, 10)) + ENCODERS, tap(3)),
    Screen('Effects', tuple(BUTTONS[i] for i in (0, 6, 10)) + ENCODERS, tap(0)),
    Screen('Scale / chords', tuple(BUTTONS[i] for i in (1, 8, 12, 13)) + ENCODERS, tap(1)),
    Screen('Global / mixer', tuple(BUTTONS[i] for i in (5, 6, 10, 11)) + ENCODERS, tap(5)),
    Screen('Sequencer / FM drums', tuple(BUTTONS[i] for i in (9, 4, 10, 11, 12, 13, 6, 7)) + ENCODERS, tap(9)),
    Screen('Performance', BUTTONS + ENCODERS),
    Screen('Projects', tuple(BUTTONS[i] for i in (7, 6, 10)) + ENCODERS, tap(7)),
), studio=UI)
