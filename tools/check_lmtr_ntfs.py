#!/usr/bin/env python3
"""Optional native NTFS >4 GiB adapter, never WSL or a claimed Windows result on Linux.

Sets the sparse flag on its own temporary file before construction. Real logical
zero payloads are hash-bound in contiguous records; no guessed framing holes.
"""
import argparse
import ctypes
from ctypes import wintypes
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.lmtr_v1 import Reader
from check_lmtr_sparse import construct, CHUNKS


def filesystem(path):
    """Require a real local NTFS volume before creating a multi-gigabyte logical file."""
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.GetVolumePathNameW.argtypes=[wintypes.LPCWSTR,wintypes.LPWSTR,wintypes.DWORD]
    kernel.GetVolumePathNameW.restype=wintypes.BOOL
    root=ctypes.create_unicode_buffer(32768)
    if not kernel.GetVolumePathNameW(str(path.resolve()),root,len(root)):raise ctypes.WinError(ctypes.get_last_error())
    name=ctypes.create_unicode_buffer(64)
    kernel.GetVolumeInformationW.argtypes=[wintypes.LPCWSTR,wintypes.LPWSTR,wintypes.DWORD,ctypes.POINTER(wintypes.DWORD),ctypes.POINTER(wintypes.DWORD),ctypes.POINTER(wintypes.DWORD),wintypes.LPWSTR,wintypes.DWORD]
    kernel.GetVolumeInformationW.restype=wintypes.BOOL
    if not kernel.GetVolumeInformationW(root.value,None,0,None,None,None,name,len(name)):raise ctypes.WinError(ctypes.get_last_error())
    if name.value!='NTFS' or str(path.resolve()).startswith('\\\\'):raise RuntimeError('local native NTFS required')
    return name.value


def mark_sparse(stream):
    """Set FSCTL_SET_SPARSE on the exclusively owned file; root/admin is not required."""
    import msvcrt
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.DeviceIoControl.argtypes=[wintypes.HANDLE,wintypes.DWORD,wintypes.LPVOID,wintypes.DWORD,wintypes.LPVOID,wintypes.DWORD,ctypes.POINTER(wintypes.DWORD),wintypes.LPVOID]
    kernel.DeviceIoControl.restype=wintypes.BOOL
    returned=wintypes.DWORD()
    if not kernel.DeviceIoControl(msvcrt.get_osfhandle(stream.fileno()),0x900c4,None,0,None,0,ctypes.byref(returned),None):raise ctypes.WinError(ctypes.get_last_error())


def allocated_bytes(path):
    """Get physical allocation independently from the >4 GiB logical length."""
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.GetCompressedFileSizeW.argtypes=[wintypes.LPCWSTR,ctypes.POINTER(wintypes.DWORD)]
    kernel.GetCompressedFileSizeW.restype=wintypes.DWORD
    high=wintypes.DWORD();ctypes.set_last_error(0)
    low=kernel.GetCompressedFileSizeW(str(path),ctypes.byref(high))
    if low==0xffffffff and ctypes.get_last_error():raise ctypes.WinError(ctypes.get_last_error())
    return (high.value<<32)|low


def file_hash(path):
    """Hash a logical sparse source in bounded pages, without materializing holes."""
    digest=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1048576),b''):digest.update(block)
    return digest.hexdigest()


def main():
    """Independent selected/full parity with bounded physical allocation and temp cleanup."""
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java',required=True);parser.add_argument('--classpath',required=True);parser.add_argument('--report',type=Path,required=True);parser.add_argument('--temp-root',type=Path);parser.add_argument('--full',action='store_true');args=parser.parse_args()
    if os.name!='nt':raise SystemExit('NOT EXECUTED: requires native Windows, not Linux/WSL')
    with tempfile.TemporaryDirectory(prefix='lmtr-ntfs-',dir=args.temp_root) as directory:
        path=Path(directory)/'over-4gib.lmtr';volume=filesystem(Path(directory))
        expected=construct(path,sparse_prepare=mark_sparse)
        before=path.stat();allocation=allocated_bytes(path)
        original_hash=file_hash(path)
        assert before.st_size>2**32 and allocation<16*1048576,(before.st_size,allocation)
        with Reader(path) as reader:python=reader.check(entry=CHUNKS)
        assert python['summary']==expected['summary'] and python['selected_chunks']==1
        assert python['logical_hashes']=={str(CHUNKS)+':acquisition':expected['target_logical_hash']}
        command=[args.java,'-Xmx256m','-cp',args.classpath,'org.lmthermal.lmtr.LmtrCommand','check',str(path),'--entry',str(CHUNKS)]
        process=subprocess.run(command,capture_output=True,text=True,timeout=180)
        assert process.returncode==0,process.stdout+process.stderr
        kotlin=json.loads(process.stdout)
        assert kotlin['summary']==python['summary'] and kotlin['logical_hashes']==python['logical_hashes']
        assert int(kotlin['chunks_decoded'])==1 and int(kotlin['records_read'])<=16
        full='not_requested'
        if args.full:
            with Reader(path) as reader:checked=reader.check(full=True)
            assert checked['summary']==expected['summary'];full='python_full_71_chunks_passed'
        after=path.stat();assert before.st_size==after.st_size and before.st_mtime_ns==after.st_mtime_ns and allocation==allocated_bytes(path)
        # Optional streaming SHA adds no large allocation; it does read the logical sparse extent.
        assert original_hash==file_hash(path),'source bytes changed'
        report=dict(platform=platform.platform(),filesystem=volume,bytes=before.st_size,allocated_bytes=allocation,
                    target_offset=expected['target_chunk_offset'],python=python,kotlin=kotlin,full=full,source_sha256=original_hash,
                    source_immutability='exact SHA/size/mtime/allocation unchanged; read-only readers',
                    limitations=['optional Python full scan is separate from selected scope','no physical write-throughput/power-loss guarantee','no universal memory ceiling'])
        args.report.write_text(json.dumps(report,sort_keys=True,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(status='passed',filesystem=volume,bytes=before.st_size,allocated_bytes=allocation)))

if __name__=='__main__':main()
