"""Compile actual application core with real org.json; no Android stubs on classpath."""
import hashlib
import json
import pathlib
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
TOOLS = pathlib.Path.home() / '.cache/ladno-android'
JAVA = TOOLS / 'jdk21/jdk-21.0.13+9/bin'
JSON_JAR = next((TOOLS / 'gradle-cache/caches/modules-2/files-2.1/org.json/json/20240303').rglob('*.jar'))
sources = sorted((ROOT / 'app/src/main/java/com/ladno/finance/core').glob('*.java'))
classes = HERE / 'classes'
classes.mkdir(exist_ok=True)
manifest = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
manifest['fixtures/money-cases.json'] = hashlib.sha256((ROOT / 'fixtures/money-cases.json').read_bytes()).hexdigest()
manifest['org.json:json:20240303'] = hashlib.sha256(JSON_JAR.read_bytes()).hexdigest()
(HERE / 'tested-source-hashes.json').write_text(json.dumps(manifest, indent=2) + '\n')
compile_cmd = [str(JAVA / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-cp', str(JSON_JAR), '-d', str(classes), *map(str, sources), str(HERE / 'CalculationQa.java')]
execute_cmd = [str(JAVA / 'java.exe'), '-cp', str(classes) + ';' + str(JSON_JAR), 'CalculationQa', str(ROOT / 'fixtures/money-cases.json'), str(HERE / 'jvm-results.json')]
(HERE / 'commands.json').write_text(json.dumps({'compile': compile_cmd, 'run': execute_cmd}, indent=2) + '\n')
for name, command in [('javac', compile_cmd), ('java', execute_cmd)]:
    result = subprocess.run(command, capture_output=True, encoding='utf-8', errors='replace')
    (HERE / (name + '.log')).write_text(result.stdout + result.stderr, encoding='utf-8')
    print(name + ': exit ' + str(result.returncode))
    print(result.stdout + result.stderr)
    if result.returncode:
        sys.exit(result.returncode)
for source in sources:
    if hashlib.sha256(source.read_bytes()).hexdigest() != manifest[str(source.relative_to(ROOT))]:
        raise RuntimeError('Source changed during the independent test: ' + str(source))
