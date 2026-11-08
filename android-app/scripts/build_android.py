"""Provision official Windows Android build tools and build an ASCII-path copy.

No machine-wide installation or PATH changes. SDK licence acceptance requires
the explicit --accept-sdk-license flag. All generated tool/cache paths remain
under the explicit --tools-root. Sources remain in the shared project.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import urllib.request
import zipfile

# Tool output is UTF-8 even when the Windows parent console uses a legacy code page.
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
sys.stderr.reconfigure(encoding='utf-8', errors='replace')

PROJECT = Path(__file__).resolve().parents[1]
VERSIONS = json.loads((PROJECT / 'build-versions.json').read_text(encoding='utf-8'))
HEADERS = {'User-Agent': 'LadnoFinanceBuild/0.1', 'Accept': 'application/json'}


def log(message):
    print(message, flush=True)


def inside(path: Path, root: Path):
    resolved = path.resolve()
    if resolved == root.resolve() or not resolved.is_relative_to(root.resolve()):
        raise ValueError(f'Unsafe generated target: {resolved}')
    return resolved


def get_bytes(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=120) as response:
        return response.read()


def get_json(url):
    return json.loads(get_bytes(url).decode('utf-8'))


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def download(url, target: Path, expected: str | None = None):
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.is_file() and (expected is None or sha256(target) == expected.lower()):
        return target
    partial = target.with_suffix(target.suffix + '.part')
    log(f'Download: {url}')
    curl = shutil.which('curl.exe')
    if curl:
        command = [curl, '--location', '--fail', '--connect-timeout', '20',
                   '--speed-limit', '1024', '--speed-time', '45', '--max-time', '900',
                   '--retry', '2', '--retry-delay', '3', '--output', str(partial), url]
        subprocess.run(command, check=True, timeout=950)
    else:
        with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=180) as response, partial.open('wb') as stream:
            shutil.copyfileobj(response, stream, 1024 * 1024)
    actual = sha256(partial)
    if expected is not None and actual != expected.lower():
        raise ValueError(f'SHA-256 mismatch for {target.name}: {actual}')
    os.replace(partial, target)
    log(f'Verified {target.name}: {actual}')
    return target


def extract(archive: Path, destination: Path, tools: Path):
    inside(destination, tools)
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as bundle:
        for member in bundle.infolist():
            target = (destination / member.filename).resolve()
            if not target.is_relative_to(destination.resolve()):
                raise ValueError('Zip path escapes destination')
            if member.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with bundle.open(member) as source, target.open('wb') as output:
                    shutil.copyfileobj(source, output)


def run(command, *, cwd=None, env=None, input_text=None, log_file=None):
    log('Run: ' + subprocess.list2cmdline([str(item) for item in command]))
    process = subprocess.Popen([str(item) for item in command], cwd=cwd, env=env,
                               stdin=subprocess.PIPE if input_text is not None else None,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               text=True, encoding='utf-8', errors='replace')
    if input_text is not None:
        process.stdin.write(input_text)
        process.stdin.close()
    output = []
    for line in process.stdout:
        print(line, end='', flush=True)
        output.append(line)
    code = process.wait()
    if log_file:
        log_file.parent.mkdir(parents=True, exist_ok=True)
        log_file.write_text(''.join(output), encoding='utf-8')
    if code:
        raise RuntimeError(f'Command failed with exit {code}')


def provision(tools: Path, accept_license: bool):
    tools.mkdir(parents=True, exist_ok=True)
    cache = inside(tools / 'downloads', tools)
    cache.mkdir(parents=True, exist_ok=True)
    state_file = tools / 'toolchain.json'
    state = json.loads(state_file.read_text(encoding='utf-8')) if state_file.exists() else {}
    java = Path(state.get('javaHome', tools / 'no-jdk'))
    if not (java / 'bin/java.exe').is_file():
        log('Resolve official Eclipse Temurin JDK 21 Windows metadata')
        metadata = get_json('https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse')
        package = metadata[0]['binary']['package']
        archive = download(package['link'], cache / package['name'], package['checksum'])
        jdk_root = inside(tools / 'jdk21', tools)
        extract(archive, jdk_root, tools)
        candidates = [item for item in jdk_root.iterdir() if (item / 'bin/java.exe').is_file()]
        if len(candidates) != 1:
            raise RuntimeError('Expected exactly one extracted JDK')
        java = candidates[0]
        state['javaHome'] = str(java)
        state['jdkPackage'] = package
        state_file.write_text(json.dumps(state, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        log('JDK ready: ' + str(java))
    gradle = tools / ('gradle-' + VERSIONS['gradle'])
    if not (gradle / 'bin/gradle.bat').is_file():
        url = 'https://downloads.gradle.org/distributions/gradle-' + VERSIONS['gradle'] + '-bin.zip'
        expected = get_bytes(url + '.sha256').decode().strip().split()[0]
        archive = download(url, cache / ('gradle-' + VERSIONS['gradle'] + '-bin.zip'), expected)
        extract(archive, tools, tools.parent)
        state['gradleSha256'] = expected
    sdk = inside(tools / 'sdk', tools)
    manager = sdk / 'cmdline-tools/latest/bin/sdkmanager.bat'
    if not manager.is_file():
        if not accept_license:
            raise RuntimeError('Downloading Android SDK tools requires explicit SDK licence approval: ' + VERSIONS['sdkTerms'])
        info = VERSIONS['commandLineTools']
        archive = download(info['url'], cache / info['url'].rsplit('/', 1)[1], info['sha256'])
        unpacked = inside(tools / 'sdk-cli-unpacked', tools)
        extract(archive, unpacked, tools)
        destination = inside(sdk / 'cmdline-tools/latest', tools)
        shutil.copytree(unpacked / 'cmdline-tools', destination, dirs_exist_ok=True)
    env = dict(os.environ)
    env.update(JAVA_HOME=str(java), ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk),
               ANDROID_USER_HOME=str(tools / 'android-user'),
               GRADLE_USER_HOME=str(tools / 'gradle-cache'),
               PATH=str(java / 'bin') + os.pathsep + os.environ.get('PATH', ''))
    required = [sdk / 'platforms/android-36/android.jar', sdk / 'build-tools/35.0.0/aapt2.exe', sdk / 'platform-tools/adb.exe']
    if not all(path.is_file() for path in required):
        if not accept_license:
            raise RuntimeError('SDK installation requires explicit --accept-sdk-license after user approval: ' + VERSIONS['sdkTerms'])
        log('Explicit approval flag supplied for Android SDK licence: ' + VERSIONS['sdkTerms'])
        # Accept only licences required by these three selected packages.
        run([manager, '--sdk_root=' + str(sdk), 'platform-tools', 'platforms;android-36', 'build-tools;35.0.0'], env=env,
            input_text='y\n' * 12,
            log_file=tools / 'logs/sdk-install.txt')
    state.update(gradleHome=str(gradle), sdkRoot=str(sdk), versions=VERSIONS)
    state_file.write_text(json.dumps(state, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return state, env


def models(tools):
    lock_file = PROJECT / 'ocr-models.lock.json'
    lock = json.loads(lock_file.read_text(encoding='utf-8')) if lock_file.exists() else {}
    if 'commit' not in lock:
        lock['commit'] = get_json('https://api.github.com/repos/tesseract-ocr/tessdata_fast/commits/main')['sha']
        lock['repository'] = VERSIONS['trainedDataSource']
        lock['models'] = {}
    destination = PROJECT / 'app/src/main/assets/tessdata'
    destination.mkdir(parents=True, exist_ok=True)
    for language in VERSIONS['ocrLanguages']:
        target = destination / (language + '.traineddata')
        known = lock['models'].get(language)
        if known and target.is_file() and sha256(target) == known['sha256']:
            continue
        metadata = get_json('https://api.github.com/repos/tesseract-ocr/tessdata_fast/contents/' + language + '.traineddata?ref=' + lock['commit'])
        download(metadata['download_url'], target, known['sha256'] if known else None)
        data = target.read_bytes()
        git_sha = hashlib.sha1(('blob ' + str(len(data)) + '\0').encode() + data).hexdigest()
        if git_sha != metadata['sha']:
            raise ValueError('Git object hash mismatch for ' + language)
        lock['models'][language] = {'sha256': sha256(target), 'gitBlobSha1': git_sha, 'bytes': len(data)}
    licence = destination / 'LICENSE.txt'
    if not licence.is_file():
        licence.write_bytes(get_bytes('https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/' + lock['commit'] + '/LICENSE'))
    lock_file.write_text(json.dumps(lock, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def source_files():
    excluded = {'.gradle', 'build', 'dist', '.local', '__pycache__', 'qa'}
    return sorted(path for path in PROJECT.rglob('*') if path.is_file()
                  and not excluded.intersection(path.relative_to(PROJECT).parts)
                  and path.name != 'local.properties')


def best_models():
    # A second, pinned network verifies FAST recognition; no runtime download.
    lock = json.loads((PROJECT / 'ocr-verification-model.lock.json').read_text(encoding='utf-8'))
    if not re.fullmatch(r'[0-9a-f]{40}', lock['commit']) or set(lock['models']) != {'eng', 'rus', 'kaz'}:
        raise ValueError('Invalid pinned BEST model lock')
    destination = PROJECT / 'app/src/main/assets/tessdata-best'
    destination.mkdir(parents=True, exist_ok=True)
    base = 'https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/' + lock['commit'] + '/'
    for language, known in lock['models'].items():
        target = destination / (language + '.traineddata')
        if not target.is_file() or sha256(target) != known['sha256']:
            download(base + language + '.traineddata', target, known['sha256'])
        data = target.read_bytes()
        git_sha = hashlib.sha1(('blob ' + str(len(data)) + '\0').encode() + data).hexdigest()
        if len(data) != known['bytes'] or git_sha != known['gitBlobSha1']:
            raise ValueError('BEST Git object mismatch: ' + language)
    license_file = destination / 'LICENSE.txt'
    if not license_file.is_file() or sha256(license_file) != lock['licenseSha256']:
        download(base + 'LICENSE', license_file, lock['licenseSha256'])


def notices():
    destination = PROJECT / 'app/src/main/assets/licenses'
    destination.mkdir(parents=True, exist_ok=True)
    base = 'https://raw.githubusercontent.com/adaptech-cz/Tesseract4Android/4.9.0/'
    entries = {
        'Apache-2.0.txt': 'LICENSE',
        'Leptonica.txt': 'tesseract4android/src/main/cpp/leptonica/src/leptonica-license.txt',
        'IJG-JPEG.txt': 'tesseract4android/src/main/cpp/libjpeg/src/README',
        'libpng.txt': 'tesseract4android/src/main/cpp/libpng/src/LICENSE',
    }
    manifest = {}
    for name, relative in entries.items():
        target = destination / name
        if not target.is_file():
            target.write_bytes(get_bytes(base + relative))
        manifest[name] = {'source': base + relative, 'sha256': sha256(target)}
    (destination / 'sources.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')


def build(tools, state, env, tasks):
    files = source_files()
    fingerprint = hashlib.sha256()
    expected_files = {}
    for source in files:
        data = source.read_bytes()
        fingerprint.update(source.relative_to(PROJECT).as_posix().encode() + b'\0')
        fingerprint.update(data)
        expected_files[source] = hashlib.sha256(data).hexdigest()
    stage = inside(tools / 'work' / ('ladno-' + fingerprint.hexdigest()[:16]), tools)
    stage.mkdir(parents=True, exist_ok=True)
    for source in files:
        target = inside(stage / source.relative_to(PROJECT), stage)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        if sha256(target) != expected_files[source]:
            raise RuntimeError('Source changed during staging; retry after its author finishes: ' + str(source))
    sdk = state['sdkRoot'].replace('\\', '/')
    (stage / 'local.properties').write_text('sdk.dir=' + sdk.replace(':', '\\:') + '\n', encoding='utf-8')
    keystore = inside(tools / 'signing/debug.keystore', tools)
    keystore.parent.mkdir(parents=True, exist_ok=True)
    if not keystore.is_file():
        run([Path(state['javaHome']) / 'bin/keytool.exe', '-genkeypair', '-keystore', keystore,
             '-storepass', 'android', '-keypass', 'android', '-alias', 'androiddebugkey',
             '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
             '-dname', 'CN=Ladno Development,O=Local,C=ZZ'], env=env)
    env['LADNO_DEBUG_KEYSTORE'] = str(keystore)
    dist = PROJECT / 'dist'
    dist.mkdir(exist_ok=True)
    (dist / 'build-source-manifest.json').write_text(json.dumps({
        'sourceFingerprint': fingerprint.hexdigest(),
        'files': [{'path': source.relative_to(PROJECT).as_posix(), 'sha256': expected_files[source]}
                  for source in files]
    }, indent=2) + '\n', encoding='utf-8')
    run([Path(state['gradleHome']) / 'bin/gradle.bat', '--no-daemon', '--console=plain',
         '-g', tools / 'gradle-cache', *tasks], cwd=stage, env=env, log_file=dist / 'gradle-build.log')
    apk = stage / 'app/build/outputs/apk/debug/app-debug.apk'
    if apk.exists():
        config = (stage / 'app/build.gradle').read_text(encoding='utf-8')
        version_name = re.search(r"versionName\s+'([0-9]+\.[0-9]+\.[0-9]+)'", config)
        version_code = re.search(r'versionCode\s+(\d+)', config)
        if not version_name or not version_code:
            raise RuntimeError('Missing explicit app version')
        output = dist / ('ladno-' + version_name.group(1) + '-debug.apk')
        shutil.copy2(apk, output)
        result = {'apk': output.name, 'sha256': sha256(output), 'bytes': output.stat().st_size,
                  'stage': str(stage), 'sourceFingerprint': fingerprint.hexdigest(),
                  'versionName': version_name.group(1), 'versionCode': int(version_code.group(1)),
                  'tasks': tasks, 'builtAtUtc': datetime.now(timezone.utc).isoformat()}
        (dist / 'build-result.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        log(json.dumps(result))
    for name in ['reports', 'test-results']:
        source = stage / 'app/build' / name
        if source.is_dir():
            shutil.copytree(source, dist / name, dirs_exist_ok=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tools-root', type=Path, required=True)
    parser.add_argument('--accept-sdk-license', action='store_true')
    parser.add_argument('--provision-only', action='store_true')
    parser.add_argument('--tasks', nargs='+', default=[':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug'])
    args = parser.parse_args()
    root = args.tools_root.resolve()
    if root == Path(root.anchor) or root == Path.home():
        raise ValueError('Choose a dedicated tool/cache directory')
    state, environment = provision(root, args.accept_sdk_license)
    models(root)
    best_models()
    notices()
    if not args.provision_only:
        build(root, state, environment, args.tasks)
