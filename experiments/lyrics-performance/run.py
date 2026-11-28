"""Compile actual ranking/coordinator sources, run JUnit, then paired component measurements."""
import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import statistics
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
BASELINE = '8db1db4e'
PACKAGE = 'com/alananasss/kittytune'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--dependency-cache', type=Path)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--pairs', type=int, default=5)
    args = parser.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    java_home = args.java_home or Path(os.environ['JAVA_HOME'])
    java = str(java_home / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))
    dependencies = out / 'dependencies'
    dependencies.mkdir(exist_ok=True)

    def dependency(group, artifact, version):
        if args.dependency_cache:
            cached = args.dependency_cache / 'caches/modules-2/files-2.1' / group / artifact / version
            jars = sorted(cached.glob(f'*/{artifact}-{version}.jar'))
            if jars:
                return jars[0]
        destination = dependencies / f'{artifact}-{version}.jar'
        if not destination.exists():
            url = f'https://repo.maven.apache.org/maven2/{group.replace(".", "/")}/{artifact}/{version}/{destination.name}'
            print(f'Downloading {artifact} {version}', flush=True)
            with urllib.request.urlopen(url, timeout=90) as response:
                destination.write_bytes(response.read())
        return destination

    kotlin = '2.4.21'
    stdlib = dependency('org.jetbrains.kotlin', 'kotlin-stdlib', kotlin)
    coroutines = dependency('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.11.0')
    annotations = dependency('org.jetbrains', 'annotations', '13.0')
    compiler = [dependency('org.jetbrains.kotlin', name, kotlin) for name in (
        'kotlin-compiler-embeddable', 'kotlin-build-tools-api', 'kotlin-script-runtime', 'kotlin-daemon-embeddable')]
    compiler += [dependency('org.jetbrains.kotlin', 'kotlin-reflect', '1.6.10'), stdlib, coroutines, annotations]
    junit = dependency('junit', 'junit', '4.13.2')
    hamcrest = dependency('org.hamcrest', 'hamcrest-core', '1.3')
    runtime = [stdlib, coroutines, annotations, junit, hamcrest]
    generated = out / 'baseline'
    generated.mkdir(exist_ok=True)

    def baseline_file(path):
        return subprocess.check_output(['git', 'show', f'{BASELINE}:{path}'], cwd=ROOT).decode('utf-8')

    prefix = f'app/src/main/java/{PACKAGE}'
    matcher = baseline_file(f'{prefix}/data/LyricsMatcher.kt')
    (generated / 'BaselineLyricsMatcher.kt').write_text(matcher.replace('object LyricsMatcher', 'object BaselineLyricsMatcher'), encoding='utf-8')
    manual = baseline_file(f'{prefix}/ui/player/ManualLyricsSearch.kt')
    parts = [manual[manual.index('    fun merge('):manual.index('    fun cleanPreview(')],
        manual[manual.index('    private fun duplicateKey('):manual.index('    fun isInstrumental(')],
        manual[manual.index('    private fun rank('):manual.index('    /** What timings')]]
    reference = 'package com.alananasss.kittytune.ui.player\nimport com.alananasss.kittytune.data.BaselineLyricsMatcher\ninternal object BaselineLyricsRanking {\n'
    reference += '\n'.join(parts).replace('LyricsMatcher', 'BaselineLyricsMatcher') + '\n}\n'
    (generated / 'BaselineLyricsRanking.kt').write_text(reference, encoding='utf-8')
    src = ROOT / prefix
    sources = [src / name for name in ('data/LyricsMatcher.kt', 'data/lyrics/providers/LyricsProvider.kt',
        'ui/player/lyrics/LyricModels.kt', 'ui/player/ManualLyricsRanking.kt', 'ui/player/ProgressiveLyricsMerger.kt')]
    test_root = ROOT / f'app/src/test/java/{PACKAGE}/ui/player'
    sources += [test_root / 'LyricsRankingParityTest.kt', test_root / 'ProgressiveLyricsMergerTest.kt']
    sources += list(generated.glob('*.kt')) + [ROOT / 'experiments/lyrics-performance/Benchmark.kt']
    classes = out / 'classes'
    classes.mkdir(exist_ok=True)
    classpath = os.pathsep.join(map(str, runtime))
    print('Compiling production ranking and coroutine coordinator', flush=True)
    subprocess.run([java, '-Xmx2g', '-cp', os.pathsep.join(map(str, compiler)),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '11',
        '-classpath', classpath, '-d', str(classes), *map(str, sources)], cwd=ROOT, check=True)
    classpath = str(classes) + os.pathsep + classpath
    tests = ['com.alananasss.kittytune.ui.player.LyricsRankingParityTest',
        'com.alananasss.kittytune.ui.player.ProgressiveLyricsMergerTest']
    print('Starting JUnit parity and concurrency tests', flush=True)
    test_result = subprocess.run([java, '-Xmx512m', '-cp', classpath, 'org.junit.runner.JUnitCore', *tests],
        text=True, capture_output=True, timeout=180)
    (out / 'junit.log').write_text(test_result.stdout + test_result.stderr, encoding='utf-8')
    print(test_result.stdout + test_result.stderr, flush=True)
    test_result.check_returncode()
    rows = []
    for pair in range(args.pairs):
        print(f'Starting fresh JVM pair {pair + 1}/{args.pairs}', flush=True)
        result = subprocess.run([java, '-Xms256m', '-Xmx512m', '-cp', classpath,
            'com.alananasss.kittytune.ui.player.LyricsPerformanceBenchmark', str(pair)],
            text=True, capture_output=True, check=True, timeout=900)
        (out / f'process-{pair}.csv').write_text(result.stdout, encoding='utf-8')
        for row in csv.reader(result.stdout.splitlines()):
            rows.append(dict(zip(('process', 'size', 'sample', 'variant', 'nanos', 'retained', 'checksum'), row)))
    summary = {}
    for size in (40, 150, 400):
        summary[size] = {}
        for variant in ('baseline', 'candidate'):
            values = [int(r['nanos']) / 1e6 for r in rows if int(r['size']) == size and r['variant'] == variant]
            per_process = [statistics.median([int(r['nanos']) / 1e6 for r in rows
                if int(r['size']) == size and r['variant'] == variant and int(r['process']) == p]) for p in range(args.pairs)]
            summary[size][variant] = {'median_ms': statistics.median(values), 'process_medians_ms': per_process}
    revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    metadata = {'revision': revision, 'baseline': BASELINE, 'pairs': args.pairs,
        'java': subprocess.check_output([java, '-version'], stderr=subprocess.STDOUT, text=True),
        'sources_sha256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sources if p.is_relative_to(ROOT)}, 'component_results': summary,
        'limitations': 'Component JVM measurements, not Android device frame times or whole-app gains. ViewModel and Android app are not compiled by this harness.'}
    (out / 'summary.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    print(json.dumps(summary, indent=2), flush=True)
    (out / 'complete.txt').write_text(f'Completed {time.time()}\n', encoding='utf-8')


if __name__ == '__main__':
    main()
