#!/usr/bin/env python3
"""Build the on-device page regression runner after android/build.py.

Install build/android/controller-tests.apk, then run:
  adb shell am instrument -w org.fm1.studio.tests/.ControllerPageInstrumentation
The runner navigates pages in the current project; it does not edit sound parameters.
"""
from pathlib import Path
import argparse
import shutil
import subprocess
import sys
import zipfile
PROJECT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(PROJECT/'tools'))
from dependencies import android_paths,java_tool

def run(args):subprocess.run([str(a) for a in args],check=True)
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--release',action='store_true',help='Sign with the app release certificate')
args=parser.parse_args()
sdk,_,_=android_paths();tools=sdk/'build-tools/35.0.0';jar=sdk/'platforms/android-35/android.jar'
out=PROJECT/'build/android/controller-tests';out.mkdir(parents=True,exist_ok=True)
classes=out/'classes'
if classes.exists():shutil.rmtree(classes)
classes.mkdir()
dex=out/'dex';dex.mkdir(exist_ok=True)
manifest=out/'AndroidManifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.fm1.studio.tests">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/>
<application android:label="FM1 controller tests"/>
<instrumentation android:name="org.fm1.studio.tests.ControllerPageInstrumentation" android:targetPackage="org.fm1.studio"/>
<instrumentation android:name="org.fm1.studio.tests.FirmwareMidiInstrumentation" android:targetPackage="org.fm1.studio"/>
<instrumentation android:name="org.fm1.studio.tests.Fm1PanelInstrumentation" android:targetPackage="org.fm1.studio"/>
</manifest>''')
run([java_tool('javac'),'--release','8','-classpath',jar,'-d',classes,*sorted((PROJECT/'tests/android').glob('*.java'))])
run([tools/'d8','--lib',jar,'--min-api','26','--output',dex,*classes.rglob('*.class')])
unsigned=out/'unsigned.apk'
run([tools/'aapt2','link','-o',unsigned,'--manifest',manifest,'-I',jar])
with zipfile.ZipFile(unsigned,'a',compression=zipfile.ZIP_DEFLATED) as apk:apk.write(dex/'classes.dex','classes.dex')
aligned=out/'aligned.apk';run([tools/'zipalign','-f','4',unsigned,aligned])
apk=out.parent/'controller-tests.apk'
key=PROJECT/'android/signing/release.keystore' if args.release else out.parent/'debug.keystore'
password='file:'+str(PROJECT/'android/signing/release.password') if args.release else 'pass:android'
run([tools/'apksigner','sign','--ks',key,'--ks-pass',password,'--out',apk,aligned])
print(apk)
