"""Layer-isolated mutations and combined recovery attacks; originals remain pinned.

These bounded temporary sources are not additional canonical corpus members.
Mutations deliberately reseal framing/references when testing a later semantic layer.
"""
from copy import deepcopy
import hashlib
from pathlib import Path
import struct
import tempfile
import unittest

from tools.lmtr_v1 import Reader, ContractError, dumps, loads, write_plan
from tools.lmtr_v1.container import HEADER, FOOTER, encode_record

ROOT = Path(__file__).resolve().parents[3]
CORPUS = ROOT / 'lmtr-contract/src/test/resources/lmtr'


def records(raw):
    """Split only the already-verified small test source, never hostile reader input."""
    cursor = 0
    result = []
    while cursor < len(raw):
        fields = HEADER.unpack_from(raw, cursor)
        size = fields[5] + 112
        result.append(dict(offset=cursor, length=size, kind=fields[2], ordinal=fields[4],
                           body=raw[cursor+40:cursor+size-72]))
        cursor += size
    return result


def rebuild(raw, edit=lambda kind, ordinal, value: value):
    """Reseal envelopes and backward references after an isolated JSON mutation.

References are identified by their complete Ref field set; optional JSON remains
opaque to production code. This test mutator operates only on owned synthetic data.
"""
    output = bytearray(); replacements = {}; previous = 2**63-1
    def refs(value):
        if isinstance(value, dict):
            if set(('offset', 'length', 'ordinal', 'hash', 'first', 'last')) <= value.keys():
                return deepcopy(replacements[int(value['ordinal'])])
            return {key: refs(item) for key, item in value.items()}
        if isinstance(value, list): return [refs(item) for item in value]
        return value
    for record in records(raw):
        body = record['body']; kind = record['kind']; ordinal = record['ordinal']
        if kind == 2:
            length = struct.unpack_from('<I', body)[0]
            value = edit(kind, ordinal, refs(loads(body[4:4+length])))
            encoded = dumps(value); body = struct.pack('<I', len(encoded)) + encoded + body[4+length:]
            first, last = value['entries'][0]['entry_id'], value['entries'][-1]['entry_id']
        else:
            value = edit(kind, ordinal, refs(loads(body))); body = dumps(value)
            if kind == 3: first, last = value['first'], value['last']
            elif kind == 4: first, last = value['summary']['first_entry'], value['summary']['last_entry']
            else: first = last = None
        encoded = encode_record(kind, body, ordinal, previous)
        h = encoded[:40]; digest = encoded[-40:-8]
        offset = len(output)
        replacements[ordinal] = dict(offset=str(offset), length=str(len(encoded)), ordinal=str(ordinal),
                                     hash=hashlib.sha256(h+digest).hexdigest(), first=first, last=last)
        output.extend(encoded); previous = offset
    return bytes(output)


def combined_cases():
    """Return independently specified expected outcomes, not two-reader majority votes."""
    base = (CORPUS/'files/illustration-01-stored.lmtr').read_bytes()
    recs = records(base); chunk = next(r for r in recs if r['kind'] == 2)
    prefix = base[:recs[-1]['offset']]
    cases = []
    def add(name, raw, expected, recover=True, entries=None):
        cases.append(dict(name=name, raw=bytes(raw), expected=expected, recover=recover, entries=entries))
    fake = b'LMTRCMT1' + struct.pack('<QQQ', 112, 999, 0) + bytes(40)
    torn = bytearray(base); torn[-8] ^= 1
    add('damaged-end-footer-false-markers', torn + fake*3, 'recovered', entries='3')
    damaged = bytearray(base[:chunk['offset']+chunk['length']]); damaged[-8] ^= 1
    add('uncommitted-chunk-false-markers', damaged + fake, 'recovered', entries='0')
    for tail in [b'LMTRREC1', b'not-a-record', bytes(112)]:
        add('valid-prefix-incomplete-tail-'+str(len(tail)), prefix+tail, 'recovered', entries='3')
    add('missing-end-no-tail', prefix, 'recovered', entries='3')
    # Gap/event evidence stays committed; unknown tail is still not a guessed count/end.
    gap = (CORPUS/'files/illustration-05-stored.lmtr').read_bytes()
    add('known-gap-missing-end', gap[:records(gap)[-1]['offset']], 'recovered')
    for types in [(3,), (4,), (3,4,5)]:
        data = bytearray(base)
        for record in recs:
            if record['kind'] in types+(2,): data[record['offset']+40] ^= 1
        add('source-corruption-with-nav-'+''.join(map(str,types)), data, 'committed_payload_integrity')
    def corrupt_metadata(kind, ordinal, value):
        if kind == 2: value['entries'][0]['payloads'] = []
        return value
    invalid = bytearray(rebuild(base, corrupt_metadata))
    for record in records(invalid):
        if record['kind'] in (3,4): invalid[record['offset']+40] ^= 1
    add('committed-invalid-union-with-navigation-damage', invalid, 'invalid_metadata')
    def feature(kind, ordinal, value):
        if kind in (1,2): value['required_features'] = sorted(value['required_features']+['org.example.unsupported-v1'])
        return value
    add('unsupported-header-during-recovery', rebuild(prefix,feature), 'unsupported_required_feature')
    def chunk_feature(kind, ordinal, value):
        if kind == 2: value['required_features'] = sorted(value['required_features']+['org.example.unsupported-v1'])
        return value
    add('unsupported-chunk-during-recovery', rebuild(prefix,chunk_feature), 'unsupported_required_feature')
    def wrong_length(kind, ordinal, value):
        if kind == 2: value['blocks'][0]['decoded'] = str(int(value['blocks'][0]['decoded'])+4)
        return value
    add('wrong-stored-decoded-length-valid-outer-hash', rebuild(prefix,wrong_length), 'invalid_payload')
    def offset_overflow(kind, ordinal, value):
        if kind == 3: value['children'][0]['record']['offset'] = str(2**63-1)
        return value
    add('reference-offset-plus-length-overflow', rebuild(base,offset_overflow), 'resource_limit',False)
    def bad_hash(kind, ordinal, value):
        if kind == 3: value['children'][0]['record']['hash'] = '0'*64
        return value
    add('conflicting-chunk-hash-reference', rebuild(base,bad_hash), 'invalid_container',False)
    def duplicated(kind, ordinal, value):
        if kind == 3:
            value['children'].append(deepcopy(value['children'][0]))
            value['chunks'] = '2';value['entries'] = '6'
        return value
    add('duplicate-reachable-chunk-reference', rebuild(base,duplicated), 'invalid_container',False)
    # A real legal historical snapshot precedes the final root (32+ CHUNKs).
    # Its existing test source is generated on demand below, not added to corpus.
    for field, number in [('length',2**63), ('ordinal',2**64-1)]:
        data = bytearray(prefix); struct.pack_into('<Q',data,chunk['offset']+({'length':24,'ordinal':16}[field]),number)
        # No valid footer now promises this incomplete tail: recovery yields HEADER.
        add('uncommitted-'+field+'-overflow', data[:chunk['offset']+chunk['length']], 'recovered',entries='0')
    for parent in ['native', 'absent-other-observation']:
        view = (CORPUS/'files/illustration-04-stored.lmtr').read_bytes()
        def edit_view(kind, ordinal, value):
            if kind == 2:
                native = next(p for e in value['entries'] for p in e.get('payloads',[]) if p['kind']=='view')
                native['parent_payload_id'] = native['id'] if parent=='native' else parent
            return value
        add('nested-or-cross-observation-view-'+parent, rebuild(view[:records(view)[-1]['offset']],edit_view), 'invalid_payload')
    child=(CORPUS/'files/frozen-single-file-reprocessing.lmtr').read_bytes()
    def self_parent(kind,ordinal,value):
        if kind==1:value['lineage']['parents'][0]['recording_id']=value['recording_id']
        return value
    add('self-referential-lineage-header',rebuild(child,self_parent),'invalid_metadata')
    def dataset(kind,ordinal,value):
        if kind==1:value['lineage']['parents'][0]['format_id']='lmthermal-radiometric-recording'
        return value
    add('historical-dataset-disguised-as-single-file-parent',rebuild(child,dataset),'unsupported_required_feature')
    for empty in (False,True):
        def null_entry(kind,ordinal,value):
            if kind==2:
                for entry in value['entries']:
                    if 'parent_entry' in entry:
                        entry['parent_entry']['entry_id']=None
                        if empty:entry['extensions']={}
                        else:entry.pop('extensions',None)
            return value
        add('null-parent-entry-with-'+('empty' if empty else 'absent')+'-reference-evidence',rebuild(child,null_entry),'invalid_metadata')
    return cases


def historical_cases(directory):
    """Build 37 small chunks with two real checkpoints and an orphan snapshot.

Thirty-five independent observations plus explicit segment boundaries exercise
older trusted checkpoint fallback; no prototype/canonical source is overwritten.
"""
    plan=loads((CORPUS/'plans/illustration-01-stored.json').read_bytes())
    item=plan['chunks'][0]; metadata=item['metadata']; entries=metadata['entries']
    chunks=[]
    for index in range(37):
        measurement=0<index<36
        entry=deepcopy(entries[1] if measurement else entries[0] if index==0 else entries[2])
        entry['entry_id']=str(index);entry['timeline_ns']=str(index*1000000)
        if measurement:
            entry['observation_sequence']=str(index-1);entry['receipt']['stamp']['ticks']=str(index*1000000)
        current=deepcopy(metadata);current['entries']=[entry]
        if not measurement:current['contexts']=[];current['blocks']=[]
        recipes={str(index)+':'+key.split(':',1)[1]:deepcopy(value) for key,value in item['payload_recipes'].items()} if measurement else {}
        chunks.append(dict(metadata=current,payload_recipes=recipes))
    plan['chunks']=chunks
    source=Path(directory)/'historical.lmtr';write_plan(plan,source);raw=source.read_bytes()
    recs=records(raw);checkpoints=[r for r in recs if r['kind']==4]
    assert len(checkpoints)==2
    damaged=bytearray(raw[:recs[-1]['offset']]);damaged[checkpoints[-1]['offset']+40]^=1
    first_index=next(r for r in recs if r['kind']==3)
    def malicious(kind,ordinal,value):
        if ordinal==first_index['ordinal']:value['children'][0]['record']['hash']='0'*64
        return value
    corrupt=bytearray(damaged)
    earlier_chunk=next(r for r in recs if r['kind']==2 and r['ordinal']>1)
    corrupt[earlier_chunk['offset']+40]^=1
    return [dict(name='legal-historical-snapshots',raw=raw,expected='valid',recover=False,entries='37'),
            dict(name='late-damaged-checkpoint-older-prefix',raw=bytes(damaged),expected='recovered',recover=True,entries='37'),
            dict(name='payload-before-old-checkpoint-and-late-nav-damage',raw=bytes(corrupt),expected='committed_payload_integrity',recover=True,entries=None),
            dict(name='malicious-orphan-historical-reference',raw=rebuild(raw,malicious),expected='invalid_container',recover=False,entries=None)]


def outcome(path, recover=True):
    """Return a stable full-check outcome, preserving all non-success distinctions."""
    try:
        with Reader(path) as reader: return reader.check(full=True,recover=recover)
    except ContractError as error: return dict(status='error',error=error.code,integrity_scope='none')


class CombinedRecoveryTests(unittest.TestCase):
    def test_layer_isolated_and_combined_corruption(self):
        with tempfile.TemporaryDirectory(prefix='lmtr-combined-') as directory:
            for case in combined_cases()+historical_cases(directory):
                with self.subTest(case=case['name']):
                    path = Path(directory)/'owned.lmtr'; path.write_bytes(case['raw'])
                    result = outcome(path,case['recover'])
                    self.assertEqual(case['expected'],result.get('error',result['status']),result)
                    if case['entries'] is not None: self.assertEqual(case['entries'],result['summary']['entries'])
                    if result['status']=='recovered':
                        self.assertTrue(result['unknown_tail']);self.assertIsNone(result['declared_end_status'])
                        self.assertEqual('full_verified_prefix',result['integrity_scope'])
                    self.assertEqual(case['raw'],path.read_bytes())


class CooperativeCancellationTests(unittest.TestCase):
    def test_full_and_recovery_cancel_without_success_and_callback_is_restored(self):
        for name,recover in [('illustration-01-stored',False),('committed-index-damage',True)]:
            path=CORPUS/'files'/f'{name}.lmtr';before=path.read_bytes()
            with Reader(path) as reader:
                polls=0
                def cancelled():
                    nonlocal polls
                    polls+=1
                    return polls==3
                with self.assertRaises(ContractError) as failure: reader.check(full=True,recover=recover,cancelled=cancelled)
                self.assertEqual('cancelled',failure.exception.code)
                self.assertEqual(3,polls)
                self.assertFalse(reader.cancellation())
            self.assertTrue(reader.file.closed);self.assertEqual(before,path.read_bytes())

    def test_salvage_cancels_after_one_chunk_with_narrow_scope(self):
        path=CORPUS/'files/illustration-01-stored.lmtr';before=path.read_bytes()
        with Reader(path) as reader:
            polls=0
            def cancelled():
                nonlocal polls
                polls+=1
                return polls==3
            result=reader.salvage(cancelled)
        self.assertTrue(result['cancelled']);self.assertEqual(1,len(result['ranges']))
        self.assertEqual('salvaged_chunks',result['integrity_scope']);self.assertIsNone(result['declared_end_status'])
        self.assertTrue(reader.file.closed);self.assertEqual(before,path.read_bytes())
