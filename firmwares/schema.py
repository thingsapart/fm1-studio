"""Platform-independent source definitions. Event numbers are native/firmware.h."""
from dataclasses import dataclass
from .studio import Studio, SLOOP_STUDIO

BUTTON, ENCODER, KEY = range(3)
NATIVE_SCREEN = 8

@dataclass(frozen=True)
class Control:
    label: str
    kind: int
    target: int

@dataclass(frozen=True)
class Screen:
    title: str
    controls: tuple[Control, ...]
    # Native navigation is explicit. Empty means only rearrange host controls.
    enter: tuple[tuple[int, int, int], ...] = ()

@dataclass(frozen=True)
class Firmware:
    id: str
    name: str
    source_env: str
    default_source: str
    adapter: str
    screens: tuple[Screen, ...]
    studio: Studio = SLOOP_STUDIO

def button(label, target):
    return Control(label, BUTTON, target)

def encoder(label, target):
    return Control(label, ENCODER, target)

def tap(target):
    return ((BUTTON, target, 1), (BUTTON, target, 0))

ENCODERS = tuple(encoder(label, i) for i, label in enumerate(
    ('SELECT', 'ALGO', 'PRESETS', 'K1', 'K2', 'K3', 'K4')))
KEYS = tuple(Control(str(i + 1), KEY, i) for i in range(27))

def validate(firmware):
    import re
    if not re.fullmatch(r'[a-z][a-z0-9_]*', firmware.id):
        raise ValueError('Firmware id must be a safe C symbol and storage name')
    if not firmware.screens:
        raise ValueError(f'{firmware.id}: no screens')
    if not 1 <= len(firmware.studio.tracks) <= 8 or not 0 <= firmware.studio.sample_slots <= 4:
        raise ValueError(f'{firmware.id}: invalid studio dimensions')
    if len(firmware.studio.events)!=20 or len(set(firmware.studio.events))!=20 or any(e<64 or e>255 for e in firmware.studio.events):
        raise ValueError(f'{firmware.id}: invalid semantic event mapping')
    valid_components={'PANEL','STEPS','MIX','PERFORM','SONG','MORE'}
    if any(component not in valid_components for _,component in firmware.studio.tabs):
        raise ValueError(f'{firmware.id}: unknown studio component')
    valid_routes={'new','projects','clear','pattern','sounds','samples','files','settings','keyScale','home','pages','preset','fm6','drumSynth','catalogue','hardware','dx7'}
    if any(item.action not in valid_routes for item in firmware.studio.menu):
        raise ValueError(f'{firmware.id}: unknown menu action')
    ui=firmware.studio
    if ui.track_axis not in ("horizontal","vertical") or len(ui.track_bounds)!=4:
        raise ValueError(f'{firmware.id}: invalid track hit mapping')
    left,top,right,bottom=ui.track_bounds
    if not 0<=left<right<=240 or not 0<=top<bottom<=240:
        raise ValueError(f'{firmware.id}: track hit bounds must fit the native display')
    for bounds in ui.track_screen_bounds:
        if len(bounds)!=5 or bounds[0]<0 or not 0<=bounds[1]<bounds[3]<=240 or not 0<=bounds[2]<bounds[4]<=240:
            raise ValueError(f'{firmware.id}: invalid per-screen track bounds')
    if len({b[0] for b in ui.track_screen_bounds})!=len(ui.track_screen_bounds):
        raise ValueError(f'{firmware.id}: duplicate per-screen track bounds')
    layout={"transport","tracks","status","sounds","workspace","tabs","keyTools","keyboard"}
    if len(set(ui.layout))!=len(ui.layout) or set(ui.layout)!=layout:
        raise ValueError(f'{firmware.id}: studio layout must contain each shared component once')
    if len(ui.drum_notes)!=ui.drum_lanes or any(n<0 or n>127 for n in ui.drum_notes) or not 1<=ui.project_slots<=4:
        raise ValueError(f'{firmware.id}: invalid drum/project mapping')
    if len(set(title for title,_ in ui.tabs))!=len(ui.tabs) or not ui.tabs:
        raise ValueError(f'{firmware.id}: invalid studio tabs')
    if len(set(name for name,_ in ui.sheets))!=len(ui.sheets):
        raise ValueError(f'{firmware.id}: duplicate studio sheet')
    for page in ui.pages:
        if not page.native or len(set(page.parameters))!=len(page.parameters):
            raise ValueError(f'{firmware.id}: invalid parameter page')
        for event in page.enter:
            if len(event)!=3 or not 0<=event[0]<=8 or event[1]<0:
                raise ValueError(f'{firmware.id}: invalid page navigation')
    titles = set()
    for screen in firmware.screens:
        if not screen.title or screen.title in titles:
            raise ValueError(f'{firmware.id}: empty/duplicate screen title')
        titles.add(screen.title)
        for c in screen.controls:
            if not c.label or c.kind not in (BUTTON, ENCODER, KEY) or not 0 <= c.target < (14, 7, 27)[c.kind]:
                raise ValueError(f'{firmware.id}: invalid control {c}')
        for kind, target, value in screen.enter:
            if not ((kind == BUTTON and 0 <= target < 14 and value in (0,1)) or
                    (kind == NATIVE_SCREEN and 0 <= target < 256 and value == 0)):
                raise ValueError(f'{firmware.id}: invalid navigation event')
        held = set()
        for kind, target, value in screen.enter:
            if kind != BUTTON: continue
            if value:
                held.add(target)
            else:
                held.discard(target)
        if held:
            raise ValueError(f'{firmware.id}: navigation leaves a button held')
