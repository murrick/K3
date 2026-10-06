"""Run the finite controls and four workflow timing JVMs, serially."""
from pathlib import Path
import os,subprocess,sys
root=Path(__file__).parent
matrix=[('son',['exact','prepared','full'],{'TAG':'focus','FOCUS':'44'}),('son',['exact','full'],{'TAG':'reverse','REVERSE':'true'}),('small',['full'],{}),('son',['exact'],{'TAG':'e1','SAMPLES':'6','MEASURE':'true'}),('son',['full'],{'TAG':'f1','SAMPLES':'6','MEASURE':'true'}),('son',['full'],{'TAG':'f2','SAMPLES':'6','MEASURE':'true'}),('son',['exact'],{'TAG':'e2','SAMPLES':'6','MEASURE':'true'})]
start=int(sys.argv[1]) if len(sys.argv)>1 else 0
for case,modes,properties in matrix[start:]:
 env={k:v for k,v in os.environ.items() if not k.startswith('PREPARED_STUDY_')}
 env.update({'PREPARED_STUDY_'+k:v for k,v in properties.items()})
 print('START',case,modes,properties,flush=True)
 subprocess.run([sys.executable,str(root/'run.py'),case,*modes],env=env,check=True)
