"""Actual Kotlin DSP + official Media3 lifecycle sources, minimal platform shims on desktop JVM."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--dependency-cache', type=Path)
    parser.add_argument('--media3-sources', type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    java_home = args.java_home or Path(os.environ['JAVA_HOME'])
    java = str(java_home / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))
    javac = str(java_home / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac'))
    jars = output / 'dependencies'
    jars.mkdir(exist_ok=True)

    def dep(group, artifact, version):
        if args.dependency_cache:
            matches = sorted((args.dependency_cache / 'caches/modules-2/files-2.1' / group / artifact / version).glob(f'*/{artifact}-{version}.jar'))
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
    runtime = [stdlib, dep('junit', 'junit', '4.13.2'), dep('org.hamcrest', 'hamcrest-core', '1.3')]
    media3 = args.media3_sources or jars / 'media3-common-1.11.1-sources.jar'
    if not media3.exists():
        with urllib.request.urlopen('https://dl.google.com/dl/android/maven2/androidx/media3/media3-common/1.11.1/media3-common-1.11.1-sources.jar', timeout=90) as response:
            media3.write_bytes(response.read())
    shims = {
        'androidx/media3/common/C.java': 'package androidx.media3.common; public class C { public static final int ENCODING_PCM_16BIT=2, ENCODING_PCM_FLOAT=4, INDEX_UNSET=-1; @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE,java.lang.annotation.ElementType.PARAMETER}) public @interface PcmEncoding {} }',
        'androidx/media3/common/Format.java': 'package androidx.media3.common; public class Format { public static final int NO_VALUE=-1; public int sampleRate, channelCount, pcmEncoding; }',
        'androidx/media3/common/Timeline.java': 'package androidx.media3.common; public class Timeline { public static final Timeline EMPTY=new Timeline(); public boolean isEmpty(){return true;} public int getIndexOfPeriod(Object uid){return -1;} public static class Window {} }',
        'androidx/media3/common/util/Util.java': 'package androidx.media3.common.util; public class Util { public static boolean isEncodingLinearPcm(int e){return e==2 || e==4;} public static int getPcmFrameSize(int e,int c){return (e==2?2:4)*c;} }',
        'com/google/common/base/Preconditions.java': 'package com.google.common.base; public class Preconditions { public static void checkArgument(boolean x){if(!x) throw new IllegalArgumentException();} }',
        'android/util/Log.java': 'package android.util; public class Log {}',
    }
    for name in ['androidx.annotation.CallSuper', 'androidx.annotation.Nullable', 'androidx.media3.common.util.UnstableApi', 'com.google.errorprone.annotations.CanIgnoreReturnValue']:
        package, cls = name.rsplit('.', 1)
        shims[name.replace('.', '/') + '.java'] = f'package {package}; public @interface {cls} {{}}'
    generated = output / 'platform-shims'
    sources_java = []
    with zipfile.ZipFile(media3) as archive:
        for name in ['AudioProcessor', 'BaseAudioProcessor']:
            path = f'androidx/media3/common/audio/{name}.java'
            shims[path] = archive.read(path).decode('utf-8')
    for name, content in shims.items():
        target = generated / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding='utf-8')
        sources_java.append(target)
    classes = output / 'classes'
    classes.mkdir(exist_ok=True)
    subprocess.run([javac, '-d', str(classes), *map(str, sources_java)], check=True)
    routing = generated / 'Routing.kt'
    routing.write_text('''package com.alananasss.kittytune.audio
enum class AudioMode { STANDARD, BIT_PERFECT }
enum class AudioStage { PLAYER_FX }
object AudioRoutingMatrix {
    internal var modeProvider: () -> AudioMode = { AudioMode.STANDARD }
    fun isRouted(stage: AudioStage) = modeProvider() == AudioMode.STANDARD
}
''', encoding='utf-8')
    usb = generated / 'Usb.kt'
    usb.write_text('package com.alananasss.kittytune.audio.usb; object UsbAudioManager', encoding='utf-8')
    sources = [ROOT / f'app/src/main/java/com/alananasss/kittytune/ui/player/audio/{name}.kt' for name in ['AudioProcessors', 'PlayerEffectsAudioProcessor']]
    sources += [ROOT / 'app/src/test/java/com/alananasss/kittytune/ui/player/audio/PlayerEffectsAudioProcessorTest.kt', ROOT / 'experiments/audio-pipeline/Benchmark.kt']
    classpath = os.pathsep.join(map(str, [classes, *runtime]))
    print('Compiling actual DSP and Media3 1.11.1 lifecycle; platform shims only', flush=True)
    subprocess.run([java, '-Xmx2g', '-cp', os.pathsep.join(map(str, compiler)),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '11',
        '-classpath', classpath, '-d', str(classes), *map(str, sources), str(routing), str(usb)], check=True)
    print('Starting audio pipeline regressions', flush=True)
    result = subprocess.run([java, '-Xmx512m', '-cp', classpath, 'org.junit.runner.JUnitCore',
        'com.alananasss.kittytune.ui.player.audio.PlayerEffectsAudioProcessorTest'], text=True, capture_output=True, timeout=300)
    (output / 'junit.log').write_text(result.stdout + result.stderr, encoding='utf-8')
    print(result.stdout + result.stderr, flush=True)
    metadata = {'revision': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'exit_code': result.returncode, 'media3_sources_sha256': hashlib.sha256(media3.read_bytes()).hexdigest(),
        'sources_sha256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes().replace(b'\r\n', b'\n')).hexdigest() for p in sources},
        'scope': 'Actual DSP and official Media3 lifecycle sources on JVM with minimal platform shims; full Android module tests run separately. No device or whole-app gains measured.'}
    (output / 'result.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    result.check_returncode()
    for process in range(5):
        print(f'Starting fresh JVM comparison {process + 1}/5', flush=True)
        measured = subprocess.run([java, '-Xms256m', '-Xmx512m', '-cp', classpath,
            'com.alananasss.kittytune.ui.player.audio.AudioPipelineBenchmark', str(process)], text=True, capture_output=True, timeout=600)
        (output / f'process-{process}.csv').write_text(measured.stdout, encoding='utf-8')
        (output / f'process-{process}.log').write_text(measured.stderr, encoding='utf-8')
        measured.check_returncode()
    (output / 'complete.json').write_text(json.dumps({'revision': metadata['revision'], 'processes': 5}), encoding='utf-8')


if __name__ == '__main__':
    main()
