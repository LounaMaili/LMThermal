#!/usr/bin/env python3
"""Independent same-byte mixed-corruption regression, without repinning the corpus."""
import argparse
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
from tools.lmtr_v1.tests.test_hardening import combined_cases, historical_cases, outcome


def run(java,classpath):
    """Require each explicit oracle in both runtimes and preserve all owned test bytes."""
    results=[]
    with tempfile.TemporaryDirectory(prefix='lmtr-hardening-') as directory:
        for case in combined_cases()+historical_cases(directory):
            path=Path(directory)/(case['name']+'.lmtr');path.write_bytes(case['raw'])
            expected=case['expected'];before=hashlib.sha256(case['raw']).hexdigest()
            python=outcome(path,case['recover'])
            process=subprocess.run([java,'-Xmx128m','-cp',classpath,'org.lmthermal.lmtr.LmtrCommand',
                                    'check',str(path),'--full']+(['--recover'] if case['recover'] else []),
                                   capture_output=True,text=True,timeout=30)
            assert process.returncode in (0,2),(case['name'],process.stderr)
            kotlin=json.loads(process.stdout)
            for runtime,result in [('python',python),('kotlin',kotlin)]:
                assert result.get('error',result['status'])==expected,(case['name'],runtime,expected,result)
                if case['entries'] is not None: assert result['summary']['entries']==case['entries'],(case['name'],runtime,result)
                if result['status']=='recovered':
                    assert result['unknown_tail'] and result['declared_end_status'] is None
                    assert result['integrity_scope']=='full_verified_prefix'
            assert before==hashlib.sha256(path.read_bytes()).hexdigest()
            results.append(dict(case=case['name'],bytes=len(case['raw']),sha256=before,expected=expected,
                                kotlin=kotlin,python=python,immutable=True))
    return dict(platform=platform.platform(),python=platform.python_version(),cases=results,case_count=len(results),
                fixture_manifest_sha256=hashlib.sha256((ROOT/'lmtr-contract/src/test/resources/lmtr/manifest.json').read_bytes()).hexdigest(),
                scope='temporary hostile combinations; unchanged 51-member canonical corpus',
                accuracy_warning='Native-equivalent temperatures; absolute physical accuracy not yet independently validated.')


def main():
    """Use the cached direct JVM classpath, optionally writing a durable new checkpoint."""
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java');parser.add_argument('--classpath');parser.add_argument('--report',type=Path);args=parser.parse_args()
    java=args.java or str(Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java'))
    classpath=args.classpath or (ROOT/'lmtr-contract/build/runtime-classpath.txt').read_text().strip()
    result=run(java,classpath)
    if args.report:args.report.write_text(json.dumps(result,sort_keys=True,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(case_count=result['case_count'],status='passed',manifest=result['fixture_manifest_sha256'])))

if __name__=='__main__':main()
