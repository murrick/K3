#!/usr/bin/env python3
"""Cold recovery of pins exported before producer ACK on distinct ext4 devices."""
from concurrent.futures import ThreadPoolExecutor
import hashlib,importlib.util,json,os,queue,subprocess,sys,tempfile,threading,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.dont_write_bytecode=True
spec=importlib.util.spec_from_file_location('block',ROOT/'scripts/qualify-context-block-device.py');block=importlib.util.module_from_spec(spec);spec.loader.exec_module(block)

def boot(run,images,action,mode,manifest,negative=None,stop_visible=False):
    phase=action+'-'+mode;servers={}
    for name in ['os','producer','consumer']:
        servers[name]=block.nbd.VolatileNbd(images[name],run/(name+'.sock'),run/(phase+'-'+name+'.jsonl'),honour_barriers=not(name=='producer' and negative=='lying' and action.startswith('write')))
    tools=ROOT/'target/persistence-vm/tools';env=os.environ.copy();env['LD_LIBRARY_PATH']=str(tools/'usr/lib/x86_64-linux-gnu');env['QEMU_MODULE_DIR']=str(tools/'usr/lib/x86_64-linux-gnu/qemu')
    command=[str(tools/'usr/bin/qemu-system-x86_64'),'-L',str(tools/'usr/share/qemu'),'-bios',str(tools/'usr/share/seabios/bios-256k.bin'),'-machine','pc,accel=tcg','-cpu','max','-smp','1','-m','768','-nodefaults','-display','none','-serial','stdio','-monitor','none','-nic','none','-no-reboot','-kernel',str(ROOT/manifest['kernel']),'-append','console=ttyS0 root=/dev/vda rw rootfstype=ext4 panic=-1 init=/sbin/init random.trust_cpu=on kanger.action='+action+' kanger.mode='+mode]
    for name in servers:command+=['-blockdev','driver=nbd,node-name='+name+',server.type=unix,server.path='+str(run/(name+'.sock')),'-device','virtio-blk-pci,drive='+name+',write-cache=on']
    (run/(phase+'-command.json')).write_text(json.dumps(command,indent=2)+'\n')
    p=subprocess.Popen(command,env=env,cwd=ROOT,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,errors='replace',bufsize=1)
    q=queue.Queue();log=run/(phase+'-serial.log')
    def consume():
        with log.open('w') as f:
            for line in p.stdout:f.write(line);f.flush();q.put(line.strip())
        q.put(None)
    reader=threading.Thread(target=consume,daemon=True);reader.start();visible_stats=None;observed=None
    wanted='PIN_CONSUMER_ACK' if action.startswith('write') else ('PIN_PRODUCER_ONLY_RECOVERED' if action=='read-producer' else ('PIN_COLD_MISSING' if action=='read-missing' else 'PIN_COLD_RECOVERED'))
    try:
        end=time.monotonic()+900
        while time.monotonic()<end:
            try:line=q.get(timeout=1)
            except queue.Empty:continue
            if line is None:break
            if line.startswith('PIN_VISIBLE mode='+mode+' '):
                if visible_stats is not None:raise ValueError('Duplicate visibility signal')
                visible_stats={name:dict(server.stats) for name,server in servers.items()}
                if stop_visible:observed=line;break
                p.stdin.write('\n');p.stdin.flush()
            if line.startswith(wanted+' mode='+mode+' '):observed=line;break
            if 'KANGER_GUEST_FAILED' in line or 'Kernel panic' in line:break
    finally:
        for server in servers.values():server.arm_cut()
        if p.poll() is None:p.kill()
        p.wait(timeout=20);reader.join(timeout=10);stats={name:server.stop() for name,server in servers.items()}
    failure=None if observed is not None else 'Guest failed before '+wanted+'; inspect '+str(log)
    if action.startswith('write') and visible_stats is None:raise ValueError('Producer must expose an actual marker before consumer ACK')
    text=log.read_text();recovery={dev:'EXT4-fs ('+dev+'): recovery complete' in text for dev in ['vda','vdb','vdc']}
    if action.startswith('read') and not recovery['vdb'] and negative!='lying':raise ValueError('Producer journal recovery not observed')
    if action in ('read','read-missing') and not recovery['vdc']:raise ValueError('Consumer journal recovery not observed')
    delta=None if visible_stats is None else {name:{key:stats[name][key]-visible_stats[name][key] for key in stats[name]} for name in stats}
    return dict(marker=observed,failure=failure,exit_code=p.returncode,block_stats=stats,visible_stats=visible_stats,post_visible_stats=delta,journal_recovery=recovery,honour_barriers={name:server.honour_barriers for name,server in servers.items()},consumer_provider_unmodified=action!='write-negative',negative=negative,producer_only_cut=stop_visible)

def main():
    manifest=json.loads((ROOT/'target/persistence-pin-vm/manifest.json').read_text())
    for name,digest in manifest['sha256'].items():
        if hashlib.sha256((ROOT/name).read_bytes()).hexdigest()!=digest:raise ValueError('Prepared input changed: '+name)
    output=ROOT/'target/context-device-pins';output.mkdir(exist_ok=True);run=Path(tempfile.mkdtemp(prefix='run-',dir=output))
    controls=block.controls(run)
    def one(mode,kind):
        case=run/(mode+'-'+kind);case.mkdir();images={}
        for name in ['os','producer','consumer']:
            images[name]=case/(name+'.ext4');source=manifest['os_template'] if name=='os' else manifest['data_templates'][name]
            subprocess.run(['cp','--sparse=always',str(ROOT/source),str(images[name])],check=True)
        negative=kind if kind in ('bypass','lying') else None;window=kind=='window'
        write=boot(case,images,'write-negative' if kind=='bypass' else 'write',mode,manifest,negative,stop_visible=window)
        if write['failure']:raise RuntimeError(write['failure'])
        read=boot(case,images,'read-producer' if window else ('read-missing' if negative else 'read'),mode,manifest,negative)
        if not window and not negative and write['post_visible_stats']['producer']['flushes']+write['post_visible_stats']['producer']['fua']==0:raise ValueError('Consumer export caused no producer barrier')
        if kind=='bypass' and write['post_visible_stats']['producer']['flushes']+write['post_visible_stats']['producer']['fua']!=0:raise ValueError('Negative control producer was accidentally stabilized')
        result=dict(write=write,cold_read=read,passed=read['failure'] is None);(case/'result.json').write_text(json.dumps(result,indent=2)+'\n')
        print('DEVICE_PIN_PASS' if result['passed'] else 'DEVICE_PIN_OBSERVATION',mode,kind,read['marker'] or read['failure'],flush=True);return mode+'-'+kind,result
    first=one('append','export');results={first[0]:first[1]}
    specs=[(mode,kind) for mode in ['append','reindex','create','fork'] for kind in ['export','window'] if (mode,kind)!=('append','export')]+[('append','bypass'),('append','lying')]
    with ThreadPoolExecutor(max_workers=2) as pool:
        for key,result in pool.map(lambda spec:one(*spec),specs):results[key]=result
    files=[Path(__file__),ROOT/'scripts/persistence_vm/prepare_pins.py',ROOT/'target/persistence-pin-vm/manifest.json',ROOT/'scripts/persistence_vm/volatile_nbd.py',ROOT/'scripts/qualify-context-block-device.py']+list(run.rglob('*.log'))+list(run.rglob('*.jsonl'))+list(run.rglob('*-command.json'))+list(run.rglob('result.json'))
    report=dict(scope='Four marker-visible producer windows and exact direct-pin export on distinct guest devices; two sensitivity controls',head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),production_baseline='fb0171fa3ed2fdc520ca26f86089b1a88cc612dc',production_code_changed=False,prepared_inputs=manifest,wire_controls=controls,cases=results,device_isolation='Separate NBD stable files and volatile caches for OS, producer and consumer; ext4 commit=3600 only in guest',producer_pause_overlay=True,positive_consumers_unmodified=True,full_operation_matrix=False,physical_device_power_loss_acceptance=False,artifacts_sha256={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in files})
    report['passed']=all(result['passed'] for result in results.values())
    (run/'report.json').write_text(json.dumps(report,indent=2)+'\n');print('Report:',run/'report.json');print(('DEVICE_PIN_COLD_RECOVERY_PASS' if report['passed'] else 'DEVICE_PIN_COLD_RECOVERY_INCOMPLETE')+' cases='+str(len(results)))
    if not report['passed']:raise SystemExit(1)
if __name__=='__main__':main()
