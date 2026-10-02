import subprocess
from pathlib import Path

folder = Path(__file__).parent
for index in (1, 2):
    recording = '../build/post-defaults-%d.jfr' % index
    command = ['java', '-Xmx512m', '-XX:FlightRecorderOptions=stackdepth=256',
               '-XX:StartFlightRecording=filename=' + recording + ',settings=profile,dumponexit=true',
               '-Dbench.samples=6', '-Dbench.allocation=true', '-cp',
               '../build/post-defaults-tools:../build/defaults-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar',
               'org.kanger.WarmSonProfileRunner']
    with (folder / ('profile-%d.log' % index)).open('w') as out, (folder / ('profile-%d.err' % index)).open('w') as err:
        subprocess.run(command, stdout=out, stderr=err, check=True)
    with (folder / ('profile-%d.tsv' % index)).open('w') as out:
        subprocess.run(['java', '-cp', '../build/post-defaults-tools', 'ReadWarmProfile', recording], stdout=out, check=True)
    print('DONE', index, flush=True)
