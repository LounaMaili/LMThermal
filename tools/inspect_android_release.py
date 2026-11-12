#!/usr/bin/env python3
"""Host-only release ELF/ZIP audit; not a device test, signature verifier or Play certification."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

PAGE_SIZE = 16384


def load_alignments(data):
    """Read little-endian ELF64 program headers, rejecting missing/unaligned load segments."""
    if len(data) < 64 or data[:6] != b'\x7fELF\x02\x01':
        raise ValueError('expected little-endian ELF64')
    offset = struct.unpack_from('<Q', data, 32)[0]
    entry_size, count = struct.unpack_from('<HH', data, 54)
    if entry_size < 56 or not count or offset + entry_size * count > len(data):
        raise ValueError('invalid ELF program table')
    alignments = []
    for index in range(count):
        header = offset + index * entry_size
        if struct.unpack_from('<I', data, header)[0] != 1:
            continue
        file_offset, virtual = struct.unpack_from('<QQ', data, header + 8)
        alignment = struct.unpack_from('<Q', data, header + 48)[0]
        if alignment < PAGE_SIZE or alignment & (alignment - 1) or file_offset % alignment != virtual % alignment:
            raise ValueError('ELF LOAD segment lacks compatible 16 KiB alignment')
        alignments.append(alignment)
    if not alignments:
        raise ValueError('ELF has no LOAD segment')
    return alignments


def inspect(path):
    """Check the actual release artifacts; AAB package alignment still needs bundletool/generated APKs."""
    libraries = []
    with path.open('rb') as raw, zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            if not info.filename.endswith('.so'):
                continue
            if '/arm64-v8a/' not in info.filename:
                raise ValueError('unexpected ABI: '+info.filename)
            data = archive.read(info)
            alignments = load_alignments(data)
            item = dict(member=info.filename, bytes=len(data), sha256=hashlib.sha256(data).hexdigest(),
                        load_alignment=alignments)
            if path.suffix == '.apk' and info.compress_type == zipfile.ZIP_STORED:
                raw.seek(info.header_offset)
                header = raw.read(30)
                name_length, extra_length = struct.unpack_from('<HH', header, 26)
                offset = info.header_offset + 30 + name_length + extra_length
                if offset % PAGE_SIZE:
                    raise ValueError('uncompressed APK library lacks 16 KiB ZIP alignment')
                item['zip_data_alignment_16k'] = True
            libraries.append(item)
    if not libraries:
        raise ValueError('no native libraries found')
    return dict(status='passed', artifact=path.name, bytes=path.stat().st_size,
                sha256=hashlib.sha256(path.read_bytes()).hexdigest(), libraries=libraries,
                limitation='Static ELF/ZIP inspection only; no ART/device/Play delivery validation')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifact', type=Path)
    print(json.dumps(inspect(parser.parse_args().artifact), indent=2))
