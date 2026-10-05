from pathlib import Path
import re,subprocess,difflib,json
root=Path(__file__).parent
build=Path('../build/rule-read-source');build.mkdir(exist_ok=True)
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
    return 'org.kanger.RuleReadProfile.select('+receiver+', mind, "'+site+'")'
   lines.append(re.sub(pattern,replace,line))
  s=''.join(lines)
 lines=[]
 for n,line in enumerate(s.splitlines(True),1):
  site=p.stem+':'+str(n)
  original_line=line
  if p.name=='RuleFactory.java':
   line=re.sub(r'(?<== )get\(id\)', 'org.kanger.RuleReadProfile.lookup(this, id, "'+site+'")', line)
  if p.name=='RuleCandidateIndex.java':
   line=line.replace('activeMind.getRules().get(id)', 'org.kanger.RuleReadProfile.lookup(activeMind.getRules(), id, "'+site+'")')
  if p.name=='Domain.java':
   line=line.replace('mind.getRules().find(this)', 'org.kanger.RuleReadProfile.find(mind.getRules(), this, "'+site+'")')
   line=line.replace('mind.getRules().find(d)', 'org.kanger.RuleReadProfile.find(mind.getRules(), d, "'+site+'")')
  def iterator_replace(m):
   return m.group(1)+'org.kanger.RuleReadProfile.iterate('+m.group(2)+', "'+site+'"))'
  line=re.sub(r'(for \(.* : )(\w+\.getRules\(\))\)',iterator_replace,line)
  if p.name=='GeneratedCVarMaterializer.java':
   line=re.sub(r'(for \(Object value : )(factory)\)',iterator_replace,line)
  if p.name=='RuleFactory.java':
   line=re.sub(r'(for \(Object \w+ : )(cache|this)\)',iterator_replace,line)
  if line!=original_line:sites.append(dict(site=site,path=filename,line=n,original=original_line.strip(),kind='source'))
  def read_replace(m):
   sites.append(dict(site=site,path=filename,line=n,original=original_line.strip(),kind='data_read'))
   return 'org.kanger.RuleReadProfile.read('+m.group(1)+', mind, "'+site+'")'
  lines.append(re.sub(r'\b(\w+)\.getData\(mind\)',read_replace,line))
 s=''.join(lines)
 if p.name=='Domain.java':
  start=s.index('    public Domain setMind(Mind mind) throws Exception {')
  end=s.index('    public int getHashStruct()',start)
  body=s[start:end]
  body=body.replace('        this.mind = mind;', '        org.kanger.RuleReadProfile.enter(this, this.mind, mind);\n        try {\n        this.mind = mind;',1)
  body=body.replace('        return this;\n    }','        return this;\n        } finally { org.kanger.RuleReadProfile.leave(); }\n    }',1)
  s=s[:start]+body+s[end:]
 if p.name=='TVariable.java':
  s=s.replace('    public TVariable setMind(Mind mind) {','    public TVariable setMind(Mind mind) {\n        if (org.kanger.RuleReadProfile.inspectVariable())\n            org.kanger.RuleReadProfile.variable(this, activeMind(), mind);',1)
 if s!=original:
  dest=build/filename;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(s);updated.append(str(dest))
  patch.extend(difflib.unified_diff(original.splitlines(True),s.splitlines(True),fromfile='a/'+filename,tofile='b/'+filename,n=0))
 else:updated.append(filename)
root.joinpath('callsites.json').write_text(json.dumps(sites,indent=2)+'\n')
root.joinpath('instrumentation.patch').write_text(''.join(patch))
updated.append(str(root/'RuleReadProfile.java'))
build.joinpath('sources.txt').write_text('\n'.join(updated)+'\n')
classes='../build/rule-read-classes'
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',classes,'@'+str(build/'sources.txt')],check=True)
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',classes,'-sourcepath','kanger-qualification/src','-d',classes,str(root/'RuleReadProfileRunner.java')],check=True)
