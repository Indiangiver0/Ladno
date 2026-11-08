"""Independent, read-only APK inspection; never installs or starts the app."""
from pathlib import Path
import hashlib
import json
import re
import struct
import subprocess
import zipfile

PROJECT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
TOOLS = Path(r'C:\Users\04068\.cache\ladno-android')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def run(name, command):
    result = subprocess.run([str(p) for p in command], capture_output=True)
    text = (result.stdout + result.stderr).decode('utf-8', errors='replace')
    (OUT / (name + '.txt')).write_text(text, encoding='utf-8')
    return {'command': [str(p) for p in command], 'exitCode': result.returncode, 'output': text}


def inspect():
    state = json.loads((TOOLS / 'toolchain.json').read_text(encoding='utf-8'))
    build = json.loads((PROJECT / 'dist/build-result.json').read_text(encoding='utf-8'))
    apk = PROJECT / 'dist' / build['apk']
    sdk = TOOLS / 'sdk/build-tools/35.0.0'
    checks = []
    def check(name, actual, expected):
        checks.append({'id': name, 'actual': actual, 'expected': expected, 'pass': actual == expected})
    check('apk/hashMatchesBuildRecord', sha(apk.read_bytes()), build['sha256'])
    check('apk/expectedFinalSha', build['sha256'], '38c9d4490419eda3685e7187281d5f1ecfb958a95d77c57f7f59633febcfbd5a')
    check('apk/expectedFinalBytes', apk.stat().st_size, 37404606)
    check('apk/expectedSourceFingerprint', build['sourceFingerprint'], 'b5b8d938295f9489124bec82ad0c07c0aa86fcde8f0d1c5bd8cafc343db57b03')
    signature = run('signature', [Path(state['javaHome']) / 'bin/java.exe', '-jar', sdk / 'lib/apksigner.jar', 'verify', '--verbose', '--print-certs', apk])
    check('signature/toolSuccess', signature['exitCode'], 0)
    badging = run('badging', [sdk / 'aapt2.exe', 'dump', 'badging', apk])
    check('badging/toolSuccess', badging['exitCode'], 0)
    for token in ["package: name='com.ladno.finance'", "versionCode='1'", "versionName='0.1.0'", "minSdkVersion:'26'", "targetSdkVersion:'36'", "launchable-activity: name='com.ladno.finance.MainActivity'"]:
        check('badging/' + token, token in badging['output'], True)
    permissions = run('permissions', [sdk / 'aapt2.exe', 'dump', 'permissions', apk])
    check('permissions/toolSuccess', permissions['exitCode'], 0)
    for permission in ['INTERNET', 'READ_SMS', 'RECEIVE_SMS', 'CAMERA', 'BIND_ACCESSIBILITY_SERVICE']:
        check('permissions/no-' + permission, ('android.permission.' + permission) in permissions['output'], False)
    tree = run('manifest', [sdk / 'aapt2.exe', 'dump', 'xmltree', '--file', 'AndroidManifest.xml', apk])
    check('manifest/toolSuccess', tree['exitCode'], 0)
    for token in ['android.permission.BIND_NOTIFICATION_LISTENER_SERVICE', 'com.ladno.finance.BankNotificationService', 'androidx.core.content.FileProvider', 'com.ladno.finance.NoBackupAgent']:
        check('manifest/' + token, token in tree['output'], True)
    component_blocks = re.split(r'(?m)^\s+E: (activity|service|provider|receiver) \(line=\d+\)', tree['output'])
    components = {}
    for index in range(1, len(component_blocks), 2):
        block = component_blocks[index + 1]
        name = re.search(r':name\([^)]*\)="([^"]+)"', block).group(1)
        exported = re.search(r':exported\([^)]*\)=(true|false)', block)
        permission = re.search(r':permission\([^)]*\)="([^"]+)"', block)
        components[name] = {'type': component_blocks[index], 'exported': exported.group(1) if exported else None,
                            'permission': permission.group(1) if permission else None}
    expected_components = {
        'com.ladno.finance.MainActivity': {'type': 'activity', 'exported': 'true', 'permission': None},
        'com.ladno.finance.BankNotificationService': {'type': 'service', 'exported': 'true', 'permission': 'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE'},
        'androidx.core.content.FileProvider': {'type': 'provider', 'exported': 'false', 'permission': None},
        'androidx.startup.InitializationProvider': {'type': 'provider', 'exported': 'false', 'permission': None},
        'androidx.profileinstaller.ProfileInstallReceiver': {'type': 'receiver', 'exported': 'true', 'permission': 'android.permission.DUMP'},
    }
    check('manifest/components-and-export-boundaries', components, expected_components)
    for name, expected in [('allowBackup', 'false'), ('usesCleartextTraffic', 'false'), ('debuggable', 'true'), ('extractNativeLibs', 'false')]:
        actual = re.search(':' + name + r'\([^)]*\)=(true|false)', tree['output'])
        check('manifest/' + name, actual.group(1) if actual else None, expected)
    alignment = run('zipalign-16kb', [sdk / 'zipalign.exe', '-c', '-P', '16', '-v', '4', apk])
    check('alignment/zipalign16kb', alignment['exitCode'], 0)
    build_sources = json.loads((PROJECT / 'dist/build-source-manifest.json').read_text(encoding='utf-8'))
    check('sourceManifest/fingerprint', build_sources['sourceFingerprint'], build['sourceFingerprint'])
    mismatches = [item['path'] for item in build_sources['files'] if sha((PROJECT / item['path']).read_bytes()) != item['sha256']]
    check('sourceManifest/all-current-source-hashes', mismatches, [])
    lock = json.loads((PROJECT / 'ocr-models.lock.json').read_text(encoding='utf-8'))
    archive_details = {}
    with zipfile.ZipFile(apk) as bundle:
        for name in ['index.html', 'styles.css', 'view.js', 'bridge.js', 'app.js', 'preview-adapter.js']:
            path = 'assets/www/' + name
            actual = sha(bundle.read(path))
            expected = sha((PROJECT / 'app/src/main/assets/www' / name).read_bytes())
            check('asset/current-' + name, actual, expected)
        for language, model in lock['models'].items():
            check('model/' + language, sha(bundle.read('assets/tessdata/' + language + '.traineddata')), model['sha256'])
        check('schema/current', sha(bundle.read('assets/db/schema.sql')), sha((PROJECT / 'app/src/main/assets/db/schema.sql').read_bytes()))
        archive_details['abis'] = sorted({p.split('/')[1] for p in bundle.namelist() if p.startswith('lib/') and p.endswith('.so')})
        for path in ['assets/licenses/Apache-2.0.txt', 'assets/licenses/Leptonica.txt', 'assets/licenses/IJG-JPEG.txt', 'assets/licenses/libpng.txt', 'assets/licenses/THIRD_PARTY_NOTICES.txt']:
            check('license/' + path, path in bundle.namelist(), True)
        archive_details['nativeLibraries'] = sorted(p for p in bundle.namelist() if p.startswith('lib/') and p.endswith('.so'))
        archive_details['elf64LoadAlignments'] = {}
        for path in archive_details['nativeLibraries']:
            if path.split('/')[1] not in ['arm64-v8a', 'x86_64']:
                continue
            data = bundle.read(path)
            if data[:4] != b'\x7fELF' or data[4] != 2:
                raise ValueError('Expected ELF64: ' + path)
            order = '<' if data[5] == 1 else '>'
            phoff = struct.unpack_from(order + 'Q', data, 32)[0]
            phentsize, phnum = struct.unpack_from(order + 'HH', data, 54)
            loads = []
            for i in range(phnum):
                offset = phoff + i * phentsize
                if struct.unpack_from(order + 'I', data, offset)[0] == 1:
                    loads.append(struct.unpack_from(order + 'Q', data, offset + 48)[0])
            archive_details['elf64LoadAlignments'][path] = loads
            check('alignment/elf64-' + path, bool(loads) and all(value >= 16384 for value in loads), True)
        check('dex/present', 'classes.dex' in bundle.namelist(), True)
    result = {'build': build, 'apkBytes': apk.stat().st_size, 'checks': checks, 'commands': [signature, badging, permissions, tree, alignment], 'archive': archive_details,
              'harnessCorrections': ["Initial oracle incorrectly sought sdkVersion:'26'; aapt2 prints minSdkVersion:'26'. Initial JSON retained as apk-results-initial-harness.json."],
              'limitations': ['No installation, Android lifecycle, SQLite runtime, OCR JNI, NLS delivery, Keystore or OEM backup execution performed.']}
    (OUT / 'apk-results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'checks': len(checks), 'failed': [c['id'] for c in checks if not c['pass']], 'sha256': build['sha256']}))
    return result


if __name__ == '__main__':
    inspect()
