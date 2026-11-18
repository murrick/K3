#!/usr/bin/env python3
"""Cold ext4 recovery in an owned VM over a flush-aware volatile NBD model."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import queue
import shutil
import socket
import struct
import subprocess
import sys
import tempfile
import threading
import time

ROOT=Path(__file__).resolve().parents[1]
sys.dont_write_bytecode=True
spec=importlib.util.spec_from_file_location('volatile_nbd',ROOT/'scripts/persistence_vm/volatile_nbd.py')
nbd=importlib.util.module_from_spec(spec);spec.loader.exec_module(nbd)


class Client:
    def __init__(self,path):
        self.sock=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM);self.sock.connect(str(path));self.counter=0
        header=nbd.receive(self.sock,152)
        assert header[:8]==b'NBDMAGIC' and struct.unpack('>I',header[24:28])[0]==13
    def command(self,kind,offset=0,length=0,payload=b'',flags=0,error=False):
        self.counter+=1;cookie=struct.pack('>Q',self.counter)
        self.sock.sendall(struct.pack('>IHH8sQI',nbd.REQUEST,flags,kind,cookie,offset,length)+payload)
        magic,code,returned=struct.unpack('>II8s',nbd.receive(self.sock,16))
        assert magic==nbd.REPLY and returned==cookie
        if error:assert code!=0;return
        assert code==0,code
        return nbd.receive(self.sock,length) if kind==0 else None
    def cut(self,server):server.arm_cut();self.sock.close();return server.stop()


def controls(run):
    image=run/'control.img';image.write_bytes(bytes(4096));sock=run/'control.sock'
    def start(name):
        server=nbd.VolatileNbd(image,sock,run/(name+'.jsonl'));return server,Client(sock)
    server,c=start('unflushed');c.command(1,0,512,b'A'*512);assert c.command(0,0,512)==b'A'*512
    assert c.cut(server)['dropped_sectors']==1;assert image.read_bytes()[:512]==bytes(512)
    server,c=start('fua');c.command(1,0,512,b'A'*512);c.command(1,512,512,b'B'*512,flags=1)
    assert c.cut(server)['dropped_sectors']==1
    assert image.read_bytes()[:1024]==bytes(512)+b'B'*512
    server,c=start('flush');c.command(1,0,512,b'A'*512);c.command(3);c.command(1,1024,512,b'C'*512)
    c.command(0,4096,512,error=True)
    assert c.cut(server)['dropped_sectors']==1
    assert image.read_bytes()[:1536]==b'A'*512+b'B'*512+bytes(512)
    server,c=start('cut-freeze');c.command(1,1536,512,b'D'*512);server.arm_cut()
    try:
        c.command(3)
        raise AssertionError('A post-cut flush must never be acknowledged')
    except (EOFError,BrokenPipeError,ConnectionResetError):pass
    c.sock.close();assert server.stop()['dropped_sectors']==1
    assert image.read_bytes()[1536:2048]==bytes(512)
    return dict(cut_freezes_new_commands=True,unflushed_ack_is_lost=True,fua_does_not_flush_unrelated_writes=True,
                flush_covers_completed_prefix=True,later_writes_are_lost=True,out_of_range_refused=True)


def boot(run,image,phase,manifest,honour_barriers=True,expect_missing=False,expected_marker=None,fault=None,expected_prefix=False,previous_marker='KANGER_BLOCK_PREVIOUS_ACK revision=1 value=42',release_previous=True,timeout_seconds=240):
    base=ROOT/'target/persistence-vm';tools=base/'tools';sock=run/'b.sock'
    server=nbd.VolatileNbd(image,sock,run/(phase+'-block.jsonl'),honour_barriers=honour_barriers)
    env=os.environ.copy();env['LD_LIBRARY_PATH']=str(tools/'usr/lib/x86_64-linux-gnu');env['QEMU_MODULE_DIR']=str(tools/'usr/lib/x86_64-linux-gnu/qemu')
    command=[str(tools/'usr/bin/qemu-system-x86_64'),'-L',str(tools/'usr/share/qemu'),
             '-bios',str(tools/'usr/share/seabios/bios-256k.bin'),'-machine','pc,accel=tcg','-cpu','max','-smp','1','-m','768','-nodefaults',
             '-display','none','-serial','stdio','-monitor','none','-nic','none','-no-reboot',
             '-kernel',str(ROOT/manifest['kernel']),'-append',
             'console=ttyS0 root=/dev/vda rw rootfstype=ext4 panic=-1 init=/sbin/init random.trust_cpu=on kanger.phase='+phase,
             '-blockdev','driver=nbd,node-name=os,server.type=unix,server.path='+str(sock),
             '-device','virtio-blk-pci,drive=os,write-cache=on']
    (run/(phase+'-command.json')).write_text(json.dumps(command,indent=2)+'\n')
    process=subprocess.Popen(command,cwd=ROOT,env=env,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,errors='replace',bufsize=1)
    lines=queue.Queue();log_path=run/(phase+'-serial.log')
    def consume():
        with log_path.open('w') as log:
            for line in process.stdout:log.write(line);log.flush();lines.put(line.strip())
        lines.put(None)
    reader=threading.Thread(target=consume,daemon=True);reader.start()
    expected='KANGER_BLOCK_ACK revision=1 value=42' if phase=='write' else 'KANGER_BLOCK_RECOVERED revision=1 value=42'
    if expect_missing:expected='KANGER_BLOCK_MISSING revision=1 code=STORAGE_NOT_FOUND'
    if expected_marker is not None:expected=expected_marker
    observed=False;previous_ack=None;deadline=time.monotonic()+timeout_seconds
    try:
        while time.monotonic()<deadline:
            if fault is not None and server.fault_seen.is_set():observed=True;break
            try:line=lines.get(timeout=1)
            except queue.Empty:continue
            if line is None:break
            if line==previous_marker:
                if previous_ack is not None:raise RuntimeError('Duplicate previous ACK')
                previous_ack=dict(server.stats)
                if not release_previous:observed=True;break
                if fault is not None:server.arm_fault(**fault)
                process.stdin.write('\n');process.stdin.flush()
            if line==expected or (expected_prefix and line.startswith(expected)):observed=True;break
            if 'KANGER_GUEST_FAILED' in line or 'Kernel panic' in line:break
    finally:
        server.arm_cut()
        if process.poll() is None:process.kill()
        process.wait(timeout=20);reader.join(timeout=10);stats=server.stop()
    if fault is not None:
        observed=server.fault_seen.is_set() and previous_ack is not None
        if 'KANGER_BLOCK_NEXT_ACK' in log_path.read_text():raise RuntimeError('Cut followed the next application ACK')
    if not observed:raise RuntimeError('Guest did not reach '+expected+'; inspect '+str(log_path))
    if phase.startswith('write') and stats['flushes']+stats['fua']==0:raise RuntimeError('No stable barrier reached NBD backend')
    if phase.startswith('write') and stats['writes']<=stats['fua']:raise RuntimeError('VM did not exercise cacheable writes')
    recovery_seen='EXT4-fs (vda): recovery complete' in log_path.read_text()
    if phase.startswith('read') and not expect_missing and not recovery_seen:raise RuntimeError('Cold journal recovery was not observed')
    return dict(expected_marker=expected if fault is None else None,normal_completion_marker=expected,
                observed_event=('application_marker' if release_previous else 'previous_publication_ack') if fault is None else 'block_cut',
                next_application_ack_observed='KANGER_BLOCK_NEXT_ACK' in log_path.read_text(),
                observed=True,exit_code=process.returncode,block_stats=stats,
                honour_barriers=honour_barriers,journal_recovery_seen=recovery_seen,
                previous_ack_stats=previous_ack,cut=fault,cut_reached=server.fault_seen.is_set(),
                post_previous_ack_stats=None if previous_ack is None else {k:stats[k]-previous_ack[k] for k in stats})


def main():
    base=ROOT/'target/persistence-vm';manifest=json.loads((base/'manifest.json').read_text())
    for name,digest in manifest['sha256'].items():
        if hashlib.sha256((ROOT/name).read_bytes()).hexdigest()!=digest:raise ValueError('Prepared input changed: '+name)
    output=ROOT/'target/context-block-device';output.mkdir(parents=True,exist_ok=True)
    run=Path(tempfile.mkdtemp(prefix='run-',dir=str(output)))
    self_controls=controls(run)
    image=run/'stable.ext4'
    subprocess.run(['cp','--sparse=always',str(ROOT/manifest['template']),str(image)],check=True)
    write=boot(run,image,'write',manifest)
    read=boot(run,image,'read',manifest)
    negative=run/'negative';negative.mkdir()
    bad_image=negative/'lying.ext4'
    subprocess.run(['cp','--sparse=always',str(ROOT/manifest['template']),str(bad_image)],check=True)
    bad_write=boot(negative,bad_image,'write',manifest,honour_barriers=False)
    bad_read=boot(negative,bad_image,'read',manifest,expect_missing=True)
    if bad_write['block_stats']['dropped_sectors']==0:raise RuntimeError('Negative control dropped no writes')
    matrix={}
    for operation,revision in [('create',0),('append2',2),('fork',1)]:
        case=run/operation;case.mkdir();case_image=case/'stable.ext4'
        subprocess.run(['cp','--sparse=always',str(ROOT/manifest['template']),str(case_image)],check=True)
        result={}
        for phase,marker in [('write','ACK'),('read','RECOVERED')]:
            result[phase]=boot(case,case_image,phase+'-'+operation,manifest,
                expected_marker='KANGER_BLOCK_'+marker+' operation='+operation+' revision='+str(revision))
        matrix[operation]=result
    files=[ROOT/'target/persistence-vm/manifest.json',Path(__file__),ROOT/'scripts/persistence_vm/volatile_nbd.py',ROOT/'scripts/persistence_vm/prepare.py']
    files += sorted(run.rglob('*.log'))+sorted(run.rglob('*.jsonl'))+sorted(run.rglob('*-command.json'))
    report=dict(scope='Acknowledged create, append, retained R1 after R2, and fork cold recovery under a flush-honouring block model',
                head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),source_dirty=True,
                qemu_version=manifest['qemu_version'],self_controls=self_controls,write=write,cold_read=read,
                lying_device_control=dict(write=bad_write,cold_read=bad_read,acknowledged_value_lost=True),
                acknowledged_operation_matrix=matrix,guest_provider_unmodified=True,block_model_qualification_passed=True,full_operation_matrix=False,
                physical_device_power_loss_acceptance=False,
                artifacts_sha256={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in files})
    (run/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    print('Report:',run/'report.json');print('BLOCK_DEVICE_COLD_RECOVERY_PASS value=42 revision=1')


if __name__=='__main__':main()
