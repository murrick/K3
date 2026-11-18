#!/usr/bin/env python3
"""Fetch checksum-locked official distribution debs into target; never install packages."""
import hashlib
import json
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[2]


def main():
    lock=json.loads((Path(__file__).with_name('tool-packages.json')).read_text())
    output=ROOT/'target/persistence-vm/debs';output.mkdir(parents=True,exist_ok=True)
    architecture=subprocess.check_output(['dpkg','--print-architecture'],text=True).strip()
    if architecture!='amd64':raise RuntimeError('This tool bundle is qualified only for amd64')
    for package in lock['packages']:
        path=output/package['filename']
        if not path.exists():subprocess.run(['apt-get','download',package['package']+'='+package['version']],cwd=output,check=True)
        if hashlib.sha256(path.read_bytes()).hexdigest()!=package['sha256']:raise ValueError('Package checksum mismatch: '+path.name)
    print('VM_TOOLS_FETCHED packages='+str(len(lock['packages'])))


if __name__=='__main__':main()
