#!/usr/bin/env python3
"""Independent bounded diagnostic reader. NONCANONICAL R2 bytes; no product integration.

STORED/DEFLATE use only the standard library. Optional Zstd loads a local reference
library; R1b must not recommend it until the same bytes pass real Windows parity.
"""
import argparse
import ctypes
import ctypes.util
import decimal
import hashlib
import json
import pathlib
import struct
import zlib

MIB = 1048576
CHUNK = 64 * MIB
RECORD = 65 * MIB
META = MIB
PAGE = 512 * 1024
MAX_LONG = (1 << 63) - 1
TYPES = {1: 'HEADER', 2: 'CHUNK', 3: 'INDEX_PAGE', 4: 'CHECKPOINT', 5: 'END'}


def demand(test, reason='invalid_r2'):
    if not test:
        raise ValueError(reason)


def uint(value):
    demand(isinstance(value, (str, int)) and not isinstance(value, bool), 'integer_type')
    if isinstance(value, str):
        demand(value.isascii() and value.isdecimal() and len(value) <= 19, 'decimal_integer')
    result = int(value)
    demand(0 <= result <= MAX_LONG, 'integer_range')
    return result


def add(a, b):
    a, b = uint(a), uint(b)
    demand(a <= MAX_LONG - b, 'integer_overflow')
    return a + b


def bounded_json(data):
    demand(len(data) <= META, 'json_bytes')
    def pairs(entries):
        result = {}
        for key, value in entries:
            demand(key not in result, 'duplicate_key')
            result[key] = value
        return result
    def reject_constant(value):
        raise ValueError('nonfinite_json')
    # Lexical depth is checked BEFORE CPython's recursive JSON parser.
    depth = 0; in_string = False; escaped = False
    for value in data:
        if in_string:
            if escaped: escaped = False
            elif value == 92: escaped = True
            elif value == 34: in_string = False
        elif value == 34: in_string = True
        elif value in (91, 123):
            depth += 1; demand(depth <= 32, 'json_depth')
        elif value in (93, 125):
            depth -= 1; demand(depth >= 0)
    result = json.loads(data.decode('utf-8', errors='strict'), object_pairs_hook=pairs,
                        parse_float=decimal.Decimal, parse_constant=reject_constant)
    items = 0
    def check(value):
        nonlocal items
        if isinstance(value, (list, dict)):
            items += len(value); demand(items <= 65536, 'json_items')
            for child in value.values() if isinstance(value, dict) else value: check(child)
        elif isinstance(value, str): demand(len(value.encode('utf-8', errors='strict')) <= 16384, 'json_string')
    check(result); demand(isinstance(result, dict), 'json_object')
    return result


def sha(data): return hashlib.sha256(data).hexdigest()


class Zstd:
    class Header(ctypes.Structure):
        _fields_ = [('content', ctypes.c_ulonglong), ('window', ctypes.c_ulonglong),
                    ('block', ctypes.c_uint), ('type', ctypes.c_int), ('header_size', ctypes.c_uint), ('dict', ctypes.c_uint),
                    ('checksum', ctypes.c_uint), ('reserved1', ctypes.c_uint), ('reserved2', ctypes.c_uint)]
    def __init__(self, library=None):
        name = library or ctypes.util.find_library('zstd')
        demand(name, 'zstd_library_unavailable')
        self.lib = ctypes.CDLL(name)
        for name, args in {
            'ZSTD_getFrameHeader': [ctypes.POINTER(self.Header), ctypes.c_void_p, ctypes.c_size_t],
            'ZSTD_findFrameCompressedSize': [ctypes.c_void_p, ctypes.c_size_t],
            'ZSTD_decompress': [ctypes.c_void_p, ctypes.c_size_t, ctypes.c_void_p, ctypes.c_size_t],
        }.items():
            function = getattr(self.lib, name); function.argtypes = args; function.restype = ctypes.c_size_t
        self.lib.ZSTD_versionString.restype = ctypes.c_char_p
        self.version = self.lib.ZSTD_versionString().decode()
    def decode(self, source, size):
        header = self.Header()
        demand(self.lib.ZSTD_getFrameHeader(ctypes.byref(header), source, len(source)) == 0, 'zstd_header')
        demand(header.type == 0 and header.dict == 0 and header.window <= 8 * MIB and header.content == size, 'zstd_window_dictionary_size')
        demand(self.lib.ZSTD_findFrameCompressedSize(source, len(source)) == len(source), 'zstd_trailing')
        output = ctypes.create_string_buffer(size)
        demand(self.lib.ZSTD_decompress(output, size, source, len(source)) == size, 'zstd_decode')
        return output.raw


def decode_block(source, expected, codec, zstd=None):
    demand(expected <= CHUNK and len(source) <= RECORD, 'block_bound')
    if codec == 0:
        demand(len(source) == expected, 'stored_length'); return source
    if codec == 1:
        inflater = zlib.decompressobj()
        output = inflater.decompress(source, expected + 1)
        demand(len(output) == expected and inflater.eof and not inflater.unused_data and not inflater.unconsumed_tail, 'deflate_exact_bounded')
        return output
    if codec == 2 and zstd is not None: return zstd.decode(source, expected)
    raise ValueError('unsupported_codec')


class Reader:
    def __init__(self, path, zstd=None):
        self.path = pathlib.Path(path); self.file = self.path.open('rb'); self.size = self.path.stat().st_size
        self.read_bytes = 0; self.zstd = zstd; self.root = None; self.suffix = []; self.complete = False
        self.header = self.metadata(self.record(0))
        demand(self.header['artifact'] == 'noncanonical-r2' and uint(self.header['revision']) == 1)
        demand(all(value == 'contiguous-native-view' for value in self.header['required_features']), 'unsupported_required_feature')
        demand(all(uint(value) in ([0, 1, 2] if zstd else [0, 1]) for value in self.header['codecs']), 'unsupported_codec')
        last = self.last()
        self.recovery = None
        if last['type'] == 5 and add(last['offset'], last['length']) == self.size:
            try:
                self.root = self.metadata(self.record(last['offset']))['root']
                if self.root: self.page(self.root, last['offset'])
                self.complete = True
            except (ValueError, KeyError, TypeError):
                self.root = None; self.recovery = 'damaged_final_index'; self.recover(last)
        else:
            self.recovery = 'missing_or_torn_end'; self.recover(last)

    def close(self): self.file.close()
    def __enter__(self): return self
    def __exit__(self, *args): self.close()
    def read(self, offset, count):
        offset, count = uint(offset), uint(count)
        demand(count <= RECORD and add(offset, count) <= self.size, 'read_bound')
        self.file.seek(offset); data = self.file.read(count); self.read_bytes += count
        demand(len(data) == count, 'short_read'); return data
    def record(self, offset, expected=None, verify=True):
        h = self.read(offset, 40)
        magic, version, kind, flags, ordinal, body, previous = struct.unpack('<8sHHIQQQ', h)
        demand(magic == b'R2RECORD' and version == 1 and kind in TYPES and flags == 0, 'record_header')
        uint(ordinal); uint(previous)
        length = add(body, 112)
        demand(length <= RECORD and add(offset, length) <= self.size, 'record_length')
        demand((offset == 0 and kind == 1 and ordinal == 0 and previous == MAX_LONG) or
               (offset > 0 and kind != 1 and ordinal > 0 and previous < offset), 'record_chain')
        f = self.read(add(offset, 40 + body), 72)
        cmagic, flen, ford, fprev, digest, crc, reserved = struct.unpack('<8sQQQ32sII', f)
        demand(cmagic == b'R2CMIT!!' and (flen, ford, fprev) == (length, ordinal, previous) and reserved == 0, 'commit_footer')
        demand(zlib.crc32(h + f[:64]) == crc, 'framing_crc')
        result = dict(offset=offset, length=length, ordinal=ordinal, type=kind, body=body, previous=previous, hash=sha(h + digest))
        if expected:
            demand(all(result[key] == (expected[key] if key == 'hash' else uint(expected[key])) for key in ('offset', 'length', 'ordinal', 'hash')), 'reference_integrity')
        if verify:
            check = hashlib.sha256(); at = offset + 40; left = body
            while left:
                n = min(left, 65536); check.update(self.read(at, n)); at += n; left -= n
            demand(check.digest() == digest, 'committed_payload_integrity')
        return result
    def metadata(self, record, limit=PAGE):
        demand(record['body'] <= limit, 'metadata_bound')
        return bounded_json(self.read(record['offset'] + 40, record['body']))
    def last(self):
        # Ordinary completion reads just a footer before any payload/index navigation.
        def candidate(offset):
            try: return self.record(offset, verify=False)
            except (ValueError, KeyError, struct.error): return None
        if self.size >= 72:
            last = self.read(self.size - 72, 72)
            if last[:8] == b'R2CMIT!!':
                length = struct.unpack_from('<Q', last, 8)[0]
                if 112 <= length <= min(RECORD, self.size):
                    result = candidate(self.size - length)
                    if result: return result
        lower = max(0, self.size - RECORD - 72); end = self.size
        while end > lower:
            start = max(lower, end - MIB); block = self.read(start, end - start); at = len(block)
            while True:
                at = block.rfind(b'R2CMIT!!', 0, at)
                if at < 0: break
                if at + 72 <= len(block):
                    length = struct.unpack_from('<Q', block, at + 8)[0]
                    offset = start + at + 72 - length
                    if 112 <= length <= RECORD and offset >= 0:
                        result = candidate(offset)
                        if result: return result
            if start == lower: break
            end = start + 71
        raise ValueError('no_verified_commit_within_tail_bound')
    def page(self, ref, owner):
        demand(add(ref['offset'], ref['length']) <= owner, 'index_forward_or_cycle')
        record = self.record(uint(ref['offset']), ref); demand(record['type'] == 3, 'index_type')
        page = self.metadata(record); depth = uint(page['depth']); children = page['children']
        demand(depth < 8 and 1 <= len(children) <= 256, 'index_bounds')
        demand(uint(children[0]['first']) == uint(ref['first']) and uint(children[-1]['last']) == uint(ref['last']), 'index_range')
        previous = -1
        for child in children:
            demand(uint(child['first']) > previous and uint(child['last']) >= uint(child['first']), 'index_order')
            previous = uint(child['last'])
            demand(add(child['offset'], child['length']) <= uint(ref['offset']) and uint(child['ordinal']) < uint(ref['ordinal']), 'index_backwards')
        return page
    def recover(self, last):
        current = last
        for _ in range(1024):
            if current['type'] == 2:
                decoded = self.chunk(current)
                current.update(first=decoded['entries'][0]['sequence'], last=decoded['entries'][-1]['gap_end'])
                self.suffix.append(current); demand(len(self.suffix) <= 64, 'recovery_suffix_bound')
            elif current['type'] == 4:
                try:
                    meta = self.metadata(self.record(current['offset'])); root = meta['root']
                    if root: self.page(root, current['offset'])
                    self.root = root; return
                except (ValueError, KeyError, TypeError): pass
            elif current['type'] == 1: return
            prior = self.record(current['previous'], verify=False)
            demand(prior['ordinal'] + 1 == current['ordinal'] and prior['offset'] + prior['length'] == current['offset'], 'recovery_chain')
            current = prior
        raise ValueError('recovery_record_bound')
    def refs(self, ref, owner, expected_depth=None):
        page = self.page(ref, owner); depth = uint(page['depth'])
        if expected_depth is not None: demand(depth == expected_depth, 'index_depth')
        for child in page['children']:
            if depth == 0:
                demand(self.record(uint(child['offset']), child, verify=False)['type'] == 2, 'index_leaf_type')
                yield child
            else: yield from self.refs(child, uint(ref['offset']), depth - 1)
    def chunks(self):
        if self.root:
            for ref in self.refs(self.root, self.size):
                decoded = self.chunk(self.record(uint(ref['offset']), ref))
                demand(uint(decoded['entries'][0]['sequence']) == uint(ref['first']) and uint(decoded['entries'][-1]['gap_end']) == uint(ref['last']), 'index_leaf_range')
                yield decoded
        for ref in sorted(self.suffix, key=lambda value: uint(value['first'])): yield self.chunk(self.record(uint(ref['offset'])))
    def seek(self, sequence):
        sequence = uint(sequence)
        for ref in self.suffix:
            if uint(ref['first']) <= sequence <= uint(ref['last']): return self.chunk(self.record(ref['offset']))
        ref, owner, expected_depth = self.root, self.size, None
        for _ in range(8):
            if not ref or not uint(ref['first']) <= sequence <= uint(ref['last']): return None
            page = self.page(ref, owner); depth = uint(page['depth'])
            if expected_depth is not None: demand(depth == expected_depth, 'index_depth')
            child = next((c for c in page['children'] if uint(c['first']) <= sequence <= uint(c['last'])), None)
            if child is None: return None
            if depth == 0:
                decoded = self.chunk(self.record(uint(child['offset']), child))
                demand(uint(decoded['entries'][0]['sequence']) == uint(child['first']) and uint(decoded['entries'][-1]['gap_end']) == uint(child['last']), 'index_leaf_range')
                return decoded
            ref, owner, expected_depth = child, uint(ref['offset']), depth - 1
        raise ValueError('index_depth')
    def chunk(self, record):
        demand(record['type'] == 2, 'chunk_type'); self.record(record['offset'])
        start = record['offset'] + 40
        n = struct.unpack('<I', self.read(start, 4))[0]
        demand(0 < n <= META and n + 4 <= record['body'], 'chunk_metadata')
        meta = bounded_json(self.read(start + 4, n))
        return self.decode_metadata(meta, record['body'] - 4 - n, lambda off, size: self.read(start + 4 + n + off, size), n)
    def decode_metadata(self, meta, stored_length, read, metadata_length=None):
        if metadata_length is None: metadata_length = len(json.dumps(meta, default=str).encode())
        demand(meta['profile'] in ('ANALYSIS', 'NATIVE', 'FULL'), 'profile')
        contexts, entries, descriptions = meta['contexts'], meta['entries'], meta['blocks']
        demand(1 <= len(contexts) <= 1024 and all(isinstance(c, dict) for c in contexts), 'context_bound')
        demand(1 <= len(entries) <= 1024 and len(descriptions) <= 4, 'chunk_entry_bound')
        decoded_sum = stored_sum = 0
        for b in descriptions:
            demand(b['role'] in ('temperature', 'validity', 'native', 'acquisition') and uint(b['offset']) == stored_sum, 'block_role_offset')
            stored_sum = add(stored_sum, b['stored']); decoded_sum = add(decoded_sum, b['decoded'])
            demand(stored_sum <= stored_length and decoded_sum + metadata_length <= CHUNK, 'chunk_aggregate')
            demand(uint(b['codec']) in ([0, 1, 2] if self.zstd else [0, 1]), 'unsupported_codec')
        demand(stored_sum == stored_length, 'block_closure')
        blocks = {}
        for b in descriptions:
            demand(b['role'] not in blocks, 'duplicate_block')
            raw = decode_block(read(uint(b['offset']), uint(b['stored'])), uint(b['decoded']), uint(b['codec']), self.zstd)
            demand(sha(raw) == b['hash'], 'block_integrity'); blocks[b['role']] = raw
        result = []; used = {}; previous = previous_time = -1
        for e in entries:
            sequence, end, time = uint(e['sequence']), uint(e['gap_end']), uint(e['relative_ns'])
            demand(sequence > previous and end >= sequence and time >= previous_time, 'entry_order'); previous, previous_time = end, time
            if e['receipt_ns'] is not None: uint(e['receipt_ns'])
            demand(uint(e['context']) < len(contexts), 'context_reference')
            w, h = uint(e['width']), uint(e['height']); demand(1 <= w <= 16384 and 1 <= h <= 16384 and w * h <= 4194304, 'dimensions')
            p = e['payloads']; demand(len(p) <= 4, 'descriptor_count'); resolved = {}
            for role, d in p.items():
                demand(role in ('temperature', 'validity', 'native', 'acquisition') and not set(d).intersection(('stride', 'transform', 'frame', 'chunk')), 'restricted_view')
                length, offset = uint(d['length']), uint(d['offset'])
                demand(length <= CHUNK, 'payload_bound')
                dtype = {'temperature': 'f32le', 'native': 'u16le'}.get(role, 'u8')
                expected_length = {'temperature': w*h*4, 'validity': w*h, 'native': w*h*2}.get(role, length)
                demand(d['dtype'] == dtype and length == expected_length and d['shape'] == ([length] if role == 'acquisition' else [h, w]), 'payload_shape_dtype')
                if d['kind'] == 'view':
                    demand(role == 'native' and meta['profile'] == 'FULL' and d.get('parent') == 'acquisition' and 'block' not in d and offset == 0, 'view_parent')
                    pd = p['acquisition']; demand(pd['kind'] == 'materialized' and pd['dtype'] == 'u8', 'view_materialized_parent')
                    parent = resolved.get('acquisition')
                    if parent is None:
                        block = blocks[pd['block']]; start, n = uint(pd['offset']), uint(pd['length'])
                        demand(add(start, n) <= len(block), 'parent_bounds'); parent = block[start:start+n]
                        demand(sha(parent) == pd['hash'], 'parent_integrity')
                else:
                    demand(d['kind'] == 'materialized' and 'parent' not in d and d['block'] == role, 'materialized_descriptor')
                    parent = blocks[role]; demand(offset == used.get(role, 0), 'payload_overlap'); used[role] = add(offset, length)
                demand(add(offset, length) <= len(parent), 'payload_view_bounds')
                raw = parent[offset:offset+length]; demand(sha(raw) == d['hash'], 'logical_integrity'); resolved[role] = raw
            temperature = resolved.get('temperature')
            if temperature is None: demand(not resolved and isinstance(e['reason'], str), 'explicit_gap')
            else:
                demand(sequence == end and e['reason'] is None, 'measurement_slot')
                mask = resolved.get('validity'); demand(mask is None or all(b in (0, 1) for b in mask), 'validity')
                for i, (bits,) in enumerate(struct.iter_unpack('<I', temperature)):
                    demand((bits & 0x7f800000) != 0x7f800000 if mask is None or mask[i] else bits == 0, 'temperature_validity')
                if meta['profile'] != 'ANALYSIS': demand('native' in resolved, 'native_required')
                if meta['profile'] == 'FULL': demand('acquisition' in resolved and p['native']['kind'] == 'view', 'full_dedup_required')
                if w == 384 and h == 288 and p.get('native', {}).get('encoding') == 'org.lmthermal.ht301.raw14':
                    demand(all(v < 16384 for (v,) in struct.iter_unpack('<H', resolved['native'])), 'native_raw14')
                    if meta['profile'] == 'FULL': demand(len(resolved['acquisition']) == 224256, 'ht_transport_length')
            result.append(resolved)
        demand(all(used.get(role, 0) == len(raw) for role, raw in blocks.items()), 'unreferenced_block_bytes')
        return dict(metadata=meta, entries=entries, payloads=result)


def file_sha(path):
    digest = hashlib.sha256()
    with pathlib.Path(path).open('rb') as source:
        for block in iter(lambda: source.read(65536), b''): digest.update(block)
    return digest.hexdigest()


def validate(path, zstd=None):
    source_hash = file_sha(path)
    with Reader(path, zstd) as reader:
        lazy_open = reader.read_bytes; frames = gaps = chunks = 0; logical = hashlib.sha256()
        for chunk in reader.chunks():
            chunks += 1
            for entry, payloads in zip(chunk['entries'], chunk['payloads']):
                if entry['reason'] is None: frames += 1
                else: gaps += 1
                for role in sorted(payloads): logical.update(role.encode()); logical.update(payloads[role])
        result = dict(complete=reader.complete, recovery=reader.recovery, frames=frames, gaps=gaps, chunks=chunks,
                      logical_sha256=logical.hexdigest(), source_sha256=source_hash,
                      lazy_open_bytes=lazy_open, validation_read_bytes=reader.read_bytes)
    demand(source_hash == file_sha(path), 'source_mutated')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('path'); parser.add_argument('--zstd-library'); parser.add_argument('--zstd', action='store_true')
    parser.add_argument('--seek', type=int)
    args = parser.parse_args(); codec = Zstd(args.zstd_library) if args.zstd or args.zstd_library else None
    if args.seek is None: print(json.dumps(validate(args.path, codec), indent=2))
    else:
        with Reader(args.path, codec) as reader:
            before = reader.read_bytes; chunk = reader.seek(args.seek)
            print(json.dumps(dict(found=chunk is not None, read_bytes=reader.read_bytes - before, complete=reader.complete), indent=2))

if __name__ == '__main__': main()
