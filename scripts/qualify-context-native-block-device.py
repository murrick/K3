#!/usr/bin/env python3
"""Cold block cuts of checked topology publication and actual legacy Rule reindex."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[1]
sys.dont_write_bytecode=True
spec=importlib.util.spec_from_file_location('interrupt',ROOT/'scripts/qualify-context-interrupted-block-device.py')
interrupt=importlib.util.module_from_spec(spec);spec.loader.exec_module(interrupt)
block=interrupt.block


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workers',type=int,choices=range(1,5),default=2)
    args=parser.parse_args()
    manifest=json.loads((ROOT/'target/persistence-vm/manifest.json').read_text())
    for name,digest in manifest['sha256'].items():
        if hashlib.sha256((ROOT/name).read_bytes()).hexdigest()!=digest:raise ValueError('Prepared input changed: '+name)
    output=ROOT/'target/context-native-block';output.mkdir(parents=True,exist_ok=True)
    run=Path(tempfile.mkdtemp(prefix='run-',dir=str(output)))
    controls=block.controls(run);cut_controls=interrupt.fault_controls(run)
    append_regression={}
    for name,fault in [('ack',None),('cut',dict(command=1,ordinal=1,edge='after'))]:
        case=run/('append-'+name);case.mkdir();image=case/'stable.ext4'
        subprocess.run(['cp','--sparse=always',str(ROOT/manifest['template']),str(image)],check=True)
        write=block.boot(case,image,'write-interrupt',manifest,
            expected_marker='KANGER_BLOCK_NEXT_ACK revision=2 value=84',fault=fault)
        prefix='KANGER_BLOCK_PREVIOUS_RECOVERED revision=1 selected='
        read=block.boot(case,image,'read-interrupt',manifest,expected_marker=prefix,expected_prefix=True)
        serial=(case/'read-interrupt-serial.log').read_text();selected=int(re.search(re.escape(prefix)+r'(\d+)',serial)[1])
        if selected not in (1,2) or (fault is None and selected!=2):raise ValueError('Append helper regression failed')
        append_regression[name]=dict(write=write,cold_read=read,selected_revision=selected)
    print('APPEND_HELPER_REGRESSION_PASS',flush=True)
    operations={'topology':dict(previous=1,next=2),'reindex':dict(previous=3,next=4)}
    for operation in operations:(run/operation).mkdir()
    def one(operation,name,fault,initial_image=None,release_previous=True,checked=False):
        revisions=operations[operation];previous=revisions['previous'];next_revision=revisions['next']
        case=run/operation/name;case.mkdir();image=case/'stable.ext4'
        subprocess.run(['cp','--sparse=always',str(ROOT/manifest['template'] if initial_image is None else initial_image),str(image)],check=True)
        write_phase=('write-native-' if initial_image is None else ('write-resume-checked-native-' if checked else 'write-resume-native-'))+operation;read_phase='read-native-'+operation
        write=block.boot(case,image,write_phase,manifest,
            previous_marker='KANGER_BLOCK_PREVIOUS_ACK operation='+operation+' previous='+str(previous),
            expected_marker=('KANGER_BLOCK_NEXT_ACK operation='+operation+' revision='+str(next_revision)) if release_previous else ('KANGER_BLOCK_PREVIOUS_ACK operation='+operation+' previous='+str(previous)),
            fault=fault,release_previous=release_previous,timeout_seconds=600)
        prefix='KANGER_BLOCK_NATIVE_RECOVERED operation='+operation+' previous='+str(previous)+' selected='
        read=block.boot(case,image,read_phase,manifest,expected_marker=prefix,expected_prefix=True)
        match=re.search(re.escape(prefix)+r'(\d+)',(case/(read_phase+'-serial.log')).read_text())
        if not match:raise ValueError('Native recovery marker missing')
        selected=int(match[1])
        if selected not in (previous,next_revision):raise ValueError('Invalid recovered CURRENT')
        result=dict(write=write,cold_read=read,selected_revision=selected,previous_ack_and_history_preserved=True)
        (case/'result.json').write_text(json.dumps(result,indent=2)+'\n')
        return result
    def seed(operation):
        result=one(operation,'seed',None,release_previous=False)
        if result['selected_revision']!=operations[operation]['previous']:raise ValueError('Seed must retain previous publication')
        print('SEED_PASS',operation,flush=True)
        return operation,result
    with ThreadPoolExecutor(max_workers=min(2,args.workers)) as pool:seeds=dict(pool.map(seed,operations))
    def seed_image(operation):return run/operation/'seed/stable.ext4'
    def witness(operation):
        result=one(operation,'witness',None,initial_image=seed_image(operation))
        count=result['write']['post_previous_ack_stats']['flushes']
        if not 1<=count<=64:raise ValueError('Unexpected bounded native FLUSH count: '+str(count))
        if result['selected_revision']!=operations[operation]['next']:raise ValueError('Acknowledged native operation lost')
        print('WITNESS_PASS',operation,'flushes='+str(count),flush=True)
        return operation,dict(result=result,flushes=count)
    with ThreadPoolExecutor(max_workers=min(2,args.workers)) as pool:witnesses=dict(pool.map(witness,operations))
    checked_control=one('topology','checked-control',None,initial_image=seed_image('topology'),checked=True)
    if checked_control['selected_revision']!=2:raise ValueError('Checked full-entry control lost')
    print('CHECKED_TOPOLOGY_CONTROL_PASS',flush=True)
    specs=[]
    for operation in operations:
        for ordinal in range(1,witnesses[operation]['flushes']+1):
            for edge in ('before','after'):
                specs.append((operation,'f'+str(ordinal)+'-'+edge[0],dict(command=3,ordinal=ordinal,edge=edge)))
        specs.append((operation,'w1-a',dict(command=1,ordinal=1,edge='after')))
    def execute(spec):
        operation,name,fault=spec;result=one(operation,name,fault,initial_image=seed_image(operation))
        print('NATIVE_CUT_PASS',operation,name,'selected='+str(result['selected_revision']),flush=True)
        return operation,name,result
    cases={operation:{} for operation in operations}
    pool=ThreadPoolExecutor(max_workers=args.workers)
    try:
        for operation,name,result in pool.map(execute,specs):cases[operation][name]=result
    except BaseException:
        pool.shutdown(wait=True,cancel_futures=True)
        raise
    else:pool.shutdown(wait=True)
    files=[Path(__file__),ROOT/'scripts/qualify-context-interrupted-block-device.py',ROOT/'scripts/qualify-context-block-device.py',
           ROOT/'scripts/persistence_vm/volatile_nbd.py',ROOT/'scripts/persistence_vm/prepare.py',ROOT/'target/persistence-vm/manifest.json']
    files += [seed_image(operation) for operation in operations]
    files+=sorted(run.rglob('result.json'))
    files+=sorted(run.rglob('*.log'))+sorted(run.rglob('*.jsonl'))+sorted(run.rglob('*-command.json'))
    report=dict(scope='Finite FLUSH ordinal cuts of DB.saveConnections topology connect and Rule-v1 to Rule-v2 reindex; separate checked topology control, same device',
        head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
        source_dirty=bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True)),
        production_baseline='fb0171fa3ed2fdc520ca26f86089b1a88cc612dc',production_code_changed=False,
        prepared_inputs=manifest,workers=args.workers,wire_controls=controls,cut_controls=cut_controls,
        append_helper_regression=append_regression,
        operations=operations,seeds=seeds,witnesses=witnesses,checked_topology_control=checked_control,
        seed_strategy='Actual guest publication ACK, cut and cold-validated; stable image cloned per resumed operation; resume reconfirms previous frame/cursor before release',cases=cases,guest_provider_unmodified=True,
        all_previous_ack_and_history_preserved=True,full_operation_matrix=False,
        physical_device_power_loss_acceptance=False,
        limits='One device; actual kernel block traffic ordinal cuts, no torn sectors or cuts inside persistence; explicit saveConnections ingress; not the private-copy ordinary publication flow or general default activation',
        artifacts_sha256={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in files})
    (run/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    print('Report:',run/'report.json');print('NATIVE_BLOCK_RECOVERY_PASS cases='+str(len(specs)))


if __name__=='__main__':main()
