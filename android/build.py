#!/usr/bin/env python3
"""Build one APK containing explicitly selected, verified firmware plugins."""
from pathlib import Path
import argparse
import os
import secrets
import shutil
import subprocess
import sys
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT / 'tools'))
from prepare import prepare
from definitions import java, c
from firmwares import FIRMWARES
from dependencies import android_paths, java_tool, check_dependencies, setup_commands

def run(args):
    subprocess.run([str(a) for a in args], check=True)

def main():
    from build_lock import acquire
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--firmwares', default=','.join(f.id for f in FIRMWARES), help='Comma-separated firmware ids')
    parser.add_argument('--abi', choices=('arm64-v8a','x86_64'), default='arm64-v8a')
    parser.add_argument('--release', action='store_true', help='Build a non-debuggable APK with the persistent local release signing key')
    parser.add_argument('--setup', action='store_true', help='Print installation commands for this host; do not build')
    parser.add_argument('--check-deps', action='store_true', help='Check prerequisites without building')
    args=parser.parse_args()
    if args.setup:
        print(setup_commands('android'));return
    names=args.firmwares.split(',')
    if not names or len(names)!=len(set(names)) or any(n not in {f.id for f in FIRMWARES} for n in names):
        parser.error('Select distinct ids from: '+', '.join(f.id for f in FIRMWARES))
    selected=[next(f for f in FIRMWARES if f.id==n) for n in names]
    check_dependencies('android', selected, abi=args.abi)
    if args.check_deps:return
    acquire()
    out=PROJECT/'build/android';out.mkdir(parents=True,exist_ok=True)
    sdk,ndk,toolchain=android_paths()
    tools=sdk/'build-tools/35.0.0';jar=sdk/'platforms/android-35/android.jar'
    jtool=java_tool
    # Verify every adapter before compiling/package creation. No silent omissions.
    staged=[prepare(f) for f in selected]
    libs=out/'lib'/args.abi;libs.mkdir(parents=True,exist_ok=True)
    target={'arm64-v8a':'aarch64-linux-android26','x86_64':'x86_64-linux-android26'}[args.abi]
    cc=toolchain/(target+'-clang');cxx=toolchain/(target+'-clang++')
    link=['-Wl,-z,max-page-size=16384','-Wl,-z,common-page-size=16384','-Wl,--no-undefined']
    for f,stage in zip(selected,staged):
        run([cc,'-std=c11','-O2','-fPIC','-fvisibility=hidden','-fwrapv','-ffp-contract=off','-Wno-int-to-pointer-cast',
             '-I'+str(PROJECT/'native'),'-I'+str(stage/'build/gen'),'-I'+str(stage/'build/firmware/src'),
             '-shared',*link,stage/'plugin.c','-lm','-o',libs/f'libfm1_{f.id}.so'])
    run([cxx,'-std=c++17','-O2','-fPIC','-shared','-static-libstdc++',*link,
         '-I'+str(PROJECT/'native'),PROJECT/'android/native/audio.cpp',PROJECT/'native/session.cpp',
         '-laaudio','-ldl','-o',libs/'libfm1host.so'])
    generated=out/'generated'
    if generated.exists():shutil.rmtree(generated)
    java(selected,generated/'org/fm1/studio/Definitions.java')
    c(selected,out/'screens.h')
    classes=out/'classes';dex=out/'dex'
    for folder in (classes,dex):
        if folder.exists():shutil.rmtree(folder)
        folder.mkdir()
    run([jtool('javac'),'--release','8','-classpath',jar,'-d',classes,
         *sorted((PROJECT/'android/java').rglob('*.java')),*sorted(generated.rglob('*.java'))])
    run([tools/'d8','--lib',jar,'--min-api','26','--output',dex,*sorted(classes.rglob('*.class'))])
    assets=out/'assets'
    if assets.exists():shutil.rmtree(assets)
    assets.mkdir()
    run([sys.executable,PROJECT/"android/font.py",assets/"sloop-screen.ttf"])
    shutil.copy2(PROJECT/"android/assets/Terminus-LICENSE.txt",assets/"Terminus-LICENSE.txt")
    for name in ('LICENSE','LICENSING.md'):
        shutil.copy2(PROJECT/name,assets/name)
    shutil.copytree(PROJECT/'LICENSES',assets/'LICENSES')
    shutil.copy2(toolchain.parent/'NOTICE',assets/'Android-NDK-NOTICE.txt')
    for f,stage in zip(selected,staged):
        notices=assets/f.id;notices.mkdir()
        shutil.copy2(stage/'inputs.json',notices/'inputs.json')
        # Preserve complete upstream notices alongside engine source attributions.
        root=stage/'source'
        from prepare import source
        for name in ('LICENSE','LICENSING.md','README.md'):
            origin=source(f)/name
            if origin.exists():shutil.copy2(origin,notices/name)
        if f.id=='zp12':shutil.copy2(source(f)/'sim/sim.c',notices/'simulator-attribution.c')
        if (source(f)/'LICENSES').exists():shutil.copytree(source(f)/'LICENSES',notices/'LICENSES')
        for name in ('fm6_core.c','dx7_core.c','phys_dsp.c','phys_symp.c','eng_phys.c','eng_phase.c','eng_formant.c'):
            origin=root/'firmware/src'/name
            if origin.exists():shutil.copy2(origin,notices/name)
        for origin in (root/'assets').rglob('*'):
            if origin.is_file() and ('LICENSE' in origin.name.upper() or 'ATTRIBUTION' in origin.name.upper() or origin.name.upper() in ('OFL.TXT','COPYING','NOTICE')):
                dest=notices/'assets'/origin.relative_to(root/'assets');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(origin,dest)
    unsigned=out/'unsigned.apk'
    resources=out/'resources.zip'
    run([tools/'aapt2','compile','--dir',PROJECT/'android/res','-o',resources])
    manifest=out/'AndroidManifest.xml'
    manifest.write_text((PROJECT/'android/AndroidManifest.xml').read_text().replace('android:debuggable="false"', 'android:debuggable="%s"' % ('false' if args.release else 'true')))
    run([tools/'aapt2','link','-o',unsigned,'--manifest',manifest,'-I',jar,'-A',assets,resources])
    with zipfile.ZipFile(unsigned,'a',compression=zipfile.ZIP_DEFLATED) as apk:
        apk.write(dex/'classes.dex','classes.dex')
        for name in ['libfm1host.so']+[f'libfm1_{f.id}.so' for f in selected]:
            apk.write(libs/name,f'lib/{args.abi}/{name}')
    aligned=out/'aligned.apk';run([tools/'zipalign','-f','-p','4',unsigned,aligned])
    if args.release:
        signing=PROJECT/'android/signing';signing.mkdir(mode=0o700,exist_ok=True)
        key=signing/'release.keystore';password=signing/'release.password'
        if key.exists() and not password.exists():raise SystemExit('Missing release signing password: '+str(password))
        if not password.exists():
            with open(password,'x',opener=lambda path,flags:os.open(path,flags,0o600)) as stream:stream.write(secrets.token_urlsafe(48))
        storepass=['-storepass:file',password];keypass=['-keypass:file',password]
        signpass=['--ks-pass','file:'+str(password)]
        alias='fm1studio';identity='CN=FM1 Studio'
    else:
        key=out/'debug.keystore';storepass=['-storepass','android'];keypass=['-keypass','android']
        signpass=['--ks-pass','pass:android','--key-pass','pass:android'];alias='androiddebugkey';identity='CN=FM1 Debug'
    if not key.exists():
        run([jtool('keytool'),'-genkeypair','-keystore',key,*storepass,*keypass,
             '-alias',alias,'-keyalg','RSA','-keysize','2048','-validity','10000','-dname',identity])
        key.chmod(0o600)
    apk=out/'fm1-studio.apk'
    run([tools/'apksigner','sign','--ks',key,*signpass,'--out',apk,aligned])
    run([tools/'apksigner','verify',apk]);print(apk)

if __name__=='__main__':main()
