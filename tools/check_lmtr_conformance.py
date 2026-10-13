#!/usr/bin/env python3
"""Independent JVM/Python runtime interoperation; no camera or production application.

Build :lmtr-contract:writeRuntimeClasspath first. Temporary Kotlin artifacts are
read by Python, and checked against independently generated corpus expectations.
No independently compressed zlib bytes are assumed identical.
"""
import argparse
from decimal import Decimal, localcontext
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import tempfile

from lmtr_v1 import Reader, ContractError, dumps, loads

ROOT=Path(__file__).resolve().parents[1]
CORPUS=ROOT/'lmtr-contract/src/test/resources/lmtr'
WARNING='Native-equivalent temperatures; absolute physical accuracy not yet independently validated.'


def sha(path):
    """Hash source bytes without loading a container into memory."""
    digest=hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda:source.read(1048576),b''):digest.update(block)
    return digest.hexdigest()


def command(java,classpath,*args):
    """Run the independently built bounded JVM CLI."""
    return subprocess.run([java,'-Xmx256m','-cp',classpath,'org.lmthermal.lmtr.LmtrCommand',*map(str,args)],capture_output=True,check=False)


def report_command(java,classpath,*args):
    """Require structured success/error exit correspondence."""
    result=command(java,classpath,*args)
    assert result.returncode in (0,2),result.stderr.decode()
    report=json.loads(result.stdout)
    assert (result.returncode==0)==(report['status']!='error'),report
    return report


def compare(expected,actual):
    """Compare conformance meaning, not incidental diagnostic visit counters."""
    fields=['status']
    fields+=['error'] if expected['status']=='error' else ['integrity_scope','summary','logical_hashes','declared_end_status']
    if expected['status']=='recovered':fields+=['unknown_tail','verified_prefix_bytes','last_verified_entry','last_verified_timeline_ns','reason']
    for field in fields: assert expected.get(field)==actual.get(field),(field,expected.get(field),actual.get(field))


def json_vectors():
    """Independent below/at/above limits and semantic preservation/hash probes."""
    vectors=[('scalar-order',b'{"\\ud800\\udc00":1,"\\ue000":2,"e\\u0301":3,"\\u00e9":4}',None),
             ('exact-values',b'{"x":[9007199254740993,1.234567890123456789012345678901234567890123456789,-0,-0.0,1,1.0,1e0]}',None),
             ('escapes',b'{"x":"\\b\\t\\n\\f\\r\\u0000\\/\\\\\\\""}',None),
             ('duplicate',b'{"a":1,"\\u0061":2}','invalid_metadata'),
             ('surrogate',b'{"x":"\\ud800"}','invalid_metadata'),
             ('bom',b'\xef\xbb\xbf{}','invalid_metadata'),('utf8',b'{"x":"\xc0\x80"}','invalid_metadata'),
             ('nonfinite',b'{"x":NaN}','invalid_metadata')]
    for value in ['9'*1018,'1e-7','1e-6','1e-5','1e19','1e20','1e21','1e-4096','1e4096','0e5120','0e-5120','-0e5120']:
        vectors.append(('number-'+value,('{"x":'+value+'}').encode(),None))
    for value in ['1e-4097','1e4097','0e5121','0e-5121','9'*1019,'9'*1023,'9'*1024,'9'*1025,'0.'+'0'*1023+'1']:
        vectors.append(('refused-number-'+str(len(vectors)),('{"x":'+value+'}').encode(),'resource_limit'))
    # A 1024-byte accepted token can have trailing coefficient zeroes that vanish.
    vectors.append(('token-1024',('{"x":1'+ '0'*1023+'}').encode(),None))
    for n in (16383,16384,16385):vectors.append(('string-'+str(n),('{"x":"'+'a'*n+'"}').encode(),None if n<=16384 else 'resource_limit'))
    for n in (31,32,33):vectors.append(('depth-'+str(n),('{"x":'+'['*(n-1)+'0'+']'*(n-1)+'}').encode(),None if n<=32 else 'resource_limit'))
    for n in (65534,65535,65536):vectors.append(('elements-'+str(n),('{"x":['+','.join(['0']*n)+']}').encode(),None if n<=65535 else 'resource_limit'))
    return vectors


def run(java,classpath):
    """Validate both directions and publish only measured results."""
    manifest=json.loads((CORPUS/'manifest.json').read_bytes())
    python_read=0;kotlin_read=0;rewrites=0;canonical=0;json_results=[]
    with tempfile.TemporaryDirectory(prefix='lmtr-r3-interop-') as directory:
        temporary=Path(directory)
        for case in manifest['cases']:
            source=CORPUS/case['file'];before=sha(source);assert before==case['sha256']
            flags=['--full']+(['--recover'] if case.get('recover') else [])
            actual=report_command(java,classpath,'check',source,*flags)
            compare(case['expected'],actual);kotlin_read+=1
            try:
                with Reader(source) as reader:reference=reader.check(full=True,recover=case.get('recover',False))
            except ContractError as error:reference={'status':'error','error':error.code}
            compare(case['expected'],reference);python_read+=1
            assert before==sha(source),'source mutated'
            if case['classification']=='positive':
                output=temporary/(case['id']+'.lmtr')
                own=report_command(java,classpath,'write-plan',CORPUS/case['plan'],output)
                assert own['status']!='error',(case['id'],own)
                compare(case['expected'],own)
                produced=sha(output)
                with Reader(output) as reader:foreign=reader.check(full=True)
                compare(case['expected'],foreign);assert produced==sha(output)
                rewrites+=1
                plan=CORPUS/case['plan']
                canonical_result=command(java,classpath,'canon',plan)
                assert canonical_result.returncode==0,canonical_result.stdout
                assert canonical_result.stdout==dumps(loads(plan.read_bytes()))
                canonical+=1
        for name,data,error in json_vectors():
            vector=temporary/'json-vector.json';vector.write_bytes(data)
            runtime=command(java,classpath,'canon',vector)
            try:
                # Tiny caller precision must not truncate unknown semantic values.
                with localcontext() as context:
                    context.prec=7;expected=dumps(loads(data))
                assert error is None,(name,'unexpected Python acceptance')
                assert runtime.returncode==0,(name,runtime.stdout)
                assert runtime.stdout==expected,(name,'canonical bytes')
                json_results.append({'id':name,'status':'valid','canonical_sha256':hashlib.sha256(expected).hexdigest()})
            except ContractError as failure:
                assert error==failure.code,(name,failure.code,error)
                assert runtime.returncode==2,(name,runtime.stdout)
                assert json.loads(runtime.stdout)['error']==error,(name,runtime.stdout)
                json_results.append({'id':name,'status':'error','error':error})
    version=subprocess.run([java,'-version'],capture_output=True,text=True,check=True).stderr.splitlines()[0]
    return {'status':'passed','normative_commit':'8c343cd6cf1ca11eff4f3bbaa5c2b10dfbbf645f','base_main':'fe029363062cdf54d6cefa9d4f24265e80188091',
            'corpus_manifest_sha256':sha(CORPUS/'manifest.json'),'python_to_kotlin_full_cases':kotlin_read,'independent_python_full_cases':python_read,
            'kotlin_to_python_full_positive_cases':rewrites,'canonical_plan_byte_comparisons':canonical,'json_vectors':json_results,
            'java':version,'python':platform.python_version(),'platform':platform.system(),'jvm_heap_cap_bytes':268435456,
            'source_immutability':'all corpus sources and Kotlin-produced files unchanged by read/recovery',
            'compressed_byte_equality':'not required; exact logical bytes and descriptor hashes compared',
            'pending':['real native Windows','ART runtime','asynchronous recorder timers/backend durability','general salvage scanning'],
            'accuracy_warning':WARNING}


def main():
    """Use explicit reproducible toolchain/classpath and optional durable report."""
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java',default=os.environ.get('JAVA_HOME','')+'/bin/java' if os.environ.get('JAVA_HOME') else 'java')
    parser.add_argument('--classpath-file',type=Path,default=ROOT/'lmtr-contract/build/runtime-classpath.txt')
    parser.add_argument('--report',type=Path)
    args=parser.parse_args();report=run(args.java,args.classpath_file.read_text().strip())
    if args.report:args.report.write_text(json.dumps(report,indent=2,sort_keys=True)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k!='json_vectors'},sort_keys=True))

if __name__=='__main__':main()
