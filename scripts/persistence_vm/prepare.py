#!/usr/bin/env python3
"""Build an owned ext4 guest image from local tool debs; no mount or system installation."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'target/persistence-vm'


def call(args,**kw):return subprocess.run(list(map(str,args)),check=True,**kw)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-profile',choices=['baseline','c1'],default='baseline',
                        help='C1 is an experimental diagnostic profile, not accepted qualification evidence')
    args=parser.parse_args()
    tools=BASE/'tools';tools.mkdir(parents=True,exist_ok=True)
    lock=json.loads((Path(__file__).with_name('tool-packages.json')).read_text())
    debs=[BASE/'debs'/package['filename'] for package in lock['packages']]
    for package,deb in zip(lock['packages'],debs):
        if hashlib.sha256(deb.read_bytes()).hexdigest()!=package['sha256']:raise ValueError('Unverified tool package: '+deb.name)
        call(['dpkg-deb','-x',deb,tools])
    env=os.environ.copy();env['LD_LIBRARY_PATH']=str(tools/'usr/lib/x86_64-linux-gnu')
    qemu=tools/'usr/bin/qemu-system-x86_64'
    version=subprocess.check_output([str(qemu),'--version'],env=env,text=True).splitlines()[0]
    root=BASE/'rootfs'
    if root.exists():shutil.rmtree(root)
    for directory in ['bin','sbin','dev','proc','sys','tmp','root','store','opt/java','app','etc','lib64']:(root/directory).mkdir(parents=True,exist_ok=True)
    shutil.copy2('/usr/bin/busybox',root/'bin/busybox')
    for name in ['sh','mount','sleep','cat','mkdir']:(root/'bin'/name).symlink_to('busybox')
    jdk=Path('/usr/lib/jvm/java-21-openjdk-amd64')
    shutil.copytree(jdk,root/'opt/java',dirs_exist_ok=True,symlinks=False,ignore=shutil.ignore_patterns('jmods','include','man','src.zip'))
    dependencies=set()
    for executable in [jdk/'bin/java',*jdk.glob('lib/**/*.so')]:
        output=subprocess.run(['ldd',str(executable)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True).stdout
        dependencies.update(re.findall(r'(?:=>\s+|^\s*)(/[^\s]+)',output,re.M))
    for library in sorted(dependencies):
        destination=root/library.lstrip('/');destination.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(library,destination)
    (root/'etc/passwd').write_text('root:x:0:0:root:/root:/bin/sh\n')
    (root/'etc/group').write_text('root:x:0:\n')
    (root/'etc/nsswitch.conf').write_text('passwd: files\ngroup: files\nhosts: files\n')
    (root/'etc/hosts').write_text('127.0.0.1 localhost\n')
    jar=ROOT/'kanger-console/target/kanger-console-3.3-SNAPSHOT.jar';shutil.copy2(jar,root/'app/kanger.jar')
    source=ROOT/'kanger-qualification/src/org/kanger/storage/dumb2/ContextBlockDeviceRunner.java'
    call(['javac','-source','8','-target','8','-Xlint:-options','-cp',jar,'-d',root/'app/classes',source])
    init=root/'sbin/init'
    init.write_text('''#!/bin/sh
/bin/mount -t proc proc /proc
/bin/mount -t sysfs sysfs /sys
/bin/mount -t devtmpfs devtmpfs /dev 2>/dev/null || true
phase=read
for option in $(/bin/cat /proc/cmdline); do
 case "$option" in kanger.phase=*) phase=${option#kanger.phase=};; esac
done
export KANGER_HOME=KANGER
/opt/java/bin/java -XX:-UsePerfData __QUALIFICATION_JAVA_OPTIONS__-XX:ActiveProcessorCount=1 -Xmx128m -Djava.security.egd=file:/dev/urandom -cp /app/classes:/app/kanger.jar org.kanger.storage.dumb2.ContextBlockDeviceRunner "$phase"
echo KANGER_GUEST_FAILED
while true; do /bin/sleep 1000; done
'''.replace('__QUALIFICATION_JAVA_OPTIONS__','-XX:TieredStopAtLevel=1 ' if args.java_profile=='c1' else ''));init.chmod(0o755)
    image=BASE/'template.ext4'
    with image.open('wb') as file:file.truncate(512*1024*1024)
    call(['mkfs.ext4','-q','-F','-m','0','-d',root,image])
    kernel=next((tools/'boot').glob('vmlinuz-*'))
    compiled=list((root/'app/classes').rglob('*.class'))
    if [str(p.relative_to(root/'app/classes')) for p in compiled]!=['org/kanger/storage/dumb2/ContextBlockDeviceRunner.class']:
        raise ValueError('Guest classpath contains an unexpected provider override')
    if hashlib.sha256((root/'app/kanger.jar').read_bytes()).digest()!=hashlib.sha256(jar.read_bytes()).digest():raise ValueError('Guest provider changed')
    files=[kernel,image,jar,source,qemu,Path(__file__),Path(__file__).with_name('tool-packages.json'),*debs]

    hashes={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    manifest=dict(java_profile=args.java_profile,qemu_version=version,kernel= str(kernel.relative_to(ROOT)),template=str(image.relative_to(ROOT)),
                  packages=[subprocess.check_output(['dpkg-deb','-f',str(p),'Package','Version','Architecture'],text=True).strip() for p in debs],sha256=hashes)
    (BASE/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print('VM_IMAGE_READY',image,'qemu='+version)


if __name__=='__main__':main()
