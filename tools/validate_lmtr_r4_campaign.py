#!/usr/bin/env python3
"""Collect one mode-qualified writer API35 emulator replay; never a physical-device route.

This executable is prepared locally, not permission to launch it. Every subprocess is
logged, and collection/final classification run independently of instrumentation success.
The recorder, workload, queues, guards and historical workflow are not modified.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import platform
import shutil
import subprocess
import sys
import tarfile
import time

ROOT = Path(__file__).resolve().parents[1]
BRANCH = 'refs/heads/codex/r4-throughput-campaign'
TEST = 'org.lmthermal.art.HtMemoryRuntimeTest#fullSizeFiniteRecorderReplayReportsCadenceAndBothCodecs'
TARGET = 'org.lmthermal.art'
RUNNER = 'org.lmthermal.art.test/androidx.test.runner.AndroidJUnitRunner'
# Host watchdogs bound the diagnostic job, not recorder admission or its existing Stop deadlines.
INSTRUMENTATION_TIMEOUT = 600
COLLECTION_TIMEOUT = 300
MAX_EXTRACTED_BYTES = 2 * 1024**3
MAX_ARCHIVE_MEMBERS = 10000


def digest(path):
    """Hash original archive/source bytes without loading recordings into memory."""
    value = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()


def save(path, value):
    """Evidence files are new-only; never repair/replace an earlier receipt."""
    with path.open('x', encoding='utf-8') as output:
        json.dump(value, output, indent=2, ensure_ascii=False)
        output.write('\n')


class Capture:
    """One private result owner, with explicit exit codes independent of test verdicts."""
    def __init__(self, directory):
        directory.mkdir(mode=0o700, exist_ok=False)
        self.directory = directory
        self.commands = []
        self.errors = []
        self.emulator = None
        self.qualified = False
        self.installed = False
        self.instrumentation = None
        self.archive_ready = False
        self.freeze = None

    def run(self, name, argv, timeout=60, binary=False):
        """Do not fail-fast on nonzero/timeout: retain the raw output and command ledger."""
        path = self.directory / name
        row = {'name': name, 'argv': list(map(str, argv)), 'timeout_seconds': timeout,
               'started_monotonic_ns': time.monotonic_ns(), 'timed_out': False, 'exit_code': None}
        started = time.monotonic_ns()
        with path.open('xb') as output:
            # Binary tar output must not be contaminated with adb stderr.
            with (self.directory / (name + '.stderr')).open('xb') as errors:
                try:
                    result = subprocess.run(argv, cwd=ROOT, stdout=output,
                                            stderr=errors if binary else subprocess.STDOUT, timeout=timeout)
                    row['exit_code'] = result.returncode
                except subprocess.TimeoutExpired:
                    row['timed_out'] = True
                    errors.write(b'Host watchdog expired; remote process completion is not assumed.\n')
                except OSError as error:
                    errors.write((type(error).__name__ + ': ' + str(error) + '\n').encode())
        row['elapsed_ns'] = time.monotonic_ns() - started
        self.commands.append(row)
        with (self.directory / 'commands.jsonl').open('a') as ledger:
            ledger.write(json.dumps(row) + '\n')
        return row

    def require(self, name, argv, timeout=60):
        """A preparation failure stops execution, but the outer finalizer still collects."""
        row = self.run(name, argv, timeout)
        if row['exit_code'] != 0 or row['timed_out']:
            raise RuntimeError('preparation_failed:' + name)
        return (self.directory / name).read_text().strip()

    def adb(self, *args):
        """All operations after emulator discovery are explicitly serial-qualified."""
        if not self.emulator:
            raise RuntimeError('no_emulator_serial')
        return ['adb', '-s', self.emulator, *args]

    def collect(self):
        """Keep artifacts even on assertion failure, crash, missing JSON or host timeout."""
        if not self.qualified:
            return  # Never fall back to a default/physical ADB target.
        initial_log = self.run('logcat-before-freeze.txt', self.adb('logcat', '-d'), 60)
        if initial_log['exit_code'] != 0 or initial_log['timed_out']:
            self.errors.append('pre_freeze_logcat_unavailable')
        if self.installed and self.instrumentation is not None:
            # Freeze only the isolated test package AFTER measurement. On timeout this is a
            # forced process cutoff, never a successful recorder Stop or fabricated END.
            self.freeze = self.run('post-test-freeze.txt', self.adb('shell', 'am', 'force-stop', TARGET), 30)
            if self.freeze['exit_code'] != 0 or self.freeze['timed_out']:
                self.errors.append('test_package_freeze_failed_archive_may_be_nonatomic')
            if shutil.disk_usage(self.directory).free < 1024**3:
                self.errors.append('collector_disk_headroom_below_1GiB')
            else:
                row = self.run('synthetic.tar', self.adb('exec-out', 'run-as', TARGET,
                               'tar', 'cf', '-', 'files/r4'), COLLECTION_TIMEOUT, binary=True)
                if row['exit_code'] == 0 and not row['timed_out']:
                    try:
                        extract(self.directory / 'synthetic.tar', self.directory / 'extracted')
                        self.archive_ready = True
                    except Exception as error:
                        self.errors.append('archive_extract:' + type(error).__name__ + ':' + str(error))
                else:
                    self.errors.append('archive_collection_failed_or_timed_out')
        row = self.run('logcat.txt', self.adb('logcat', '-d'), 60)
        if row['exit_code'] != 0 or row['timed_out']:
            self.errors.append('final_logcat_unavailable')


def extract(archive, destination):
    """Extract only bounded regular r4 files/directories; reject links, traversal and duplicates.

    The unmodified tar is retained even when extraction refuses. Host extraction limits
    are not recorder queue/file limits and refusal is an explicit inconclusive collection.
    """
    destination.mkdir(mode=0o700, exist_ok=False)
    seen = set()
    total = 0
    with tarfile.open(archive, 'r:') as source:
        for number, member in enumerate(source, 1):
            path = PurePosixPath(member.name)
            if (number > MAX_ARCHIVE_MEMBERS or path.is_absolute() or '..' in path.parts
                    or path.parts[:2] != ('files', 'r4') or path in seen
                    or not (member.isfile() or member.isdir())):
                raise ValueError('unsafe_or_duplicate_archive_member')
            seen.add(path)
            target = destination.joinpath(*path.parts)
            if member.isdir():
                target.mkdir(mode=0o700, parents=True, exist_ok=True)
                continue
            total += member.size
            if total > MAX_EXTRACTED_BYTES:
                raise ValueError('collector_extraction_byte_limit')
            target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
            with source.extractfile(member) as data, target.open('xb') as output:
                shutil.copyfileobj(data, output, length=65536)


def prepare(capture, metrics_mode):
    """Require a clean exact CI checkout and reviewed source bytes before any emulator operation."""
    context = {key: os.environ.get(key) for key in ('GITHUB_ACTIONS', 'GITHUB_REPOSITORY', 'GITHUB_REF',
               'GITHUB_SHA', 'GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT', 'GITHUB_JOB')}
    context.update(test=TEST, python=sys.version, platform=platform.platform(),
                   writer_metrics_mode=metrics_mode, targeted_compile_profile="lmtrWriterTimingOnly=true")
    save(capture.directory / 'context.json', context)
    if (context['GITHUB_ACTIONS'] != 'true' or context['GITHUB_REPOSITORY'] != 'LounaMaili/LMThermal'
            or context['GITHUB_REF'] != BRANCH):
        raise RuntimeError('requires_authorized_diagnostic_branch_on_isolated_GitHub_runner')
    head = capture.require('source-sha.txt', ['git', '--no-optional-locks', 'rev-parse', 'HEAD'])
    if head != context['GITHUB_SHA']:
        raise RuntimeError('checkout_sha_mismatch')
    if capture.require('checkout-status.txt', ['git', '--no-optional-locks', 'status', '--porcelain']):
        raise RuntimeError('dirty_checkout')
    capture.require('reviewed-source-check.txt', ['sha256sum', '--check',
                    'docs/lmtr-r4/campaign-reviewed-sources.sha256'])
    paths = capture.require('tracked-paths.txt', ['git', '--no-optional-locks', 'ls-files']).splitlines()
    with (capture.directory / 'source-files.sha256').open('x') as output:
        for name in paths:
            path = ROOT / name
            if path.is_symlink() or not path.is_file():
                raise RuntimeError('unexpected_nonregular_tracked_source')
            output.write(digest(path) + '  ' + name + '\n')
    capture.require('java-version.txt', ['java', '-version'])
    serial = capture.require('emulator-serial.txt', ['adb', '-e', 'get-serialno'])
    if not serial.startswith('emulator-') or not serial.removeprefix('emulator-').isdigit():
        raise RuntimeError('not_an_emulator_serial')
    capture.emulator = serial
    values = {}
    for label, key in [('qemu', 'ro.kernel.qemu'), ('sdk', 'ro.build.version.sdk'),
                       ('abi', 'ro.product.cpu.abi'), ('fingerprint', 'ro.build.fingerprint'),
                       ('model', 'ro.product.model'), ('release', 'ro.build.version.release')]:
        values[label] = capture.require('device-' + label + '.txt', capture.adb('shell', 'getprop', key))
    save(capture.directory / 'environment.json', values)
    if values['qemu'] != '1' or values['sdk'] != '35' or values['abi'] != 'x86_64' or not values['fingerprint']:
        raise RuntimeError('unexpected_emulator_environment')
    capture.qualified = True
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if sdk:
        package = Path(sdk) / 'system-images/android-35/google_apis/x86_64/package.xml'
        if package.is_file():
            with package.open('rb') as source, (capture.directory / 'system-image-package.xml').open('xb') as output:
                shutil.copyfileobj(source, output)
        else:
            raise RuntimeError('system_image_package_revision_unavailable')
    else:
        raise RuntimeError('system_image_sdk_path_unknown')
    capture.require('gradle.txt', ['./gradlew', '-p', 'tools/lmtr-r4-art', ':lmtr-art:assembleDebug',
                    ':lmtr-art:assembleDebugAndroidTest', '-PlmtrWriterTimingOnly=true', '--console=plain'], 1200)
    apks = [ROOT / 'lmtr-art/build/outputs/apk/debug/lmtr-art-debug.apk',
            ROOT / 'lmtr-art/build/outputs/apk/androidTest/debug/lmtr-art-debug-androidTest.apk']
    save(capture.directory / 'apk-sha256.json', {str(p.relative_to(ROOT)): digest(p) for p in apks})
    capture.require('install-target.txt', capture.adb('install', '-r', str(apks[0])), 120)
    capture.installed = True
    capture.require('install-tests.txt', capture.adb('install', '-r', str(apks[1])), 120)
    # Each arm starts with empty private test data and a new target process. The
    # preceding arm has already been archived by its collector; no host evidence is removed.
    cleared = capture.require('clear-disposable-target.txt', capture.adb('shell', 'pm', 'clear', TARGET), 60)
    if cleared != 'Success':
        raise RuntimeError('target_data_reset_not_confirmed')
    capture.instrumentation = capture.run('instrumentation.txt', capture.adb('shell', 'am', 'instrument',
                    '-w', '-r', '-e', 'class', TEST, '-e', 'lmtrWriterMetrics', metrics_mode, RUNNER), INSTRUMENTATION_TIMEOUT)


def main():
    """Always seal available evidence; never turn a failed/unknown replay into a green test."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path, help='New result directory beneath a unique workflow result root')
    parser.add_argument('--metrics-mode', choices=('on', 'off'), required=True, help='Fine metrics only; common coarse metrics remain enabled')
    args = parser.parse_args()
    os.umask(0o077)
    capture = Capture(args.directory.resolve())
    try:
        prepare(capture, args.metrics_mode)
    except Exception as error:
        capture.errors.append('preparation:' + type(error).__name__ + ':' + str(error))
    finally:
        try:
            capture.collect()
        except Exception as error:
            capture.errors.append('collection:' + type(error).__name__ + ':' + str(error))
        save(capture.directory / 'collection.json', {'errors': capture.errors, 'test': TEST,
             'qualified_emulator': capture.qualified, 'instrumentation': capture.instrumentation,
             'writer_metrics_mode': args.metrics_mode,
             'post_test_freeze': capture.freeze, 'archive_ready': capture.archive_ready,
             'archive_sha256': digest(capture.directory / 'synthetic.tar') if (capture.directory / 'synthetic.tar').exists() else None,
             'atomic_snapshot': False, 'timeout_is_forced_cutoff_not_graceful_stop': bool(
                 capture.instrumentation and capture.instrumentation['timed_out'])})
        # Analyzer is invoked even if preparation, instrumentation or extraction failed.
        driver = capture.run('analysis-driver.txt', [sys.executable, str(ROOT / 'tools/analyze_lmtr_r4_api35.py'),
                    str(capture.directory), str(capture.directory / 'analysis')], 600)
        report_path = capture.directory / 'analysis/assessment.json'
        final = {'outcome': 'C', 'analysis_process': driver,
                 'instrumentation_process': capture.instrumentation,
                 'reason': 'analysis_missing_failed_or_timed_out'}
        if report_path.is_file() and not driver['timed_out']:
            try:
                report = json.loads(report_path.read_text())
                if driver['exit_code'] == {'A': 0, 'B': 10, 'C': 20}[report['outcome']]:
                    final.update(outcome=report['outcome'], reason='see_analysis_assessment')
            except (OSError, ValueError, KeyError):
                pass  # Raw analyzer output and exit remain evidence, never a synthesized success.
        metrics = capture.run('writer-metrics-driver.txt', [sys.executable,
                    str(ROOT / 'tools/check_lmtr_r4_writer_reports.py'), str(capture.directory),
                    str(capture.directory / 'writer-metrics-assessment.json'), '--mode', args.metrics_mode], 60)
        final['writer_metrics_process'] = metrics
        final['job_exit_code'] = ({'A': 0, 'B': 10, 'C': 20}[final['outcome']]
                                  if metrics['exit_code'] == 0 and not metrics['timed_out'] else 20)
        save(capture.directory / 'execution-verdict.json', final)
        manifest = capture.directory / 'SHA256SUMS'
        with manifest.open('x') as output:
            for path in sorted(capture.directory.rglob('*')):
                if path.is_file() and path != manifest:
                    output.write(digest(path) + '  ' + str(path.relative_to(capture.directory)) + '\n')
    print(json.dumps(final, indent=2))
    return final['job_exit_code']


if __name__ == '__main__':
    raise SystemExit(main())
