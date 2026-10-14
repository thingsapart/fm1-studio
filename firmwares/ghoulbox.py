"""Jason Persinger's Ghoulbox, preserving its dungeon UI and synthesis."""
from dataclasses import replace
from .felucca import FIRMWARE as FELUCCA

FIRMWARE = replace(FELUCCA, id='ghoulbox', name='Ghoulbox',
                   source_env='GHOULBOX_SOURCE', default_source='fw-ext/ghoulbox',
                   studio=replace(FELUCCA.studio, features=tuple(x for x in FELUCCA.studio.features if x!='midi_learn')))
