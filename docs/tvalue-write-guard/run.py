from pathlib import Path
import subprocess
p=Path(__file__).parent
for name in ('settlement','serialization','faults'):
 subprocess.run(['python',str(p/name/'run.py')],check=True)
