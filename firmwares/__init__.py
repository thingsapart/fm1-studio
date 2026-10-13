from .sloop import FIRMWARE as SLOOP
from .move import FIRMWARE as MOVE
from .felucca import FIRMWARE as FELUCCA
from .sloopdx import FIRMWARE as SLOOPDX
from .schema import validate

FIRMWARES = (SLOOP, MOVE, FELUCCA, SLOOPDX)
for firmware in FIRMWARES:
    validate(firmware)
assert len({f.id for f in FIRMWARES}) == len(FIRMWARES)
