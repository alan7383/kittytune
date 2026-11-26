"""Run the actual batch exclusion updater without an Android SDK."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--dependency-cache', type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    java_home = args.java_home or Path(os.environ['JAVA_HOME'])
    java = str(java_home / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))
    jars = output / 'dependencies'
    jars.mkdir(exist_ok=True)

    def dep(group, artifact, version):
        if args.dependency_cache:
            path = args.dependency_cache / 'caches/modules-2/files-2.1' / group / artifact / version
            matches = sorted(path.glob(f'*/{artifact}-{version}.jar'))
            if matches:
                return matches[0]
        target = jars / f'{artifact}-{version}.jar'
        if not target.exists():
            url = f'https://repo.maven.apache.org/maven2/{group.replace(".", "/")}/{artifact}/{version}/{target.name}'
            with urllib.request.urlopen(url, timeout=90) as response:
                target.write_bytes(response.read())
        return target

    kotlin = '2.4.21'
    stdlib = dep('org.jetbrains.kotlin', 'kotlin-stdlib', kotlin)
    coroutines = dep('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.11.0')
    annotations = dep('org.jetbrains', 'annotations', '13.0')
    compiler = [dep('org.jetbrains.kotlin', x, kotlin) for x in (
        'kotlin-compiler-embeddable', 'kotlin-build-tools-api', 'kotlin-script-runtime', 'kotlin-daemon-embeddable')]
    compiler += [dep('org.jetbrains.kotlin', 'kotlin-reflect', '1.6.10'), stdlib, coroutines, annotations]
    runtime = [stdlib, coroutines, annotations, dep('junit', 'junit', '4.13.2'), dep('org.hamcrest', 'hamcrest-core', '1.3')]
    sources = [ROOT / 'app/src/main/java/com/alananasss/kittytune/data/LikeBlacklistBatch.kt',
        ROOT / 'app/src/test/java/com/alananasss/kittytune/data/LikeBlacklistBatchTest.kt']
    sources += [ROOT / 'experiments/like-blacklist/Benchmark.kt']
    classes = output / 'classes'
    classes.mkdir(exist_ok=True)
    classpath = os.pathsep.join(map(str, runtime))
    print('Compiling actual batch exclusion updater', flush=True)
    subprocess.run([java, '-Xmx2g', '-cp', os.pathsep.join(map(str, compiler)),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '11',
        '-classpath', classpath, '-d', str(classes), *map(str, sources)], check=True)
    print('Starting like blacklist regression tests', flush=True)
    result = subprocess.run([java, '-Xmx512m', '-cp', str(classes) + os.pathsep + classpath,
        'org.junit.runner.JUnitCore', 'com.alananasss.kittytune.data.LikeBlacklistBatchTest'],
        text=True, capture_output=True, timeout=180)
    (output / 'junit.log').write_text(result.stdout + result.stderr, encoding='utf-8')
    print(result.stdout + result.stderr, flush=True)
    metadata = {'revision': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'exit_code': result.returncode,
        'sources_sha256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
        'scope': 'Real batch preference updater tests. In-memory component benchmark; no whole-app or flash-write timings.'}
    (output / 'result.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    result.check_returncode()
    for process in range(5):
        print(f'Starting fresh JVM comparison {process + 1}/5', flush=True)
        measured = subprocess.run([java, '-Xms256m', '-Xmx512m', '-cp', str(classes) + os.pathsep + classpath,
            'com.alananasss.kittytune.data.LikeBlacklistBenchmark', str(process)],
            text=True, capture_output=True, timeout=600)
        (output / f'process-{process}.csv').write_text(measured.stdout, encoding='utf-8')
        measured.check_returncode()
    (output / 'complete.json').write_text(json.dumps({'revision': metadata['revision'], 'processes': 5}), encoding='utf-8')


if __name__ == '__main__':
    main()
