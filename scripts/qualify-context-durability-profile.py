#!/usr/bin/env python3
"""Actual volatile storage refusal and local-profile controls; never hardware power-loss acceptance."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile
ROOT=Path(__file__).resolve().parents[1]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--volatile-root',type=Path,default=Path('/dev/shm'))
    args=parser.parse_args()
    output=ROOT/'target/context-durability-profile';output.mkdir(parents=True,exist_ok=True)
    run=Path(tempfile.mkdtemp(prefix='run-',dir=str(output)));classes=run/'classes';classes.mkdir()
    jar=ROOT/'kanger-console/target/kanger-console-3.3-SNAPSHOT.jar'
    sources=[ROOT/'kanger-qualification/src/org/kanger/storage/dumb2'/n for n in ('ContextVolatileProfileRunner.java','ContextPublicationDurabilityTraceRunner.java')]
    env=os.environ.copy()
    for name in ('KANGER_OPTIONS','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS'):env.pop(name,None)
    env['KANGER_HOME']='KANGER'
    command=['javac','-source','8','-target','8','-Xlint:-options','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),*map(str,sources)]
    compiled=subprocess.run(command,cwd=ROOT,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    (run/'compile.log').write_text(compiled.stdout)
    if compiled.returncode:print(compiled.stdout);return compiled.returncode
    cp=os.pathsep.join((str(classes),str(jar)))
    result=subprocess.run(['java','-XX:-UsePerfData','-cp',cp,'org.kanger.storage.dumb2.ContextVolatileProfileRunner',str(args.volatile_root),str(run/'fixture')],cwd=ROOT,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=120)
    (run/'fixture.log').write_text(result.stdout)
    match=re.search(r'VOLATILE_PROFILE filesystem=tmpfs acknowledged=(true|false) typed_refusal=(true|false)',result.stdout)
    if not match or result.returncode not in (0,1):print(result.stdout);return result.returncode or 2
    passed=result.returncode==0 and match[1]=='false' and match[2]=='true' and 'VOLATILE_LEGACY_CHECKS_PASS' in result.stdout
    with zipfile.ZipFile(jar) as packaged:
        names=[n for n in packaged.namelist() if n.startswith('org/kanger/storage/dumb2/') and n.endswith('.class')]
        names+=['org/kanger/enums/StorageLifecycleErrorCode.class']
        for name in names:
            module='kanger-core' if name.startswith('org/kanger/enums/') else 'kanger-data-dumb2'
            if packaged.read(name)!=(ROOT/module/'target/classes'/name).read_bytes():raise ValueError('Packaged class differs: '+name)
    device_observation={'available':False,'power_loss_attestation':False}
    try:
        device_id=subprocess.check_output(['findmnt','-n','-T',str(run/'fixture'),'-o','MAJ:MIN'],text=True).strip()
        if not re.fullmatch(r'[0-9]+:[0-9]+',device_id):raise ValueError('Invalid device identity')
        device_path=(Path('/sys/dev/block')/device_id).resolve(strict=True)
        name=device_path.name
        settings=Path('/proc/fs/ext4')/name/'options'
        cache=device_path/'queue/write_cache'
        if not cache.exists():cache=device_path.parent/'queue/write_cache'
        device_observation=dict(available=True,major_minor=device_id,device=name,active_ext4_options=settings.read_text().splitlines(),
                                kernel_write_cache=cache.read_text().strip() if cache.exists() else 'unavailable',power_loss_attestation=False)
    except (OSError,ValueError,subprocess.CalledProcessError):
        pass
    files=[jar,Path(__file__),*sources,run/'fixture.log']
    files += [ROOT/'kanger-data-dumb2/src/org/kanger/storage/dumb2'/n for n in ('DurabilityProfile.java','DurableFiles.java','RevisionStore.java','ContextStore.java')]
    report=dict(scope='Actual tmpfs refusal; supported local controls; no remount or reboot',head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),source_dirty=True,
                filesystem='tmpfs',acknowledged=match[1]=='true',typed_refusal=match[2]=='true',legacy_controls='VOLATILE_LEGACY_CHECKS_PASS' in result.stdout,
                eligible_profile='linux-ext4-ordered-barriers-v1',device_observation=device_observation,
                java_version=subprocess.run(['java','-version'],env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True).stdout.strip(),matching_provider_classes=len(names)-1,matching_core_error_enum=True,
                necessary_profile_gate_passed=passed,full_power_loss_acceptance=False,
                artifacts_sha256={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in files})
    (run/'report.json').write_text(json.dumps(report,indent=2)+'\n');print('Report:',run/'report.json');print('DURABILITY_PROFILE_'+('PASS' if passed else 'RED'));print(result.stdout,end='')
    return 0 if passed else 1


if __name__=='__main__':raise SystemExit(main())
