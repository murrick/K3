#!/usr/bin/env python3
"""Separate-device pin guest; reuse verified tools, never mount an image on the host."""
import hashlib,json,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'target/persistence-pin-vm'
def call(args):subprocess.run(list(map(str,args)),check=True)
def main():
    original=ROOT/'target/persistence-vm';m=json.loads((original/'manifest.json').read_text())
    if m.get('java_profile')!='baseline':raise ValueError('Baseline guest required')
    jar=ROOT/'kanger-console/target/kanger-console-3.3-SNAPSHOT.jar'
    if hashlib.sha256(jar.read_bytes()).hexdigest()!=m['sha256'][str(jar.relative_to(ROOT))]:raise ValueError('Provider changed')
    BASE.mkdir(exist_ok=True);root=BASE/'rootfs'
    if root.exists():shutil.rmtree(root)
    shutil.copytree(original/'rootfs',root,symlinks=True)
    for name in ['producer','consumer']:(root/name).mkdir(exist_ok=True)
    sources=[ROOT/'kanger-qualification/src/org/kanger/storage/dumb2'/name for name in ['ContextDevicePinRunner.java','DevicePinPause.java']]
    call(['javac','-source','8','-target','8','-Xlint:-options','-encoding','UTF-8','-cp',jar,'-d',root/'app/classes',*sources])
    revision=ROOT/'kanger-data-dumb2/src/org/kanger/storage/dumb2/RevisionStore.java';text=revision.read_text()
    points=['            // A visible next marker is not yet a durable acknowledgement.','            temporaryCreated = false;\n            confirmDurable(path);']
    for point in points:
        if text.count(point)!=1:raise ValueError('Exact publication boundary changed')
    text=text.replace(points[0],'            DevicePinPause.visible(path);\n'+points[0]).replace(points[1],'            temporaryCreated = false;\n            DevicePinPause.visible(path);\n            confirmDurable(path);')
    overlay=BASE/'RevisionStore.java';overlay.write_text(text)
    call(['javac','-source','8','-target','8','-Xlint:-options','-cp',str(root/'app/classes')+':'+str(jar),'-d',root/'app/producer-overlay',overlay])
    expected={'org/kanger/storage/dumb2/'+n+'.class' for n in ['ContextBlockDeviceRunner','ContextDevicePinRunner','DevicePinPause']}
    if {str(p.relative_to(root/'app/classes')) for p in (root/'app/classes').rglob('*.class')}!=expected:raise ValueError('Unexpected consumer override')
    if [p.name for p in (root/'app/producer-overlay').rglob('*.class')]!=['RevisionStore.class']:raise ValueError('Unexpected producer overlay')
    negative=BASE/'DurablePins.java'
    negative.write_text('package org.kanger.storage.dumb2; final class DurablePins { private DurablePins(){} static void stabilize(ConnectionVector dependencies)throws Exception{} }\n')
    call(['javac','-source','8','-target','8','-Xlint:-options','-cp',str(jar),'-d',root/'app/consumer-negative-overlay',negative])
    if [p.name for p in (root/'app/consumer-negative-overlay').rglob('*.class')]!=['DurablePins.class']:raise ValueError('Unexpected negative overlay')
    init=root/'sbin/init';init.write_text('''#!/bin/sh
/bin/mount -t proc proc /proc
/bin/mount -t sysfs sysfs /sys
/bin/mount -t devtmpfs devtmpfs /dev 2>/dev/null || true
while [ ! -b /dev/vdc ]; do /bin/sleep 1; done
/bin/mount -t ext4 -o data=ordered,barrier=1,commit=3600 /dev/vdb /producer || exit 1
/bin/mount -t ext4 -o data=ordered,barrier=1,commit=3600 /dev/vdc /consumer || exit 1
action=read
mode=append
for option in $(/bin/cat /proc/cmdline); do
 case "$option" in kanger.action=*) action=${option#kanger.action=};; kanger.mode=*) mode=${option#kanger.mode=};; esac
done
export KANGER_HOME=KANGER
cp=/app/classes:/app/kanger.jar
if [ "$action" = write-negative ]; then cp=/app/consumer-negative-overlay:$cp; fi
/opt/java/bin/java -XX:-UsePerfData -XX:ActiveProcessorCount=1 -Xmx128m -Djava.security.egd=file:/dev/urandom -cp "$cp" org.kanger.storage.dumb2.ContextDevicePinRunner "$action" "$mode"
echo KANGER_GUEST_FAILED
while true; do /bin/sleep 1000; done
''');init.chmod(0o755)
    os_image=BASE/'template.ext4'
    with os_image.open('wb') as f:f.truncate(512*1024*1024)
    call(['mkfs.ext4','-q','-F','-m','0','-d',root,os_image])
    data=BASE/'data.ext4'
    with data.open('wb') as f:f.truncate(64*1024*1024)
    call(['mkfs.ext4','-q','-F','-m','0',data])
    consumer_data=BASE/'consumer.ext4'
    with consumer_data.open('wb') as f:f.truncate(64*1024*1024)
    call(['mkfs.ext4','-q','-F','-m','0',consumer_data])
    if hashlib.sha256((root/'app/kanger.jar').read_bytes()).digest()!=hashlib.sha256(jar.read_bytes()).digest():raise ValueError('Provider copy changed')
    files=[Path(__file__),jar,revision,overlay,negative,init,os_image,data,consumer_data,original/'manifest.json',ROOT/m['kernel'],original/'tools/usr/bin/qemu-system-x86_64',*sources,*sorted((root/'app/classes').rglob('*.class')),*sorted((root/'app/producer-overlay').rglob('*.class')),*sorted((root/'app/consumer-negative-overlay').rglob('*.class'))]
    result=dict(prepared_basis=m,java_profile='baseline',kernel=m['kernel'],qemu_version=m['qemu_version'],os_template=str(os_image.relative_to(ROOT)),data_templates=dict(producer=str(data.relative_to(ROOT)),consumer=str(consumer_data.relative_to(ROOT))),consumer_provider_unmodified=True,producer_overlay='Two pause calls only: after successful marker replacement / visible initial link before confirmation',sha256={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files})
    (BASE/'manifest.json').write_text(json.dumps(result,indent=2)+'\n');print('PIN_VM_READY',BASE/'manifest.json')
if __name__=='__main__':main()
