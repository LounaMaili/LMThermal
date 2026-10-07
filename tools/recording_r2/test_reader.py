"""Bounded independent reader rejection tests; no camera or GUI dependency."""
import copy
import json
import struct
import unittest
import zlib
from reader import Reader, add, bounded_json, decode_block, sha


class HostileTests(unittest.TestCase):
    def test_checked_lengths(self):
        for a,b in [(-1,1),((1<<63)-1,1),(0,1<<64)]:
            with self.assertRaises(ValueError): add(a,b)
    def test_depth_duplicate_unicode_nonfinite_json(self):
        for value in [b'{"x":1,"x":2}', b'{"x":NaN}', b'{"x":"\xff"}', ('{"x":'+'['*40+'0'+']'*40+'}').encode()]:
            with self.assertRaises((ValueError,UnicodeError)): bounded_json(value)
    def test_expansion_trailing_unsupported(self):
        for data,n,codec in [(zlib.compress(bytes(10000)),2,1),(zlib.compress(b'abc')+b'x',3,1),(b'abc',3,7)]:
            with self.assertRaises(ValueError): decode_block(data,n,codec)
    def base(self):
        temperature=struct.pack('<6f',1,2,3,4,5,6); raw=struct.pack('<6H',5000,5001,5002,5003,5004,5005); transport=raw+bytes(64)
        def desc(role,data,dtype,shape): return dict(kind='materialized',block=role,offset=0,length=len(data),hash=sha(data),dtype=dtype,shape=shape)
        payloads={'temperature':desc('temperature',temperature,'f32le',[2,3]),'acquisition':desc('acquisition',transport,'u8',[76]),
                  'native':dict(kind='view',parent='acquisition',offset=0,length=12,hash=sha(raw),dtype='u16le',shape=[2,3],encoding='synthetic')}
        blocks=[dict(role='temperature',offset=0,stored=24,decoded=24,codec=0,hash=sha(temperature)),
                dict(role='acquisition',offset=24,stored=76,decoded=76,codec=0,hash=sha(transport))]
        meta=dict(profile='FULL',contexts=[{}],entries=[dict(sequence='0',gap_end='0',relative_ns='0',receipt_ns=None,width=3,height=2,context=0,reason=None,payloads=payloads)],blocks=blocks)
        return meta, temperature+transport
    def check(self,meta,data):
        reader=Reader.__new__(Reader); reader.zstd=None
        return reader.decode_metadata(meta,len(data),lambda offset,size:data[offset:offset+size])
    def test_alternate_geometry_view_success(self):
        m,b=self.base(); decoded=self.check(m,b)
        self.assertEqual(decoded['payloads'][0]['native'],b[24:36])
    def test_every_prohibited_view_form(self):
        mutations=[('offset',-1),('offset',(1<<63)-1),('length',13),('parent','native'),('stride',2),('transform','mirror'),('frame',1),('chunk',1),('dtype','u16be'),('shape',[3,2]),('hash','0'*64),('kind','nested')]
        for key,value in mutations:
            with self.subTest(key=key):
                m,b=self.base(); m['entries'][0]['payloads']['native'][key]=value
                with self.assertRaises(ValueError): self.check(m,b)
    def test_parent_corruption_context_dimensions_and_blocks(self):
        for change in ['parent','context','dimensions','codec','block_bound','view_of_view']:
            with self.subTest(change=change):
                m,b=self.base()
                if change=='parent': m['entries'][0]['payloads']['acquisition']['hash']='0'*64
                if change=='context': m['entries'][0]['context']=3
                if change=='dimensions': m['entries'][0]['width']=16384; m['entries'][0]['height']=16384
                if change=='codec': m['blocks'][0]['codec']=5
                if change=='block_bound': m['blocks'][0]['decoded']=65*1048576
                if change=='view_of_view': m['entries'][0]['payloads']['acquisition']['kind']='view'
                with self.assertRaises(ValueError): self.check(m,b)

if __name__=='__main__': unittest.main()
