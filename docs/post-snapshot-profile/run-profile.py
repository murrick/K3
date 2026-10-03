import subprocess,re
from pathlib import Path

folder = Path(__file__).parent
for index in (1, 2):
    recording = '../build/post-snapshot-%d.jfr' % index
    command = ['java', '-Xmx512m', '-XX:FlightRecorderOptions=stackdepth=256',
               '-XX:StartFlightRecording=filename=' + recording + ',settings=profile,dumponexit=true',
               '-Dbench.samples=6', '-Dbench.allocation=true', '-cp',
               '../build/post-snapshot-tools:../build/post-snapshot-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar',
               'org.kanger.WarmSonProfileRunner']
    with (folder / ('profile-%d.log' % index)).open('w') as out, (folder / ('profile-%d.err' % index)).open('w') as err:
        subprocess.run(command, stdout=out, stderr=err, check=True)
    log=(folder / ('profile-%d.log' % index)).read_text()
    assert re.findall(r'^SAMPLE (\d+) ',log,re.M)==[str(n) for n in range(6)],index
    assert len(re.findall(r'^RAW ',log,re.M))==len(re.findall(r'^OPTIMIZED ',log,re.M))==6,index
    assert not (folder / ('profile-%d.err' % index)).read_text(),index
    with (folder / ('profile-%d.tsv' % index)).open('w') as out:
        subprocess.run(['java', '-cp', '../build/post-snapshot-tools', 'ReadWarmProfile', recording], stdout=out, check=True)
    print('DONE', index, flush=True)
