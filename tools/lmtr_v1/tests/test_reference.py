"""Executable wire evidence: immutable round trips, exact JSON and corrupt prefixes."""
from copy import deepcopy
from decimal import Decimal, localcontext
from pathlib import Path
import json
import struct
import subprocess
import sys
import tempfile
import unittest
import zlib

from tools.lmtr_v1 import ContractError, Reader, dumps, loads, write_plan
from tools.lmtr_v1.container import HEADER, FOOTER, encode_record, inflate
from tools.lmtr_v1.metadata import SHAPES, sha

ROOT=Path(__file__).resolve().parents[3]
CASES=loads((ROOT/'docs/lmtr-r1b/examples.json').read_bytes())['cases']

def plan(case=0):
    c=deepcopy(CASES[case])
    return {'header':c['header'],'chunks':[{'metadata':c['chunk'],'payload_recipes':c['payload_recipes']}]}

def split_plan():
    source=plan(); item=source['chunks'][0]; chunks=[]
    for entry in item['metadata']['entries']:
        metadata=deepcopy(item['metadata']); metadata['entries']=[entry]
        metadata['contexts']=[c for c in metadata['contexts'] if c['id']==entry.get('context_id')]
        ids={p['block_id'] for p in entry.get('payloads',[]) if p['kind']=='materialized'}
        metadata['blocks']=[b for b in metadata['blocks'] if b['id'] in ids]
        chunks.append({'metadata':metadata,'payload_recipes':item['payload_recipes']})
    source['chunks']=chunks; return source

class ExactJsonTests(unittest.TestCase):
    def test_decimal_context_does_not_round_semantic_hashes(self):
        source=b'{"optional":1.0000000000000000000000000000000000001}'
        for precision in (1,7,28,120):
            with localcontext() as context:
                context.prec=precision
                self.assertEqual(dumps(loads(source)),source)
    def test_negative_json_zero_is_not_binary_negative_zero(self):
        self.assertEqual(dumps(loads(b'{"x":-0.0}')),b'{"x":0}')
        self.assertEqual(struct.pack('<f',-0.0),bytes.fromhex('00000080'))
    def test_scalar_key_order_not_utf16_and_no_normalization(self):
        value={'\U00010000':0,'\ue000':1,'e\u0301':2,'é':3}
        self.assertEqual(dumps(value),'{"é":2,"é":3,"":1,"𐀀":0}'.encode())
    def test_exact_canonical_thresholds(self):
        pairs={'1.0':'1','1e0':'1','0.000001':'0.000001','0.0000001':'1e-7','1e20':'100000000000000000000','1e21':'1e21','-12.300e40':'-1.23e41'}
        for source,target in pairs.items(): self.assertEqual(dumps(loads('{"x":'+source+'}')),('{"x":'+target+'}').encode())
    def test_integer_fields_reject_equivalent_decimal_tokens(self):
        for text in ('-0','1.0','1e0','true'):
            with self.subTest(text=text),self.assertRaises(ContractError): SHAPES.check('I',loads('{"x":'+text+'}')['x'])
        SHAPES.check('I',1)
    def test_duplicate_decoded_keys_rejected(self):
        with self.assertRaises(ContractError): loads(b'{"a":1,"\\u0061":2}')
    def test_nonfinite_surrogate_and_bom_rejected(self):
        for source in ('{"x":NaN}','{"x":Infinity}','{"x":"\\ud800"}','\ufeff{}'):
            with self.subTest(source=source),self.assertRaises(ContractError): loads(source)
    def test_exponent_limits_apply_even_to_zero(self):
        for token in ('0e5121','1e4097','1e-4097','0e999999999999999'):
            with self.subTest(token=token),self.assertRaises(ContractError): loads('{"x":'+token+'}')
        self.assertEqual(dumps(loads('{"x":0e5120}')),b'{"x":0}')
    def test_canonical_token_limit_closed_under_rereading(self):
        value=loads('{"x":1e4096}'); self.assertEqual(loads(dumps(value)),value)
        with self.assertRaises(ContractError): loads('{"x":'+('1'*1024)+'e4096}')
    def test_string_and_depth_bounds(self):
        nested=[]
        for _ in range(32): nested=[nested]
        for value in ({'x':'a'*16385},{'x':nested}):
            with self.assertRaises(ContractError): dumps(value)
    def test_binary_float_json_not_accepted(self):
        with self.assertRaises(ContractError): dumps({'x':0.1})

class CodecTests(unittest.TestCase):
    def test_bounded_zlib_exact_stream(self):
        data=b'x'*1000; self.assertEqual(inflate(zlib.compress(data,1),len(data)),data)
    def test_zlib_trailing_and_concatenation_rejected(self):
        encoded=zlib.compress(b'xyz')
        for data in (encoded+b'x',encoded+encoded):
            with self.assertRaises(ContractError): inflate(data,3)
    def test_zlib_wrong_decoded_length_and_expansion_rejected(self):
        for length in (2,4):
            with self.assertRaises(ContractError): inflate(zlib.compress(b'xyz'),length)
    def test_gzip_raw_and_dictionary_rejected(self):
        raw=zlib.compressobj(wbits=-15); raw=raw.compress(b'x')+raw.flush()
        dictionary=zlib.compressobj(zdict=b'shared'); dictionary=dictionary.compress(b'x')+dictionary.flush()
        for data in (raw,dictionary,b'\x1f\x8b'+bytes(8)):
            with self.assertRaises(ContractError): inflate(data,1)
    def test_adler_corruption_is_integrity_error(self):
        data=bytearray(zlib.compress(b'xyz')); data[-1]^=1
        with self.assertRaises(ContractError) as failure: inflate(bytes(data),3)
        self.assertEqual(failure.exception.code,'committed_payload_integrity')

class WireTests(unittest.TestCase):
    def setUp(self):
        self.directory=tempfile.TemporaryDirectory(); self.path=Path(self.directory.name)/'capture.lmtr'
    def tearDown(self): self.directory.cleanup()
    def check(self,**options):
        before=self.path.read_bytes()
        try:
            with Reader(self.path) as reader: return reader.check(**options)
        finally: self.assertEqual(self.path.read_bytes(),before,'read path mutated source')
    def test_all_supported_original_illustrations_encode_and_full_validate(self):
        for i,c in enumerate(CASES):
            if c['id'] in ('11','12','13'):continue
            path=Path(self.directory.name)/(c['id']+'.lmtr')
            result=write_plan(plan(i),path)
            self.assertEqual(result['status'],'valid'); self.assertEqual(result['integrity_scope'],'full')
    def test_mask_negative_zero_and_exact_hashes(self):
        result=write_plan(plan(),self.path)
        self.assertEqual(result['logical_hashes']['1:temperature'],CASES[0]['chunk']['entries'][1]['payloads'][0]['hash'])
        self.assertEqual(result['summary']['temperature_planes'],'1')
    def test_open_reports_only_visited_scope(self):
        write_plan(plan(),self.path); result=self.check()
        self.assertEqual(result['integrity_scope'],'opened_records'); self.assertEqual(result['logical_hashes'],{})
    def test_selected_entry_validates_the_whole_selected_chunk(self):
        write_plan(split_plan(),self.path); result=self.check(entry=1)
        self.assertEqual(result['integrity_scope'],'selected_chunks'); self.assertEqual(result['selected_chunks'],1)
        self.assertEqual(set(result['logical_hashes']),{'1:temperature','1:validity'})
    def test_multi_chunk_order_and_index(self):
        write_plan(split_plan(),self.path); result=self.check(full=True)
        self.assertEqual(result['summary']['chunks'],'3'); self.assertEqual(result['summary']['entries'],'3')
    def many_chunks(self,count):
        source=split_plan(); start,measurement,end=source['chunks']; chunks=[start]
        for i in range(count):
            item=deepcopy(measurement); entry=item['metadata']['entries'][0]
            entry['entry_id']=str(i+1); entry['timeline_ns']=str(i+1); entry['observation_sequence']=str(i)
            item['payload_recipes']={str(i+1)+':'+key.split(':')[1]:value for key,value in item['payload_recipes'].items()}
            chunks.append(item)
        end['metadata']['entries'][0]['entry_id']=str(count+1); end['metadata']['entries'][0]['timeline_ns']=str(count+1); chunks.append(end);source['chunks']=chunks
        return source
    def rewrite_record_same_length(self,record,value):
        body=dumps(value); raw=encode_record(record['kind'],body,record['ordinal'],record['previous'])
        self.assertEqual(len(raw),record['length'])
        data=bytearray(self.path.read_bytes()); data[record['offset']:record['offset']+record['length']]=raw; self.path.write_bytes(data)
    def test_full_scan_validates_unreachable_historical_snapshot_pages(self):
        write_plan(self.many_chunks(34),self.path)
        with Reader(self.path) as reader:
            cursor=0
            while True:
                record=reader.record(cursor)
                if record['kind']==3: break
                cursor+=record['length']
            value=deepcopy(record['json']); name=value['stream_ids'][0]; value['stream_ids']=[name[:-1]+('x' if name[-1]!='x' else 'y')]
        self.rewrite_record_same_length(record,value)
        # Final root is a fresh snapshot, so its lazy open does not claim historical-page validation.
        self.assertEqual(self.check()['integrity_scope'],'opened_records')
        with self.assertRaises(ContractError):self.check(full=True)
    def test_cached_reference_still_checks_current_owner_ordinal(self):
        write_plan(plan(),self.path)
        with Reader(self.path) as reader:
            end=reader.terminal(); checkpoint=reader.reference(end['json']['checkpoint'],end,4)
            ref=checkpoint['json']['root']; reader.reference(ref,checkpoint,3)
            wrong_owner=dict(checkpoint); wrong_owner['ordinal']=int(ref['ordinal'])
            with self.assertRaises(ContractError):reader.reference(ref,wrong_owner,3)
    def test_checkpoint_last_chunk_ordinal_is_verified(self):
        write_plan(plan(),self.path)
        with Reader(self.path) as reader:
            end=reader.terminal(); checkpoint=reader.reference(end['json']['checkpoint'],end,4)
            value=deepcopy(checkpoint['json']); value['last_chunk']['ordinal']='0'
            forged=dict(checkpoint,json=value)
            with self.assertRaises(ContractError):reader.checkpoint_shape(forged)
    def test_writer_seals_large_stream_inventory_pages_before_fanout(self):
        source=plan(); original=source['chunks'][0]['metadata']['segments'][0]
        slots=[{'id':'s%02d'%i+'a'*120,'purpose':'thermal'} for i in range(64)]
        source['header']['streams']=slots; chunks=[]
        for i in range(34):
            segment=deepcopy(original); segment['streams']=[]
            for slot in slots:
                stream=deepcopy(original['streams'][0]); stream['stream_id']=slot['id']; segment['streams'].append(stream)
            segment['start_entry']=str(2*i);segment['start_timeline_ns']=str(2*i);segment['id']=sha(dumps({k:v for k,v in segment.items() if k!='id'}))
            for entry in ({'kind':'segment_start','entry_id':str(2*i),'timeline_ns':str(2*i),'segment_id':segment['id']},{'kind':'segment_end','entry_id':str(2*i+1),'timeline_ns':str(2*i+1),'segment_id':segment['id'],'reason':'normal_stop'}):
                chunks.append({'metadata':{'recording_id':source['header']['recording_id'],'required_features':source['header']['required_features'],'segments':[segment],'contexts':[],'entries':[entry],'blocks':[]},'payload_recipes':{}})
        source['chunks']=chunks; result=write_plan(source,self.path)
        self.assertEqual(result['summary']['chunks'],'68')
        with Reader(self.path) as reader:
            cursor=0; shallow=[]
            while cursor<reader.size:
                record=reader.record(cursor)
                if record['kind']==3 and record['json']['depth']==0: shallow.append(record['json'])
                cursor+=record['length']
        self.assertTrue(any(32<len(page['children'])<68 for page in shallow),'expected early-sized page before 256 leaves')
    def test_rolling_index_promotes_past_256_leaves(self):
        source=split_plan(); start,measurement,end=source['chunks']; chunks=[start]
        for i in range(258):
            item=deepcopy(measurement); entry=item['metadata']['entries'][0]
            entry['entry_id']=str(i+1); entry['timeline_ns']=str(i+1); entry['observation_sequence']=str(i)
            item['payload_recipes']={str(i+1)+':'+key.split(':')[1]:value for key,value in item['payload_recipes'].items()}
            chunks.append(item)
        end['metadata']['entries'][0]['entry_id']='259'; end['metadata']['entries'][0]['timeline_ns']='259'; chunks.append(end);source['chunks']=chunks
        result=write_plan(source,self.path)
        self.assertEqual(result['summary']['chunks'],'260')
        self.assertEqual(result['summary']['observations'],'258')
        self.assertEqual(self.check(entry=257)['integrity_scope'],'selected_chunks')
    def test_zero_chunks_empty_terminal_tree(self):
        source=plan(); source['chunks']=[]; result=write_plan(source,self.path)
        self.assertEqual(result['summary']['chunks'],'0'); self.assertIsNone(result['summary']['last_entry'])
    def test_deflate_roundtrip_preserves_source_float32_bits(self):
        source=plan()
        for b in source['chunks'][0]['metadata']['blocks']: b['codec']=1
        result=write_plan(source,self.path)
        self.assertEqual(result['logical_hashes']['1:temperature'],CASES[0]['chunk']['entries'][1]['payloads'][0]['hash'])
    def test_truncated_end_readonly_recovery_keeps_unknown_tail(self):
        write_plan(plan(),self.path); data=self.path.read_bytes(); self.path.write_bytes(data[:-30])
        with self.assertRaises(ContractError): self.check()
        result=self.check(recover=True,full=True)
        self.assertEqual(result['status'],'recovered'); self.assertTrue(result['unknown_tail']); self.assertIsNone(result['declared_end_status'])
        self.assertEqual(result['summary']['observations'],'1')
    def test_committed_payload_corruption_not_a_gap(self):
        write_plan(plan(),self.path)
        with Reader(self.path) as reader:
            offset=reader.header_end; record=reader.record(offset); prefix=struct.unpack_from('<I',record['body'])[0]
        data=bytearray(self.path.read_bytes()); data[offset+40+4+prefix]^=1; self.path.write_bytes(data)
        for recover in (False,True):
            with self.assertRaises(ContractError) as failure:self.check(full=True,recover=recover)
            self.assertEqual(failure.exception.code,'committed_payload_integrity')
    def test_header_crc_failure_not_bypassed_by_recovery(self):
        write_plan(plan(),self.path); data=bytearray(self.path.read_bytes()); data[12]=1; self.path.write_bytes(data)
        with self.assertRaises(ContractError): self.check(recover=True)
    def test_fast_recovery_candidate_limit_is_explicit(self):
        write_plan(plan(),self.path)
        forged=b'x'*40+FOOTER.pack(b'LMTRCMT1',112,0,0,b'\0'*32,0,0)
        self.path.write_bytes(self.path.read_bytes()+forged*1025)
        with self.assertRaises(ContractError) as failure:self.check(recover=True)
        self.assertEqual(failure.exception.code,'recovery_limit')
    def test_header_unknown_feature_rejects_even_recovery(self):
        source=plan(); value=deepcopy(source['header']); value['required_features'].append('org.example.required'); value['required_features'].sort()
        self.path.write_bytes(encode_record(1,dumps(value),0,2**63-1))
        with self.assertRaises(ContractError) as failure:self.check(recover=True)
        self.assertEqual(failure.exception.code,'unsupported_required_feature')
    def test_header_unassigned_codec_has_stable_error(self):
        value=deepcopy(plan()['header']); value['codecs']=[0,2]
        self.path.write_bytes(encode_record(1,dumps(value),0,2**63-1))
        with self.assertRaises(ContractError) as failure:self.check(recover=True)
        self.assertEqual(failure.exception.code,'unsupported_codec')
    def test_malformed_header_shapes_return_contract_errors(self):
        for key,value in [('container_version','not_object'),('codecs',True),('streams',17)]:
            source=deepcopy(plan()['header']); source[key]=value
            self.path.write_bytes(encode_record(1,dumps(source),0,2**63-1))
            with self.subTest(key=key),self.assertRaises(ContractError): self.check()
    def test_header_only_missing_end_is_incomplete_not_completed(self):
        value=plan()['header']; self.path.write_bytes(encode_record(1,dumps(value),0,2**63-1))
        result=self.check(recover=True,full=True)
        self.assertEqual(result['summary']['entries'],'0');self.assertTrue(result['unknown_tail'])
        self.assertIsNone(result['declared_end_status'])
    def test_unsupported_required_feature_writer_refuses(self):
        source=plan(); source['header']['required_features'].append('org.example.essential-v1'); source['header']['required_features'].sort()
        with self.assertRaises(ContractError) as failure:write_plan(source,self.path)
        self.assertEqual(failure.exception.code,'unsupported_required_feature')
    def test_reserved_codec_writer_refuses(self):
        source=plan(); source['chunks'][0]['metadata']['blocks'][0]['codec']=2
        with self.assertRaises(ContractError) as failure:write_plan(source,self.path)
        self.assertEqual(failure.exception.code,'unsupported_codec')
    def test_segment_close_required_before_end(self):
        source=plan(); source['chunks'][0]['metadata']['entries'].pop()
        with self.assertRaises(ContractError):write_plan(source,self.path)
    def test_chunk_closure_hash_cannot_drop_optional_exact_metadata(self):
        source=plan(); context=source['chunks'][0]['metadata']['contexts'][0]; context['value']['optional']=Decimal('1.000000000000000000000000001')
        with self.assertRaises(ContractError):write_plan(source,self.path)
    def test_context_source_change_rejected(self):
        source=plan(); item=source['chunks'][0]; context=item['metadata']['contexts'][0]; context['value']['source']['model_id']='different'; context['id']=sha(dumps(context['value']))
        item['metadata']['entries'][1]['context_id']=context['id']
        with self.assertRaises(ContractError):write_plan(source,self.path)
    def test_reprocessing_requires_known_original_algorithm_and_settings(self):
        source=plan(); source['header']['lineage']={'operation':'reprocessing','parents':[{'format_id':'lmthermal-recording','recording_id':None,'file_sha256':'1'*64,'file_bytes':'100'}],'algorithm':{'status':'known','value':{'id':'org.example.copy','version':'1'}}}
        entry=source['chunks'][0]['metadata']['entries'][1]; entry['parent_entry']={'parent_index':0,'entry_id':'1'}
        with self.assertRaises(ContractError) as failure:write_plan(source,self.path)
        self.assertIn('known algorithm/settings',failure.exception.detail)
    def test_mask_two_not_silently_truthy(self):
        source=plan(); source['chunks'][0]['payload_recipes']['1:validity']['hex']='020101000101'
        with self.assertRaises(ContractError) as failure:write_plan(source,self.path)
        self.assertEqual(failure.exception.code,'invalid_payload')
    def test_invalid_cell_requires_positive_zero_bits(self):
        source=plan(); recipe=source['chunks'][0]['payload_recipes']['1:temperature']; data=bytearray.fromhex(recipe['hex']); data[12:16]=bytes.fromhex('00000080'); recipe['hex']=data.hex()
        with self.assertRaises(ContractError):write_plan(source,self.path)
    def test_roi_cache_incorrect_statistics_rejected(self):
        source=plan(); measurement=source['chunks'][0]['metadata']['entries'][1]['measurement']; measurement['statistics']['mean']=999
        with self.assertRaises(ContractError):write_plan(source,self.path)
    def test_observation_counter_skip_needs_explicit_gap(self):
        source=plan(); e=source['chunks'][0]['metadata']['entries'][1]; other=deepcopy(e); other['entry_id']='2'; other['observation_sequence']='99'; other['timeline_ns']='2'; source['chunks'][0]['metadata']['entries'].insert(2,other); end=source['chunks'][0]['metadata']['entries'][-1]; end['entry_id']='3';end['timeline_ns']='3'
        for key,recipe in list(source['chunks'][0]['payload_recipes'].items()):source['chunks'][0]['payload_recipes']['2:'+key.split(':')[1]]=recipe
        with self.assertRaises(ContractError):write_plan(source,self.path)
    def test_cli_check_and_canon_are_machine_readable(self):
        write_plan(plan(),self.path)
        result=subprocess.run([sys.executable,'-m','tools.lmtr_v1','check',str(self.path),'--full'],cwd=ROOT,capture_output=True,check=True)
        self.assertEqual(json.loads(result.stdout)['status'],'valid')
        source=Path(self.directory.name)/'json';source.write_text('{"x":1.0}')
        result=subprocess.run([sys.executable,'-m','tools.lmtr_v1','canon',str(source)],cwd=ROOT,capture_output=True,check=True)
        self.assertEqual(result.stdout,b'{"x":1}')
    def test_writer_refuses_overwrite_existing_capture(self):
        write_plan(plan(),self.path); original=self.path.read_bytes()
        with self.assertRaises(FileExistsError):write_plan(plan(),self.path)
        self.assertEqual(self.path.read_bytes(),original)
