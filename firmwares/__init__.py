from .sloop import FIRMWARE as SLOOP
from .move import FIRMWARE as MOVE
from .felucca import FIRMWARE as FELUCCA
from .sloopdx import FIRMWARE as SLOOPDX
from .ghoulbox import FIRMWARE as GHOULBOX
from .zp12 import FIRMWARE as ZP12
from .schema import validate

FIRMWARES = (SLOOP, MOVE, FELUCCA, SLOOPDX, GHOULBOX, ZP12)
for firmware in FIRMWARES:
    validate(firmware)
assert len({f.id for f in FIRMWARES}) == len(FIRMWARES)
