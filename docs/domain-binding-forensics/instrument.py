from pathlib import Path
import re,subprocess,difflib,json
root=Path(__file__).parent
build=Path('../build/domain-binding-source');build.mkdir(exist_ok=True)
sources=Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
updated=[];patch=[];sites=[]
for filename in sources:
 p=Path(filename);original=p.read_text();s=original
 if p.name in ['Step.java','Sapato.java'] or p.name.endswith('Factory.java') or p.name=='Rule.java':
  lines=[]
  for n,line in enumerate(s.splitlines(True),1):
   pattern=r'\(\(IUnit\) (\w+)\)\.setMind\(mind\)|\b(d|view)\.setMind\(mind\)'
   def replace(m):
    receiver='(IUnit) '+m.group(1) if m.group(1) else m.group(2)
    site=p.stem+':'+str(n);sites.append(dict(site=site,path=filename,line=n,original=line.strip()))
    return 'org.kanger.DomainBindingProfile.select('+receiver+', mind, "'+site+'")'
   lines.append(re.sub(pattern,replace,line))
  s=''.join(lines)
 if p.name=='Domain.java':
  start=s.index('    public Domain setMind(Mind mind) throws Exception {')
  end=s.index('    public int getHashStruct()',start)
  body=s[start:end]
  body=body.replace('        this.mind = mind;', '        org.kanger.DomainBindingProfile.enter(this, this.mind, mind);\n        try {\n        this.mind = mind;',1)
  body=body.replace('        return this;\n    }','        return this;\n        } finally { org.kanger.DomainBindingProfile.leave(); }\n    }',1)
  s=s[:start]+body+s[end:]
 if p.name=='TVariable.java':
  s=s.replace('    public TVariable setMind(Mind mind) {','    public TVariable setMind(Mind mind) {\n        if (org.kanger.DomainBindingProfile.inspectVariable())\n            org.kanger.DomainBindingProfile.variable(this, activeMind(), mind);',1)
 if s!=original:
  dest=build/filename;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(s);updated.append(str(dest))
  patch.extend(difflib.unified_diff(original.splitlines(True),s.splitlines(True),fromfile='a/'+filename,tofile='b/'+filename,n=0))
 else:updated.append(filename)
root.joinpath('callsites.json').write_text(json.dumps(sites,indent=2)+'\n')
root.joinpath('instrumentation.patch').write_text(''.join(patch))
updated.append(str(root/'DomainBindingProfile.java'))
build.joinpath('sources.txt').write_text('\n'.join(updated)+'\n')
classes='../build/domain-binding-classes'
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',classes,'@'+str(build/'sources.txt')],check=True)
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',classes,'-sourcepath','kanger-qualification/src','-d',classes,str(root/'DomainBindingProfileRunner.java'),str(root/'DomainBindingWitness.java')],check=True)
