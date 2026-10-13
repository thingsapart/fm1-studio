"""Serialize generators and consumers of the shared staged firmware trees."""
import fcntl
from pathlib import Path

_lock=None
def acquire():
    global _lock
    if _lock is not None:return
    out=Path(__file__).resolve().parents[1]/'build';out.mkdir(exist_ok=True)
    _lock=(out/'.build.lock').open('a')
    fcntl.flock(_lock,fcntl.LOCK_EX)
