"""Sven Trogus's zp12 sampling drum machine."""
from dataclasses import replace
from .sloop import FIRMWARE as SLOOP
from .studio import SLOOP_STUDIO, MENU, ParameterPage

UI = replace(SLOOP_STUDIO, tracks=tuple('CHANNEL '+str(i+1) for i in range(8)),
    track_axis="vertical", track_bounds=(4,100,236,162), track_screen_bounds=(),
    sample_slots=0, fixed_drum=-1, drum_lanes=16, drum_keys_physical=True,
    drum_notes=tuple(range(36,52)), midi_groups=(), features=("native_keys","sampler_pads"), sound_pages=("SOUND","TRUNC","OUT","SENDS","WAVE","FILTER","CHORUS","DELAY","REVERB"),
    tabs=(("PANEL","PANEL"),("MIX","MIX")), pages=(ParameterPage('*'),),
    menu=tuple(x for x in MENU if x.action in ('files','settings','home','pages','hardware')))
FIRMWARE = replace(SLOOP, id='zp12', name='zp12', source_env='ZP12_SOURCE',
    default_source='fw-ext/zp12', adapter='zp12', studio=UI)
