from .studio import FELUCCA_STUDIO
"""Felucca profile; adapters are distinct from SLOOP despite shared ancestry."""
from .schema import Firmware, Screen, button, ENCODERS, tap

BUTTONS = tuple(button(label, i) for i, label in enumerate(
    ('FX', 'SCALE', 'ENV', 'LFO', 'EDIT', 'GLOBAL', 'HOME', 'SAVE',
     'ARP', 'SEQ', 'PLAY', 'REC', 'OCT−', 'OCT+')))
FIRMWARE = Firmware('felucca', 'Felucca', 'FELUCCA_SOURCE', 'fw-ext/felucca', 'felucca', (
    Screen('Panel', BUTTONS + ENCODERS),
    Screen('Sound', tuple(BUTTONS[i] for i in (6, 2, 3, 4, 0)) + ENCODERS, tap(4)),
    Screen('Envelope', tuple(BUTTONS[i] for i in (2,6,10)) + ENCODERS, tap(2)),
    Screen('LFO / modulation', tuple(BUTTONS[i] for i in (3,4,6)) + ENCODERS, tap(3)),
    Screen('Scale / chords', tuple(BUTTONS[i] for i in (1,8,12,13)) + ENCODERS, tap(1)),
    Screen('Sequencer', tuple(BUTTONS[i] for i in (9,4,10,11,12,13)) + ENCODERS, tap(9)),
    Screen('Effects', tuple(BUTTONS[i] for i in (0,6,10)) + ENCODERS, tap(0)),
    Screen('Global / song', tuple(BUTTONS[i] for i in (5,6,10,11)) + ENCODERS, tap(5)),
    Screen('Projects / presets', tuple(BUTTONS[i] for i in (7,6,12,13)) + ENCODERS, tap(7)),
    Screen('Performance', tuple(BUTTONS[i] for i in (0, 1, 8, 5, 10, 11, 12, 13)) + ENCODERS),
), studio=FELUCCA_STUDIO)
