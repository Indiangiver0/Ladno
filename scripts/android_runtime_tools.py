"""Provision official emulator tools and check acceleration; never change host settings."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import time

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
ROOT = Path(__file__).resolve().parents[1]
CACHE = Path('C:/Users/04068/.cache/ladno-android')
spec = importlib.util.spec_from_file_location('ladno_build', ROOT / 'android-app/scripts/build_android.py')
build_tools = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build_tools)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--probe', action='store_true')
    parser.add_argument('--boot', action='store_true')
    args = parser.parse_args()
    state = json.loads((CACHE / 'toolchain.json').read_text(encoding='utf-8'))
    sdk, java = Path(state['sdkRoot']), Path(state['javaHome'])
    if sdk.resolve() != (CACHE / 'sdk').resolve() or not java.resolve().is_relative_to(CACHE.resolve()):
        raise RuntimeError('Unexpected toolchain directory')
    evidence = ROOT / 'android-app/qa/runtime-host'
    evidence.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ)
    env.update(JAVA_HOME=str(java), ANDROID_SDK_ROOT=str(sdk), ANDROID_HOME=str(sdk),
               JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US',
               ANDROID_USER_HOME=str(CACHE / 'android-user'),
               ANDROID_EMULATOR_HOME=str(CACHE / 'emulator-user'),
               ANDROID_AVD_HOME=str(CACHE / 'avd'),
               PATH=str(java / 'bin') + os.pathsep + env.get('PATH', ''))
    # Emulator requires these task-local homes to exist before it creates locks.
    for key in ('ANDROID_USER_HOME', 'ANDROID_EMULATOR_HOME', 'ANDROID_AVD_HOME'):
        Path(env[key]).mkdir(parents=True, exist_ok=True)
    emulator = sdk / 'emulator/emulator.exe'
    if not emulator.is_file():
        # Previously accepted SDK licence hashes are reused; decline any NEW terms.
        build_tools.run([sdk / 'cmdline-tools/latest/bin/sdkmanager.bat', '--sdk_root=' + str(sdk), '--verbose', 'emulator'],
                        env=env, input_text='n\n' * 12,
                        log_file=evidence / 'sdk-emulator-install.log')
    command = [str(emulator), '-accel-check']
    completed = subprocess.run(command, env=env, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=60)
    result = {'command': command, 'exitCode': completed.returncode,
              'stdout': completed.stdout, 'stderr': completed.stderr,
              'hostSettingsChanged': False, 'deviceOrAvdCreated': False}
    (evidence / 'acceleration.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result))
    if completed.returncode == 0 and args.boot:
        image = 'system-images;android-30;default;x86_64'
        image_file = sdk / 'system-images/android-30/default/x86_64/system.img'
        if not image_file.is_file():
            build_tools.run([sdk / 'cmdline-tools/latest/bin/sdkmanager.bat', '--sdk_root=' + str(sdk), '--verbose', image],
                            env=env, input_text='n\n' * 12, log_file=evidence / 'sdk-image-install.log')
        avd = CACHE / 'avd/Ladno_QA_API30.avd'
        avd.parent.mkdir(exist_ok=True)
        if not (avd / 'config.ini').is_file():
            build_tools.run([sdk / 'cmdline-tools/latest/bin/avdmanager.bat', 'create', 'avd',
                             '--name', 'Ladno_QA_API30', '--package', image,
                             '--path', avd, '--device', 'pixel_2'],
                            env=env, input_text='no\n', log_file=evidence / 'avd-create.log')
        adb = sdk / 'platform-tools/adb.exe'
        serial = 'emulator-5580'
        with (evidence / 'emulator.log').open('w', encoding='utf-8') as output:
            process = subprocess.Popen([str(emulator), '-avd', 'Ladno_QA_API30', '-port', '5580',
                                        '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot',
                                        '-gpu', 'swiftshader', '-memory', '1536', '-cores', '2', '-accel', 'on'],
                                       env=env, stdout=output, stderr=subprocess.STDOUT,
                                       creationflags=subprocess.CREATE_NO_WINDOW)
        print('Starting dedicated headless test emulator, PID=' + str(process.pid), flush=True)
        started = time.monotonic()
        booted = False
        while time.monotonic() - started < 300:
            if process.poll() is not None:
                raise RuntimeError('Emulator exited; inspect runtime-host/emulator.log')
            probe = subprocess.run([str(adb), '-s', serial, 'shell', 'getprop', 'sys.boot_completed'],
                                   env=env, capture_output=True, text=True, timeout=15)
            if probe.returncode == 0 and probe.stdout.strip() == '1':
                booted = True
                break
            print('Waiting for Android boot, elapsed=' + str(round(time.monotonic() - started)), flush=True)
            time.sleep(5)
        if not booted:
            process.terminate()
            raise RuntimeError('Bounded 300-second Android boot timeout; test emulator stopped')
        name = subprocess.run([str(adb), '-s', serial, 'emu', 'avd', 'name'], env=env,
                              capture_output=True, text=True, timeout=15)
        if name.stdout.splitlines()[0].strip() != 'Ladno_QA_API30':
            raise RuntimeError('Unexpected emulator identity; no app installation allowed')
        details = {'serial': serial, 'avd': 'Ladno_QA_API30', 'pid': process.pid,
                   'bootSeconds': round(time.monotonic() - started), 'hostSettingsChanged': False}
        for field, prop in [('release', 'ro.build.version.release'), ('api', 'ro.build.version.sdk'), ('abi', 'ro.product.cpu.abi')]:
            details[field] = subprocess.run([str(adb), '-s', serial, 'shell', 'getprop', prop], env=env,
                                            capture_output=True, text=True, timeout=15).stdout.strip()
        (evidence / 'emulator-state.json').write_text(json.dumps(details, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(details), flush=True)
    return completed.returncode


if __name__ == '__main__':
    sys.exit(main())
