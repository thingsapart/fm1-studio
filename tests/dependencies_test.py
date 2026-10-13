"""Preflight tests do not install packages or invoke a compiler."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'tools'))
import dependencies as deps


class DependenciesTest(unittest.TestCase):
    def test_distro_families_and_derivatives(self):
        for name, like, expected in [('ubuntu', 'debian', 'debian'), ('pop', 'ubuntu', 'debian'),
                                     ('rocky', '"rhel centos fedora"', 'fedora'),
                                     ('manjaro', 'arch', 'arch'), ('opensuse-tumbleweed', 'suse', 'suse'),
                                     ('custom', '', 'linux')]:
            self.assertEqual(deps.host_profile('Linux', {'ID': name, 'ID_LIKE': like}), expected)
        self.assertEqual(deps.host_profile('Darwin'), 'macos')
        self.assertEqual(deps.host_profile('Windows'), 'unsupported')

    def test_setup_commands_for_each_supported_family(self):
        for profile, manager in [('debian', 'apt-get'), ('fedora', 'dnf'), ('arch', 'pacman'),
                                 ('suse', 'zypper'), ('macos', 'brew')]:
            for target in ['android', 'esp32p4']:
                commands = deps.setup_commands(target, profile)
                self.assertIn(manager, commands)
                self.assertIn('python3 -m venv .venv', commands)
                self.assertIn('git submodule update --init --recursive', commands)
                if target == 'android':
                    self.assertIn('"ndk;27.2.12479018"', commands)
                    self.assertIn('"platforms;android-35"', commands)
                    self.assertIn('"build-tools;35.0.0"', commands)
                else:
                    self.assertIn('platformio', commands)
                    self.assertIn('KLICKWERK_SOURCE', commands)
                subprocess.run(['sh', '-n'], input=commands, text=True, check=True)

    def test_android_paths_respect_explicit_env_and_spaces(self):
        with patch.dict(os.environ, {'ANDROID_SDK_ROOT': '/tmp/sdk with spaces',
                                     'ANDROID_NDK_ROOT': '/tmp/custom ndk'}, clear=True):
            sdk, ndk, compiler = deps.android_paths()
            self.assertEqual(sdk, Path('/tmp/sdk with spaces').resolve())
            self.assertEqual(ndk, Path('/tmp/custom ndk').resolve())
            self.assertTrue(compiler.is_relative_to(ndk))
        with patch.dict(os.environ, {'ANDROID_HOME': '/tmp/android-home'}, clear=True):
            self.assertEqual(deps.android_paths()[0], Path('/tmp/android-home').resolve())

    def test_missing_tools_and_python_packages_are_reported_together(self):
        with patch.object(deps, 'executable', return_value=False), \
             patch.object(deps.importlib.util, 'find_spec', return_value=None):
            missing = deps.missing_dependencies('android', [])
        for text in ['Pillow', 'fonttools', 'javac', 'aapt2', 'NDK compiler']:
            self.assertTrue(any(text in item for item in missing), text)

    def test_old_jdk_rejected(self):
        version = subprocess.CompletedProcess([], 0, stdout='javac 11.0.20', stderr='')
        with patch.object(deps, 'executable', return_value=True), \
             patch.object(deps.subprocess, 'run', return_value=version):
            missing = deps.missing_dependencies('android', [])
        self.assertTrue(any('JDK 17+' in item for item in missing))

    def test_os_release_parser(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'os-release'
            path.write_text('# ignored\nID=ubuntu\nID_LIKE="debian"\nNAME="Ubuntu Linux"\n')
            self.assertEqual(deps.host_profile('Linux', deps.linux_release(path)), 'debian')

    def test_cli_modes_do_not_start_builds(self):
        root = Path(__file__).resolve().parents[1]
        for target in ['android', 'esp32p4']:
            result = subprocess.run([sys.executable, root/target/'build.py', '--setup'], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn('printed only', result.stdout)
        with tempfile.TemporaryDirectory() as folder:
            env = dict(os.environ, ANDROID_SDK_ROOT=folder, ANDROID_NDK_ROOT=folder)
            result = subprocess.run([sys.executable, root/'android/build.py', '--check-deps'],
                                    env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode, 1)
            self.assertIn('Missing build prerequisites:', result.stderr)
            self.assertIn('Installation commands:', result.stderr)
            self.assertNotIn('Traceback', result.stderr)


if __name__ == '__main__':
    unittest.main()
