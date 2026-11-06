"""One entrypoint for related checks, including the production JVM download adapter."""
import argparse, os, subprocess, sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--cache',type=Path,required=True);args=p.parse_args()
cache=args.cache.resolve();evidence=root/'evidence';evidence.mkdir(exist_ok=True)
def run(name,command,env=None):
    with (evidence/(name+'.log')).open('w') as log:
        result=subprocess.run(command,cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT)
    print(name+': '+('PASS' if result.returncode==0 else 'FAIL'),flush=True)
    if result.returncode:raise SystemExit('Check failed; inspect evidence/'+name+'.log')
run('typecheck',['node',str(root/'node_modules/typescript/bin/tsc'),'--noEmit'])
run('native-build',[sys.executable,str(root/'scripts/build_native.py'),'--cache',str(cache),'--test'])
env=dict(os.environ);env['MOVNESS_NATIVE_TEST_CLASSPATH']=(cache/'runtime-classpath.txt').read_text()
run('tests',['node','--import',str(root/'node_modules/tsx/dist/loader.mjs'),'--test',*(str(p) for p in sorted((root/'test').glob('*.test.ts')))],env)
run('build',['node',str(root/'scripts/build.mjs')])
