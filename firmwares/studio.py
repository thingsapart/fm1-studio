"""Shared complete Android instrument surface; profiles can replace any section."""
from dataclasses import dataclass, replace
from .studio_labels import LABELS

@dataclass(frozen=True)
class MenuEntry:
    label: str
    action: str

@dataclass(frozen=True)
class ParameterPage:
    native: str
    title: str = ""
    # Empty retains all native fields; labels select and reorder individual controls.
    parameters: tuple[str,...] = ()
    enter: tuple[tuple[int,int,int],...] = ()

@dataclass(frozen=True)
class Studio:
    tracks: tuple[str, ...]
    # Native 240x240 track selection rectangle, split into rows or columns.
    track_axis: str = "horizontal"
    track_bounds: tuple[int,int,int,int] = (0,40,240,184)
    track_buttons_even: bool = False
    # Optional (native screen id, left, top, right, bottom) overrides.
    track_screen_bounds: tuple[tuple[int,int,int,int,int],...] = ()
    sample_slots: int = 4
    project_slots: int = 4
    midi_groups: tuple[str,...] = ("SYSTEM","MIDI","MIDI 2")
    drum_lanes: int = 16
    drum_keys_physical: bool = True
    midi_route_parameter: int = 14
    nudge_min: int = -32
    nudge_max: int = 31
    drum_notes: tuple[int,...] = (36,35,38,39,42,46,44,37,40,43,48,49,51,70,63,56)
    labels: tuple[tuple[str,str],...] = LABELS
    # Semantic operation remapping: (event kind, logical operation, adapter operation).
    bindings: tuple[tuple[int,int,int],...] = tuple(
        (kind,operation,operation) for kind,operations in (
            (21,tuple(range(9))+tuple(range(16,20))+tuple(range(32,35))+tuple(range(40,57))),
            (22,tuple(range(11))), (24,tuple(range(34))+tuple(range(40,48))+tuple(range(50,66))+(70,71)),
            (25,tuple(range(15))), (26,tuple(range(7)))) for operation in operations)
    fixed_drum: int = 3
    tabs: tuple[tuple[str,str], ...] = (("PANEL","PANEL"),("STEPS","STEPS"),("MIX","MIX"),("PERFORM","PERFORM"),("SONG","SONG"))
    layout: tuple[str,...] = ("transport","tracks","status","sounds","workspace","tabs","keyTools","keyboard")
    menu: tuple[MenuEntry,...] = ()
    pages: tuple[ParameterPage,...] = (ParameterPage("*"),)
    # Common sound overview: native groups, in display order; absent groups are omitted.
    sound_pages: tuple[str,...] = ("ENV","EDIT 1","FILTER","EDIT 2","VOICE","FX","SENDS","SEL","SEL 2","SCL","CHORD","ARP","DLY","DELAY","REV/CHO","REVERB","CHORUS","DRUMS","LANES","KIT")
    events: tuple[int,...] = tuple(range(72,92))
    # Same UI widgets; native capabilities determine the exposed editing fields.
    features: tuple[str,...] = ("fm6","fm6_bank","drum_synth","locks","fill","free_take","arrangement","midi_learn","repeat","touch")
    sheets: tuple[tuple[str,tuple[str,...]],...] = (
        ("pattern",("UNDO","REDO","CLEAR TRACK","SHIFT LEFT","SHIFT RIGHT","DUPLICATE ×2","HALVE LENGTH","TRANSPOSE","INITIALIZE SOUND","IMPORT MIDI FILE","EXPORT MIDI FILE")),
        ("projects",("LOAD","SAVE CURRENT LOOP","ERASE SLOT","EXPORT PROJECT","IMPORT PROJECT","NAME SECTION")),
        ("sounds",("LOAD SOUND","STORE CURRENT SOUND","ERASE SOUND","RENAME","EXPORT SOUND","IMPORT SOUND")),
        ("samples",("IMPORT FSMP","EXPORT FSMP","BUILD INSTRUMENT FROM WAVS","CHOP A WAV RECORDING","BUILD DRUM KIT FROM WAVS","ERASE SLOT")),
        ("settings",("DISPLAY LAYOUT","DISPLAY SIZE","CAMERA CLEARANCE","DISPLAY COLOR","DISPLAY ZOOM","OUTPUT LOWCUT","MIDI LEARN","CLEAR MIDI ASSIGNMENTS","MIDI CHANNELS / SYNC","FIRMWARE")),
        ("perform",("FX","REPEAT","TOUCH","MASTER")),
        ("files",("Connect USB / available MIDI","Connect Bluetooth LE MIDI","MIDI routing and pad notes","Disconnect MIDI","Master volume","Export Android backup","Restore Android backup","Import user sample slot","Audio diagnostics","MIDI knobs → FM-1","Connect MIDI output","Disconnect MIDI output")),
        ("fm6_operator",("RATE 1","RATE 2","RATE 3","RATE 4","LEVEL 1","LEVEL 2","LEVEL 3","LEVEL 4","BREAKPOINT","LEFT DEPTH","RIGHT DEPTH","LEFT CURVE","RIGHT CURVE","RATE SCALE","AMP SENS","VELOCITY SENS","OUTPUT","FIXED MODE","COARSE","FINE","DETUNE")),
        ("fm6_voice",("PITCH RATE 1","PITCH RATE 2","PITCH RATE 3","PITCH RATE 4","PITCH LEVEL 1","PITCH LEVEL 2","PITCH LEVEL 3","PITCH LEVEL 4","ALGORITHM","FEEDBACK","OSC SYNC","LFO SPEED","LFO DELAY","LFO PITCH","LFO AMP","LFO SYNC","LFO WAVE","PITCH SENS","TRANSPOSE")),
        ("drum_fields",("WAVE","NOISE / CLAP","PITCH","FINE","BEND","BEND TIME","HOLD","DECAY","TONE LEVEL","SECOND RATIO","SECOND LEVEL","CLICK","NOISE LEVEL","NOISE HOLD","NOISE DECAY","FILTER / RES","CUTOFF","FILTER ENV","NOISE HPF","CHIP CLOCK","DRIVE","OUT LEVEL")),
        ("step",("NOTE","LENGTH","VELOCITY","FLAGS","LEVEL","RATCHET","NUDGE","FILL CONDITION","LOCKS","REMOVE NOTE / HIT","CLEAR WHOLE STEP")),
        ("recording",("RECORD MODE","RECORD LENGTH","RECORD START","CANCEL RECORDING","FINISH TAKE","DISCARD TAKE")),
        ("fm6",("OPERATOR 6","OPERATOR 5","OPERATOR 4","OPERATOR 3","OPERATOR 2","OPERATOR 1","VOICE / PITCH ENVELOPE","PATCH NAME","BANK SLOTS","IMPORT PATCH / SYSEX","EXPORT PATCH","IMPORT CARTRIDGE (REPLACE BANK)","EXPORT CARTRIDGE")),
        ("fm6Bank",("STORE TRACK PATCH","ERASE")),
        ("synthKits",("COPY FACTORY KIT","NAME","BIT CRUSH","SAMPLE / HOLD","USE ON DRUM TRACK","STORE ALL SYN KITS","IMPORT KIT","EXPORT KIT")),
        ("synthSound",("PLAY","WAVE","NOISE / CLAP","PITCH","FINE","BEND","BEND TIME","HOLD","DECAY","TONE LEVEL","SECOND RATIO","SECOND LEVEL","CLICK","NOISE LEVEL","NOISE HOLD","NOISE DECAY","FILTER / RES","CUTOFF","FILTER ENV","NOISE HPF","CHIP CLOCK","DRIVE","OUT LEVEL")),
        ("learn",("ENABLE LEARN","DISABLE LEARN")),
        ("songEntry",("SECTION","BARS","MOVE UP","MOVE DOWN","REMOVE")),
        ("chain",("APPEND A","APPEND B","APPEND C","APPEND D","START CHAIN","CLEAR CHAIN")),
        ("clips",("CLIP","TRACK TYPE","GROOVE TEMPLATE","COMMIT GROOVE")),
        ("songChain",("PLAY SONG CHAIN","STOP SONG CHAIN","SECTION","ADD SECTION","CLEAR CHAIN","SAVE CHAIN IN PROJECT")),
        ("sampleBuilder",("PLAY RECORDING","TAP CUT DURING PLAY","EQUAL PARTS","FIND HITS","FIT TO SLOT","SEND TO USR","USE SLOT ON SELECTED TRACK")),
        ("sampleZone",("PLAY","LEAVE OUT","KEEP","ROOT NOTE","ASSIGN DRUM LANE","TRIM LENGTH","GAIN / %")),
        ("song",("LOOP / SONG MODE","PLAY SECTION","STORE SECTION","RECORD ARRANGEMENT","CHAIN","AT END","ADD SECTION","SAVE ARRANGEMENT")),
    )

MENU = tuple(MenuEntry(label,action) for label,action in (
    ("NEW PROJECT","new"),("PROJECTS / SECTIONS","projects"),("CLEAR SELECTED TRACK","clear"),
    ("PATTERN ACTIONS / UNDO","pattern"),("USER SOUNDS","sounds"),("SAMPLE SLOTS / FILES","samples"),
    ("MIDI / AUDIO / FILES","files"),("INSTRUMENT SETTINGS","settings"),("KEY / SCALE FOR ALL TRACKS","keyScale"),
    ("HOME / TRACK CONTROLS","home"),("PAGES","pages"),("SOUND / PRESET","preset"),
    ("FM6 OPERATORS / BANKS","fm6"),("DRUM SYNTH","drumSynth"),("ALL FACTORY SOUNDS","catalogue"),("HARDWARE PANEL","hardware")))
SLOOP_PAGES=tuple(ParameterPage(title) for title in
    ("GLOBAL","MASTER","ENV","ENV DEST","LFO","LFO 2","LFO DEST","FX","FILTER","SLICER","INSERT","INSERT 2","DLY","REV/CHO","SEL","SEL 2","EDIT 1","EDIT 2","VOICE","VOICE 2","SYSTEM","DRUMS","MIDI","MIDI 2","TEXTURE","TEXTURE 2","ARP","ARP 2","PATTERN","KIT","TRIM","*"))
MOVE_PAGES=tuple(ParameterPage(title,enter=((8,screen,256+page),)) for title,screen,page in (
    ("GLOBAL",5,0),("MASTER",7,0),("ENV",6,1),("ENV DEST",6,1),("LFO",6,2),("LFO DEST",6,2),
    ("FX",6,3),("FILTER",6,3),("SLICER",6,3),("DLY",6,3),("REV/CHO",6,3),("SEL",1,0),("EDIT 1",6,0),("EDIT 2",6,0),
    ("VOICE",6,0),("VOICE 2",6,0),("SYSTEM",9,0),("DRUMS",6,0),("ARP",3,0),("ARP 2",3,1),("PATTERN",2,0),("KIT",6,0),("TRIM",6,3)))+(ParameterPage("*"),)
FELUCCA_PAGES=tuple(ParameterPage(title) for title in
    ("ENV","ENV DEST","LFO","LFO 2","LFO DEST","MOD","FX","SLICER","INSERT","INSERT 2","DLY","REVERB","CHORUS","SCL","CHORD","EDIT 1","EDIT 2","FILTER","VOICE","VOICE 2","VOICE 3","ARP","ARP 2","CLOCK","LANES","LANES 2","*"))
SLOOP_STUDIO=Studio(("TRACK 1","TRACK 2","TRACK 3","DRUMS"),menu=MENU,pages=SLOOP_PAGES)
MOVE_STUDIO=replace(SLOOP_STUDIO,track_axis="vertical",track_bounds=(8,44,232,176),track_screen_bounds=((4,26,44,234,176),),track_buttons_even=True,tracks=tuple("TRACK "+str(i+1) for i in range(8)),pages=MOVE_PAGES,sample_slots=3,project_slots=2,fixed_drum=-1,midi_groups=("SYSTEM",),features=("fm6","fm6_bank","drum_synth","locks","fill","free_take","clips","groove","repeat"),tabs=(("PANEL","PANEL"),("STEPS","STEPS"),("MIX","MIX"),("PERFORM","PERFORM"),("CLIPS","SONG")))
FELUCCA_STUDIO=replace(SLOOP_STUDIO,tracks=tuple("TRACK "+str(i+1) for i in range(4)),pages=FELUCCA_PAGES,fixed_drum=-1,drum_lanes=8,drum_keys_physical=False,sample_slots=3,nudge_min=-8,nudge_max=7,drum_notes=(36,38,39,42,46,45,37,56),midi_groups=("CLOCK",),
    features=("fm6","locks","midi_learn","song_chain","probability","motion","touch"),
    menu=tuple(replace(x,label="FM6 OPERATORS / PATCHES") if x.action=="fm6" else x for x in MENU if x.action!="drumSynth"))
