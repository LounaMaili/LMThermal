#!/usr/bin/env python3
"""Pinned BSD-3-Clause reference Zstd, confined to the Android test APK."""
import hashlib
import pathlib
import subprocess
import tarfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
ARCHIVE = ROOT / '.local-tools/recording-r2/zstd-1.5.7.tar.gz'
EXPECTED = 'eb33e51f49a15e023950cd7825ca74a4a2b43db8354825ac24fc1b7ee09e6fa3'
assert hashlib.sha256(ARCHIVE.read_bytes()).hexdigest() == EXPECTED
SOURCE = ARCHIVE.parent / 'zstd-1.5.7'
if not SOURCE.exists():
    with tarfile.open(ARCHIVE) as archive:
        archive.extractall(ARCHIVE.parent, filter='data')
NDK = ROOT / '.local-tools/sdk/ndk/28.0.13004108/toolchains/llvm/prebuilt/linux-x86_64/bin'
OUTPUT = ROOT / 'app/build/generated/r2-jni/arm64-v8a/libr2_zstd.so'
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
BUILD = ARCHIVE.parent / 'objects-arm64'
BUILD.mkdir(exist_ok=True)
objects = []
for directory in ('common', 'compress', 'decompress'):
    for source in sorted((SOURCE / 'lib' / directory).glob('*.c')):
        obj = BUILD / (source.stem + '.o')
        subprocess.run([str(NDK / 'aarch64-linux-android26-clang'), '-c', '-Os', '-fPIC',
                        '-DZSTD_MULTITHREAD=0', '-I' + str(SOURCE / 'lib'), str(source), '-o', str(obj)], check=True)
        objects.append(str(obj))
subprocess.run([str(NDK / 'aarch64-linux-android26-clang++'), '-std=c++17', '-Os', '-fPIC', '-shared',
                '-I' + str(SOURCE / 'lib'), str(ROOT / 'tools/recording_r2/zstd_test_jni.cpp'),
                *objects, '-o', str(OUTPUT)], check=True)
subprocess.run([str(NDK / 'llvm-strip'), '--strip-unneeded', str(OUTPUT)], check=True)
print({'version': '1.5.7', 'source_sha256': EXPECTED, 'abi': 'arm64-v8a',
       'library_bytes': OUTPUT.stat().st_size, 'library_sha256': hashlib.sha256(OUTPUT.read_bytes()).hexdigest()})
