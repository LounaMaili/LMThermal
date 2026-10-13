"""Command-line LMTR exact JSON, writer and immutable reader diagnostics."""
import argparse
from decimal import Decimal
import json
from pathlib import Path
import sys
import struct
from .container import Reader, write_plan
from .jsoncodec import ContractError, dumps, loads

def main():
    parser=argparse.ArgumentParser(description=__doc__); sub=parser.add_subparsers(dest='command',required=True)
    canon=sub.add_parser('canon'); canon.add_argument('input')
    check=sub.add_parser('check'); check.add_argument('file'); check.add_argument('--recover',action='store_true'); check.add_argument('--full',action='store_true'); check.add_argument('--entry',type=int)
    write=sub.add_parser('write-plan'); write.add_argument('plan'); write.add_argument('output')
    args=parser.parse_args()
    try:
        if args.command=='canon':
            sys.stdout.buffer.write(dumps(loads(Path(args.input).read_bytes()))); return 0
        if args.command=='write-plan':
            # Plan transport is not LMTR wire JSON; arbitrary base64 strings do not relax record bounds.
            plan=json.loads(Path(args.plan).read_text(),parse_float=Decimal)
            result=write_plan(plan,args.output)
        else:
            with Reader(args.file) as reader: result=reader.check(args.full,args.entry,args.recover)
    except ContractError as error:
        result={'status':'error','error':error.code,'detail':error.detail,'integrity_scope':'none'}
        print(json.dumps(result,sort_keys=True)); return 2
    except (OSError,ValueError,KeyError,struct.error) as error:
        result={'status':'error','error':'invalid_plan_or_io' if args.command=='write-plan' else 'invalid_container','detail':str(error),'integrity_scope':'none'}
        print(json.dumps(result,sort_keys=True)); return 2
    print(json.dumps(result,sort_keys=True)); return 0

if __name__=='__main__': raise SystemExit(main())
