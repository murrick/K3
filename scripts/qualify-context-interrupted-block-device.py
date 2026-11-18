#!/usr/bin/env python3
"""Finite cuts around every observed FLUSH of R2; preserve the previous actual R1 ACK."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[1]
sys.dont_write_bytecode=True
spec=importlib.util.spec_from_file_location('block_harness',ROOT/'scripts/qualify-context-block-device.py')
block=importlib.util.module_from_spec(spec);spec.loader.exec_module(block)


def fault_controls(run):
    results={}
    for command,edge in [(3,'before'),(3,'after'),(1,'after')]:
        name=str(command)+'-'+edge;image=run/(name+'.img');image.write_bytes(b'A'*4096)
        server=block.nbd.VolatileNbd(image,run/(name+'.sock'),run/(name+'.jsonl'))
        client=block.Client(run/(name+'.sock'))
        if command==3:client.command(1,0,512,b'B'*512)
        server.arm_fault(command,1,edge)
        try:
            client.command(command,0,512,b'B'*512) if command==1 else client.command(command)
            raise AssertionError('A cut command must not be acknowledged')
        except (EOFError,BrokenPipeError,ConnectionResetError):pass
        assert server.fault_seen.is_set()
        client.sock.close();stats=server.stop()
        persisted=command==3 and edge=='after'
        assert image.read_bytes()[:512]==(b'B' if persisted else b'A')*512
        assert stats['dropped_sectors']==(0 if persisted else 1)
        results[name]=True
    return results


def main():
    manifest=json.loads((ROOT/'target/persistence-vm/manifest.json').read_text())
    for name,digest in manifest['sha256'].items():
        if hashlib.sha256((ROOT/name).read_bytes()).hexdigest()!=digest:raise ValueError('Prepared input changed: '+name)
    output=ROOT/'target/context-interrupted-block-device';output.mkdir(parents=True,exist_ok=True)
    run=Path(tempfile.mkdtemp(prefix='run-',dir=str(output)))
    controls=block.controls(run);cut_controls=fault_controls(run)
    def one(name,fault):
        case=run/name;case.mkdir();image=case/'stable.ext4'
        subprocess.run(['cp','--sparse=always',str(ROOT/manifest['template']),str(image)],check=True)
        write=block.boot(case,image,'write-interrupt',manifest,
            expected_marker='KANGER_BLOCK_NEXT_ACK revision=2 value=84',fault=fault)
        read=block.boot(case,image,'read-interrupt',manifest,
            expected_marker='KANGER_BLOCK_PREVIOUS_RECOVERED revision=1 selected=',expected_prefix=True)
        serial=(case/'read-interrupt-serial.log').read_text()
        selected=int(serial.split('KANGER_BLOCK_PREVIOUS_RECOVERED revision=1 selected=')[1].splitlines()[0])
        return dict(write=write,cold_read=read,selected_revision=selected,previous_ack_preserved=True)
    witness=one('ack-witness',None)
    flushes=witness['write']['post_previous_ack_stats']['flushes']
    if not 1<=flushes<=32:raise ValueError('Unexpected bounded R2 FLUSH count: '+str(flushes))
    if witness['selected_revision']!=2:raise ValueError('ACKed R2 control must recover R2')
    specs=[('flush-'+str(n)+'-'+edge,dict(command=3,ordinal=n,edge=edge))
           for n in range(1,flushes+1) for edge in ('before','after')]
    specs.append(('write-1-after',dict(command=1,ordinal=1,edge='after')))
    def execute(spec):
        name,fault=spec;result=one(name,fault)
        print('CUT_PASS',name,'selected='+str(result['selected_revision']),flush=True)
        return name,result
    with ThreadPoolExecutor(max_workers=2) as pool:cases=dict(pool.map(execute,specs))
    files=[Path(__file__),ROOT/'scripts/qualify-context-block-device.py',ROOT/'scripts/persistence_vm/volatile_nbd.py',
           ROOT/'scripts/persistence_vm/prepare.py',ROOT/'target/persistence-vm/manifest.json']
    files+=sorted(run.rglob('*.log'))+sorted(run.rglob('*.jsonl'))+sorted(run.rglob('*-command.json'))
    report=dict(scope='All observed R2 FLUSH ordinal boundaries after an actual R1 ACK; one single-device append fixture',
        head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
        source_dirty=bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True)),
        production_baseline='fb0171fa3ed2fdc520ca26f86089b1a88cc612dc',production_code_changed=False,
        prepared_inputs=manifest,wire_controls=controls,cut_controls=cut_controls,witness=witness,
        witness_r2_flush_count=flushes,cases=cases,guest_provider_unmodified=True,
        previous_ack_preserved_in_all_cases=True,full_operation_matrix=False,
        physical_device_power_loss_acceptance=False,
        limits='Serial untorn sector model; no cuts inside a persistence request; ordinal counts may vary across runs; not all Java publication boundaries or devices',
        artifacts_sha256={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in files})
    (run/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    print('Report:',run/'report.json');print('INTERRUPTED_BLOCK_RECOVERY_PASS cases='+str(len(cases)))


if __name__=='__main__':main()
