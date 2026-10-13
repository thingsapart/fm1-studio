"""Read-only build preflight and platform-specific installation guidance."""
from pathlib import Path
import importlib.util
import os
import platform
import re
import shutil
import subprocess
import sys

PROJECT = Path(__file__).resolve().parents[1]


def linux_release(path=Path('/etc/os-release')):
    try:
        return dict(line.split('=', 1) for line in path.read_text().splitlines()
                    if '=' in line and not line.startswith('#'))
    except OSError:
        return {}


def host_profile(system=None, release=None):
    system = system or platform.system()
    if system == 'Darwin':
        return 'macos'
    if system != 'Linux':
        return 'unsupported'
    release = linux_release() if release is None else release
    ids = (release.get('ID', '') + ' ' + release.get('ID_LIKE', '')).replace('"', '').replace("'", '').split()
    for family, names in [('debian', {'debian', 'ubuntu', 'linuxmint', 'pop'}),
                          ('fedora', {'fedora', 'rhel', 'centos', 'rocky', 'almalinux'}),
                          ('arch', {'arch', 'manjaro', 'endeavouros'}),
                          ('suse', {'suse', 'opensuse', 'opensuse-tumbleweed', 'opensuse-leap'})]:
        if names.intersection(ids):
            return family
    return 'linux'


def android_paths():
    configured = os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
    candidates = [PROJECT.parent/'sloop-fm1/ports/android/build/sdk',
                  Path.home()/'Library/Android/sdk', Path.home()/'Android/Sdk',
                  PROJECT/'build/android/sdk']
    sdk = Path(configured).expanduser() if configured else next(
        (p for p in candidates if (p/'platforms').is_dir()), PROJECT/'build/android/sdk')
    sdk = sdk.resolve()
    ndk = Path(os.environ.get('ANDROID_NDK_ROOT', sdk/'ndk/27.2.12479018')).expanduser().resolve()
    host = 'darwin-x86_64' if platform.system() == 'Darwin' else 'linux-x86_64'
    return sdk, ndk, ndk/'toolchains/llvm/prebuilt'/host/'bin'


def java_tool(name):
    return str(Path(os.environ['JAVA_HOME']).expanduser()/'bin'/name) if os.environ.get('JAVA_HOME') else name


def executable(command):
    return shutil.which(str(command)) is not None


def setup_commands(target, profile=None):
    profile = profile or host_profile()
    android = target == 'android'
    lines = [f'# FM1 Studio {target} setup ({profile}); run from the repository root.',
             '# These commands are printed only; nothing is installed automatically.']
    packages = {
        'debian': 'sudo apt-get update\nsudo apt-get install git python3 python3-venv python3-pip build-essential binutils cmake ninja-build curl unzip' + (' openjdk-21-jdk' if android else ''),
        'fedora': 'sudo dnf install git python3 python3-pip gcc gcc-c++ binutils cmake ninja-build curl unzip' + (' java-21-openjdk-devel' if android else ''),
        'arch': 'sudo pacman -S --needed git python python-pip base-devel cmake ninja curl unzip' + (' jdk21-openjdk' if android else ''),
        'suse': 'sudo zypper install git python3 python3-pip python3-virtualenv gcc gcc-c++ binutils cmake ninja curl unzip' + (' java-21-openjdk-devel' if android else ''),
        'macos': '# Install Homebrew first if needed: https://brew.sh\nbrew install git python cmake ninja' + (' openjdk@21\nexport JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"' if android else '') + '\n# Install Apple command-line tools if cc/nm are missing:\nxcode-select --install',
    }
    lines.append(packages.get(profile, '# Install Git, Python 3.10+ with venv/pip, a C/C++ compiler, binutils, CMake, Ninja, curl and unzip using your package manager.' + ('\n# Also install JDK 17 or 21.' if android else '')))
    lines += ['# Use Python 3.10+; older distro releases may require a newer Python package.',
              'python3 -m venv .venv', '. .venv/bin/activate',
              'python3 -m pip install --upgrade pip',
              'python3 -m pip install Pillow fonttools' + ('' if android else ' platformio'),
              'git submodule update --init --recursive']
    if android:
        lines += ['export ANDROID_SDK_ROOT="$PWD/build/android/sdk"',
                  '# JAVA_HOME must point to your JDK if java/javac are not on PATH.',
                  '# Setup uses JDK 21 for current Command-line Tools; APK builds also support JDK 17.']
        if profile == 'macos':
            lines += ['brew install --cask android-commandlinetools', 'FM1_SDKMANAGER="$(command -v sdkmanager)"']
        else:
            lines += ['export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"',
                      'FM1_SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"',
                      'if [ ! -x "$FM1_SDKMANAGER" ]; then',
                      '    mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools" "$PWD/build/android/cmdline-unpack"',
                      '    curl -fL https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip -o "$PWD/build/android/commandlinetools.zip"',
                      '    unzip -o "$PWD/build/android/commandlinetools.zip" -d "$PWD/build/android/cmdline-unpack"',
                      '    mv "$PWD/build/android/cmdline-unpack/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"',
                      'fi']
        lines += ['"$FM1_SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --licenses',
                  '"$FM1_SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --install "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;27.2.12479018"',
                  'export ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/27.2.12479018"',
                  '# sdkmanager is retained for compatibility with existing Command-line Tools.',
                  '# https://developer.android.com/tools/sdkmanager',
                  'python3 android/build.py --check-deps', 'python3 android/build.py']
    else:
        lines += ['# Set the path to the separately supplied, content-locked Klickwerk board sources:',
                  'export KLICKWERK_SOURCE=/path/to/klickwerk2',
                  '# PlatformIO downloads the pinned platform, ESP-IDF and compiler on first build.',
                  'python3 esp32p4/build.py --check-deps', 'python3 esp32p4/build.py']
    if profile == 'unsupported':
        lines.insert(2, '# This build supports macOS and Linux; use a supported host.')
    if android and platform.system() == 'Linux' and platform.machine() not in ('x86_64', 'AMD64'):
        lines.insert(2, '# Android NDK Linux host tools require x86_64; use an x86_64 host or VM.')
    return '\n'.join(lines)


def missing_dependencies(target, selected, abi='arm64-v8a', pio='pio', cc='cc', nm='nm'):
    missing = []
    if sys.version_info < (3, 10):
        missing.append('Python 3.10 or newer')
    if host_profile() == 'unsupported':
        missing.append('Supported host: macOS or Linux')
    if not executable('git'):
        missing.append('Git')
    for module, package in [('PIL', 'Pillow'), ('fontTools', 'fonttools')]:
        if importlib.util.find_spec(module) is None:
            missing.append(f'Python package {package} (for {sys.executable})')
    for f in selected:
        root = Path(os.environ.get(f.source_env, PROJECT/f.default_source)).expanduser()
        if not (root/'firmware/src/core.h').is_file():
            missing.append(f'{f.name} sources at {root} (initialize submodules or set {f.source_env})')
    if target == 'android':
        for tool in ('java', 'javac', 'keytool'):
            if not executable(java_tool(tool)):
                missing.append(f'JDK tool {java_tool(tool)}')
        if executable(java_tool('javac')):
            try:
                version = subprocess.run([java_tool('javac'), '-version'], capture_output=True, text=True, timeout=10)
                match = re.search(r'javac (\d+)', version.stdout + version.stderr)
                if version.returncode or not match or int(match[1]) < 17:
                    missing.append('Working JDK 17+ (17 or 21 recommended)')
            except (OSError, subprocess.TimeoutExpired):
                missing.append('Working JDK 17+ (javac could not run)')
        sdk, ndk, toolchain = android_paths()
        if not (sdk/'platforms/android-35/android.jar').is_file():
            missing.append(f'Android SDK platform 35 at {sdk}')
        for tool in ('aapt2', 'd8', 'zipalign', 'apksigner'):
            if not executable(sdk/'build-tools/35.0.0'/tool):
                missing.append(f'Android build-tools 35.0.0: {tool}')
        target_triple = {'arm64-v8a': 'aarch64-linux-android26', 'x86_64': 'x86_64-linux-android26'}[abi]
        for tool in (target_triple+'-clang', target_triple+'-clang++'):
            if not executable(toolchain/tool):
                missing.append(f'Android NDK compiler {tool} at {ndk}')
        if platform.system() == 'Linux' and platform.machine() not in ('x86_64', 'AMD64'):
            missing.append('x86_64 Linux host for the Android NDK')
    else:
        for command in (pio, cc, nm):
            if not executable(command):
                missing.append(f'Executable {command}')
        kw = Path(os.environ.get('KLICKWERK_SOURCE', PROJECT.parent.parent/'klickwerk2')).expanduser()
        if not (kw/'firmware/p4/src').is_dir():
            missing.append(f'Klickwerk board sources at {kw} (set KLICKWERK_SOURCE)')
    return missing


def check_dependencies(target, selected, **kwargs):
    missing = missing_dependencies(target, selected, **kwargs)
    if missing:
        print('Missing build prerequisites:\n' + '\n'.join('  - '+item for item in missing), file=sys.stderr)
        print('\nInstallation commands:\n' + setup_commands(target), file=sys.stderr)
        raise SystemExit(1)
    print(f'{target}: build prerequisites found.')
