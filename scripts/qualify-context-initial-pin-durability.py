#!/usr/bin/env python3
"""Two JVMs: failed initial creation must not delete an already exported exact pin. No crash model."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile
ROOT = Path(__file__).resolve().parents[1]
P = 'org.kanger.storage.dumb2.'


def main():
    output = ROOT / 'target/context-initial-pin-durability'
    output.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix='run-', dir=str(output)))
    classes, overlay = run / 'classes', run / 'producer-overlay'
    classes.mkdir(); overlay.mkdir()
    jar = ROOT / 'kanger-console/target/kanger-console-3.3-SNAPSHOT.jar'
    env = os.environ.copy()
    for name in ('KANGER_OPTIONS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(name, None)
    env['KANGER_HOME'] = 'KANGER'
    sources = [ROOT / 'kanger-qualification/src' / ((P+n).replace('.', '/')+'.java') for n in ('InitialPinPause', 'InitialPinDurabilityRunner')]
    args = ['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-encoding', 'UTF-8']
    result = subprocess.run(args+['-cp', str(jar), '-d', str(classes)]+list(map(str,sources)), cwd=ROOT, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (run/'compile.log').write_text(result.stdout)
    if result.returncode:
        print(result.stdout); return result.returncode
    original = ROOT/'kanger-data-dumb2/src/org/kanger/storage/dumb2/RevisionStore.java'
    source = original.read_text(); boundary = '\n            confirmDurable(path);\n'
    if source.count(boundary)!=1:
        raise ValueError('Initial marker confirmation boundary changed')
    injected = run/'RevisionStore.java'
    injected.write_text(source.replace(boundary,'\n            InitialPinPause.beforeConfirmation(path);'+boundary))
    cp = os.pathsep.join((str(classes),str(jar)))
    result = subprocess.run(args+['-cp',cp,'-d',str(overlay),str(injected)],cwd=ROOT,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    (run/'overlay-compile.log').write_text(result.stdout)
    if result.returncode:
        print(result.stdout); return result.returncode
    observations = []
    logs = []
    for mode, pin in [('create', 0), ('fork', 1)]:
        result = subprocess.run(['java','-XX:-UsePerfData','-cp',cp,P+'InitialPinDurabilityRunner','coordinate',str(run/mode),os.pathsep.join((str(overlay),cp)),mode],cwd=ROOT,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=150)
        log=run/(mode+'.log');log.write_text(result.stdout);logs += [log,run/mode/'producer.log']
        match=re.search(r'INITIAL_EXPORTED_PIN consumer=2 pinned='+str(pin)+r' readable=(true|false)',result.stdout)
        if not match or result.returncode not in (0,1):
            print(result.stdout); return result.returncode or 2
        observations.append(dict(operation=mode,consumer_revision=2,pinned_revision=pin,exact_pin_readable=match[1]=='true',passed=result.returncode==0 and match[1]=='true'))
    passed=all(o['passed'] for o in observations)
    with zipfile.ZipFile(jar) as z:
        names=[n for n in z.namelist() if n.startswith('org/kanger/storage/dumb2/') and n.endswith('.class')]
        if not all(z.read(n)==(ROOT/'kanger-data-dumb2/target/classes'/n).read_bytes() for n in names):
            raise ValueError('Consumer packaged classes differ from built provider')
    files=[jar,original,injected,Path(__file__),*sources,*logs]
    report=dict(scope='Two JVMs, living filesystem, no power-cut projection',head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
                source_dirty=True,matching_provider_classes=len(names),cases=observations,initial_export_gate_passed=passed,full_power_loss_acceptance=False,
                producer_overlay='Pause then return IO error before initial confirmation; consumer uses unmodified packaged provider',
                artifacts_sha256={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in files})
    (run/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    print('Report:',run/'report.json');print('INITIAL_EXPORTED_PIN_'+('PASS' if passed else 'RED'));print(json.dumps(observations))
    return 0 if passed else 1


if __name__=='__main__':
    raise SystemExit(main())
