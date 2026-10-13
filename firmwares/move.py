from .studio import MOVE_STUDIO
"""MOVE native screens and physical bindings, matched to its upstream SC_* enum."""
from .schema import Firmware, Screen, button, ENCODERS, NATIVE_SCREEN

# Physical ids retain the SLOOP panel ABI; MOVE's native UI owns their behavior.
BUTTONS = tuple(button(label, i) for i, label in enumerate(
    ('PERFORM', 'TRACKS', 'ENV', 'LFO', 'SOUND', 'MIXER', 'HOME', 'CLIPS',
     'GROOVE', 'SEQ', 'START/STOP', 'REC', 'OCT−', 'OCT+')))
FIRMWARE = Firmware('move', 'FM1 MOVE', 'MOVE_SOURCE', 'fw-ext/fm1-move', 'move', (
    Screen('Panel', BUTTONS + ENCODERS),
    *tuple(Screen(title,tuple(BUTTONS[i] for i in controls)+ENCODERS,((NATIVE_SCREEN,n,0),))
           for n,title,controls in (
               (0,'Play',(6,10,11,12,13)),
               (1,'Tracks',(1,5,10,11)),
               (2,'Sequencer',(9,4,10,11,12,13)),
               (3,'Groove',(8,10,11)),
               (4,'Clips / scenes',(7,9,10,11,6)),
               (5,'Mixer',(5,1,10)),
               (6,'Sound',(4,2,3,1,6)),
               (7,'Perform',(0,1,8,5,10,11,12,13)),
               (8,'Project',(6,7,10,11,12,13)),
               (9,'Settings',(6,12,13)),
           )),
), studio=MOVE_STUDIO)
