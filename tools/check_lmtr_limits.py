#!/usr/bin/env python3
"""On-demand maximum index depth and bounded recovery refusal vectors.

Construction uses copied synthetic authoritative source bytes and independent raw
framing. Negative lengths/footer candidates never enter the canonical Git corpus.
"""
import argparse
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import subprocess
import struct
import tempfile
from lmtr_v1 import Reader,ContractError,dumps
from lmtr_v1.container import encode_record,make_ref,COMMIT

ROOT=Path(__file__).resolve().parents[1]
TAIL=68157512


def jvm(java,classpath,path,*flags):
    """Require the bounded JVM command to give a structured diagnostic exit."""
    result=subprocess.run([java,'-Xmx128m','-cp',classpath,'org.lmthermal.lmtr.LmtrCommand','check',str(path),*flags],capture_output=True,text=True,timeout=180)
    assert result.returncode in (0,2),result.stdout+result.stderr
    return json.loads(result.stdout)


def tree(path,levels):
    """Create a real singleton tree at each accepted level; no missing/fake source leaves."""
    seed=ROOT/'lmtr-contract/src/test/resources/lmtr/files/illustration-01-stored.lmtr'
    with Reader(seed) as reader:
        header=reader.record(0);chunk=reader.record(header['length']);metadata=reader.metadata(chunk);end=deepcopy(reader.terminal()['json'])
    ordinal=0;previous=2**63-1
    with path.open('xb') as output:
        def append(kind,body,first=None,last=None):
            nonlocal ordinal,previous
            offset=output.tell();raw=encode_record(kind,body,ordinal,previous);output.write(raw)
            ref={'offset':str(offset),'length':str(len(raw)),'ordinal':str(ordinal),'first':first,'last':last,'hash':hashlib.sha256(raw[:40]+hashlib.sha256(body).digest()).hexdigest()}
            ordinal+=1;previous=offset;return ref
        append(1,header['body']);source=append(2,chunk['body'],'0','2');root=source
        for depth in range(levels):
            child={'record':root,'time_first_ns':'0','time_last_ns':'2000000','stream_ids':['primary']}
            page={'depth':depth,'children':[child],'first':'0','last':'2','time_first_ns':'0','time_last_ns':'2000000','stream_ids':['primary'],'chunks':'1','entries':'3'}
            root=append(3,dumps(page),'0','2')
        checkpoint={'recording_id':header['json']['recording_id'],'root':root,'last_chunk':source,'previous_checkpoint':None,'summary':end['summary']}
        cp=append(4,dumps(checkpoint),'0','2');end.update(root=root,checkpoint=cp);append(5,dumps(end))


def long_prefix(path,chunks=1,navigation=0):
    """Create locally valid source epochs or trusted orphan pages for exact fast bounds."""
    seed=ROOT/'lmtr-contract/src/test/resources/lmtr/files/illustration-01-stored.lmtr'
    with Reader(seed) as reader:
        head=reader.record(0);source=reader.record(head['length']);original=reader.metadata(source)
    prefix=4+struct.unpack_from('<I',source['body'])[0];ordinal=0;previous=2**63-1
    with path.open('xb') as output:
        def append(kind,body):
            nonlocal ordinal,previous
            offset=output.tell();raw=encode_record(kind,body,ordinal,previous);output.write(raw)
            ref={'offset':str(offset),'length':str(len(raw)),'ordinal':str(ordinal),'first':'0','last':'2','hash':hashlib.sha256(raw[:40]+hashlib.sha256(body).digest()).hexdigest()}
            ordinal+=1;previous=offset;return ref
        append(1,head['body'])
        for n in range(chunks):
            metadata=deepcopy(original);segment=metadata['segments'][0]
            segment['start_entry']=str(n*3);segment['start_timeline_ns']=str(n*3000000)
            segment['id']=hashlib.sha256(dumps({k:v for k,v in segment.items() if k!='id'})).hexdigest()
            for j,entry in enumerate(metadata['entries']):
                entry['entry_id']=str(n*3+j);entry['timeline_ns']=str(n*3000000+j*1000000);entry['segment_id']=segment['id']
                if entry['kind']=='observation':entry['receipt']['stamp']['ticks']=entry['timeline_ns']
            encoded=dumps(metadata);ref=append(2,struct.pack('<I',len(encoded))+encoded+source['body'][prefix:])
        for _ in range(navigation):
            child={'record':ref,'time_first_ns':'0','time_last_ns':'2000000','stream_ids':['primary']}
            page={'depth':0,'children':[child],'first':'0','last':'2','time_first_ns':'0','time_last_ns':'2000000','stream_ids':['primary'],'chunks':'1','entries':'3'}
            append(3,dumps(page))


def expected_error(path,recover,code):
    """Require a real refusal, not a guessed incomplete success classification."""
    try:
        with Reader(path) as reader:reader.check(full=True,recover=recover)
        raise AssertionError('reference accepted negative limit')
    except ContractError as error:assert error.code==code,(error.code,code)


def run(java,classpath):
    """Exercise legal eight levels, illegal ninth and exact recovery budgets independently."""
    with tempfile.TemporaryDirectory(prefix='lmtr-limits-') as directory:
        directory=Path(directory);positive=directory/'depth8.lmtr';tree(positive,8);before=positive.read_bytes()
        with Reader(positive) as reader:expected=reader.check(full=True)
        actual=jvm(java,classpath,positive,'--full')
        assert actual['status']=='valid' and actual['summary']==expected['summary'] and actual['logical_hashes']==expected['logical_hashes']
        selected=jvm(java,classpath,positive,'--entry','1');assert selected['integrity_scope']=='selected_chunks' and selected['chunks_decoded']=='1'
        assert positive.read_bytes()==before
        negative=directory/'depth9.lmtr';tree(negative,9);expected_error(negative,False,'resource_limit');assert jvm(java,classpath,negative,'--full')['error']=='resource_limit'
        seed=ROOT/'lmtr-contract/src/test/resources/lmtr/files/illustration-01-stored.lmtr'
        with Reader(seed) as reader:header=reader.record(0)['body']
        tail=directory/'tail-too-long.lmtr'
        with tail.open('xb') as output:
            raw=encode_record(1,header,0,2**63-1);output.write(raw);output.seek(len(raw)+TAIL);output.write(b'\0')
        expected_error(tail,True,'recovery_limit');assert jvm(java,classpath,tail,'--recover','--full')['error']=='recovery_limit'
        candidates=directory/'candidate-limit.lmtr';candidates.write_bytes(encode_record(1,header,0,2**63-1)+(COMMIT+bytes(64))*1025)
        expected_error(candidates,True,'recovery_limit');assert jvm(java,classpath,candidates,'--recover','--full')['error']=='recovery_limit'
        suffix=directory/'suffix65.lmtr';long_prefix(suffix,chunks=65)
        expected_error(suffix,True,'recovery_limit');assert jvm(java,classpath,suffix,'--recover','--full')['error']=='recovery_limit'
        boundary=directory/'suffix64.lmtr';long_prefix(boundary,chunks=64)
        with Reader(boundary) as reader:reference=reader.check(full=True,recover=True)
        verified=jvm(java,classpath,boundary,'--recover','--full')
        assert verified['status']=='recovered' and verified['summary']==reference['summary'] and verified['logical_hashes']==reference['logical_hashes']
        assert verified['summary']['chunks']=='64'
        predecessors=directory/'predecessor-limit.lmtr';long_prefix(predecessors,navigation=1025)
        expected_error(predecessors,True,'recovery_limit');assert jvm(java,classpath,predecessors,'--recover','--full')['error']=='recovery_limit'
        return {'status':'passed','legal_depth_levels':8,'illegal_depth_levels':9,'maximum_depth_full_exact_parity':True,'selected_chunks_at_maximum_depth':1,'fast_tail_bytes':TAIL,'oversized_unknown_tail':'recovery_limit','false_footer_candidates':1025,'candidate_refusal':'recovery_limit','verified_suffix_chunks_at_limit':64,'suffix_chunks_refused':65,'predecessor_navigation_refused':1025,'source_immutability':True,'jvm_heap_cap_bytes':134217728,'limitations':['finite synthetic host limits, not physical power-loss','cancellation latency and exhaustive mixed-corruption combinations remain separate','no native Windows/ART'],'accuracy_warning':'Native-equivalent temperatures; absolute physical accuracy not yet independently validated.'}


def main():
    """Persist small evidence only; all owned stress files remain temporary."""
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java',required=True);parser.add_argument('--classpath',required=True);parser.add_argument('--report',type=Path);args=parser.parse_args();result=run(args.java,args.classpath)
    if args.report:args.report.write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
    print(json.dumps(result,sort_keys=True))

if __name__=='__main__':main()
