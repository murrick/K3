from pathlib import Path
import subprocess, re
root = Path(__file__).parent
for run in [1, 2]:
    prefix = root / ('profile-%d' % run)
    cmd = ['java','-Xmx512m','-Dbench.samples=6','-cp','../build/deletion-membership-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.DeletionMembershipProfileRunner']
    with prefix.with_suffix('.log').open('w') as out, prefix.with_suffix('.err').open('w') as err:
        subprocess.run(cmd, stdout=out, stderr=err, check=True)
    s = prefix.with_suffix('.log').read_text()
    for marker in ['SAMPLE','DELETION_COUNTS','DELETION_DEPTHS']:
        assert re.findall(r'^'+marker+r' (\d+) ',s,re.M)==[str(i) for i in range(6)], prefix
    assert not prefix.with_suffix('.err').read_text(), prefix
    print('DONE',prefix,flush=True)
