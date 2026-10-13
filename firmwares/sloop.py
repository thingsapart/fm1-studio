from .studio import SLOOP_STUDIO
"""SLOOP screen organization and all physical bindings live in this source file."""
from .schema import Firmware, Screen, button, ENCODERS, tap

BUTTONS = tuple(button(label, i) for i, label in enumerate(
    ('FX', 'SEL', 'ENV', 'LFO', 'EDIT', 'GLO', 'HOME', 'SAVE',
     'ARP', 'SEQ', 'PLAY', 'REC', 'OCT−', 'OCT+')))
FIRMWARE = Firmware('sloop', 'SLOOP', 'SLOOP_SOURCE', 'fw-ext/fm1-sloop', 'sloop', (
    Screen('Panel', BUTTONS + ENCODERS),
    Screen('Sound', tuple(BUTTONS[i] for i in (6, 2, 3, 4, 0)) + ENCODERS, tap(6)),
    Screen('Envelope', tuple(BUTTONS[i] for i in (2,6,10)) + ENCODERS, tap(2)),
    Screen('LFO', tuple(BUTTONS[i] for i in (3,4,6)) + ENCODERS, tap(3)),
    Screen('Effects', tuple(BUTTONS[i] for i in (0,6,10)) + ENCODERS, tap(0)),
    Screen('Scale / chords', tuple(BUTTONS[i] for i in (1,8,12,13)) + ENCODERS, tap(1)),
    Screen('Global / mixer', tuple(BUTTONS[i] for i in (5,6,10,11)) + ENCODERS, tap(5)),
    Screen('Sequencer', tuple(BUTTONS[i] for i in (9, 10, 11, 4, 12, 13)) + ENCODERS, tap(9)),
    Screen('Performance', tuple(BUTTONS[i] for i in (0, 1, 8, 5, 10, 11, 12, 13)) + ENCODERS),
    Screen('Projects', tuple(BUTTONS[i] for i in (7, 6, 10)) + ENCODERS, tap(7)),
), studio=SLOOP_STUDIO)
