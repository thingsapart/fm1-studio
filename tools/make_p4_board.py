#!/usr/bin/env python3
"""Generate a standalone Klickwerk-board host without modifying the current port."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import hashlib
import json
from prepare import PROJECT

def main():
    reference=PROJECT/'esp32p4/template';out=PROJECT/'build/p4-board';out.mkdir(parents=True,exist_ok=True)
    for name in ('platformio.ini','sdkconfig.defaults','partitions.csv','dependencies.lock','sources.lock.json','CMakeLists.txt'):
        shutil.copy2(reference/name,out/name)
    tools=out/'tools';tools.mkdir(exist_ok=True)
    # Verify only the board dependency. The original port's SLOOP lock covers
    # an older engine overlay, which this host does not compile or use.
    kw=Path(os.environ.get('KLICKWERK_SOURCE',PROJECT.parent.parent/'klickwerk2')).resolve()
    lock=json.loads((reference/'sources.lock.json').read_text())
    paths=['firmware/p4/src','firmware/p4/components/lvgl','firmware/p4/tools/patch_uac.py',
           'firmware/p4/tools/patch_lvgl_yield.py','firmware/p4/tools/patch_usb_descriptors.py',
           'firmware/display/contrib/lvgl_ui/lvgl','firmware/shared/ring_buffer.h','firmware/p4/dependencies.lock']
    actual={}
    for name in paths:
        path=kw/name
        for p in ([path] if path.is_file() else sorted(path.rglob('*'))):
            if p.is_file() and '__pycache__' not in p.parts:actual[str(p.relative_to(kw))]=hashlib.sha256(p.read_bytes()).hexdigest()
    if actual!=lock['klickwerk']:raise SystemExit('Klickwerk board inputs changed; review the board dependency lock before building')
    (out/'build').mkdir(exist_ok=True)
    (out/'build/paths.cmake').write_text('set(KW_ROOT "'+str(kw)+'")\n')
    (out/'board-sources.lock.json').write_text(json.dumps({'klickwerk':actual},indent=2)+'\n')
    # An optional existing component cache allows offline builds. Ordinary PIO
    # builds resolve the pinned idf_component.yml/dependencies.lock themselves.
    cache=Path(os.environ.get('FM1_COMPONENT_CACHE',PROJECT.parent/'sloop-fm1/ports/esp32p4/managed_components'))
    if not (out/'managed_components').exists() and cache.is_dir():shutil.copytree(cache,out/'managed_components')
    cmake=(out/'CMakeLists.txt').read_text().replace('project(sloop_p4)','project(fm1_wrapper_p4)')
    cmake=cmake.replace('project(fm1_wrapper_p4)','set(PROJECT_VER "0.1.0")\nproject(fm1_wrapper_p4)')
    cmake=cmake.replace('"${CMAKE_CURRENT_LIST_DIR}/src"',
                        '"${CMAKE_CURRENT_LIST_DIR}/src" "'+str(PROJECT/'esp32p4/components/fm1_wrapper')+'"')
    (out/'CMakeLists.txt').write_text(cmake)
    ini=(out/'platformio.ini').read_text().replace('sloop_p4','fm1_wrapper_p4').replace('extra_scripts = pre:tools/pio_prepare.py','')
    (out/'platformio.ini').write_text(ini)
    src=out/'src';src.mkdir(exist_ok=True)
    for name in ('platform.cpp','storage_files.h','storage_format.h','idf_component.yml'):
        shutil.copy2(reference/'src'/name,src/name)
    shutil.copy2(PROJECT/'esp32p4/board/main.cpp',src/'main.cpp')
    component=(reference/'src/CMakeLists.txt').read_text()
    component=component.replace('SRCS main.cpp keyboard.cpp surface.cpp storage.cpp platform.cpp\n    ../engine/core.c',
                                'SRCS main.cpp platform.cpp')
    component=component.replace('INCLUDE_DIRS . ../engine ../build/gen ../build/gen/sloop_firmware',
                                'INCLUDE_DIRS .')
    component=component.replace('REQUIRES lvgl','REQUIRES fm1_wrapper lvgl')
    (src/'CMakeLists.txt').write_text(component)
    print(out)

if __name__=='__main__':main()
