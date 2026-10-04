from pathlib import Path
import re, subprocess, difflib, json
root=Path(__file__).parent
build=Path('../build/variable-traversal-source');build.mkdir(exist_ok=True)
sources=Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
patch=[];sites=[];updated=[]
receiver=r'(?:\(\(Function\) a\.getObject\(mind\)\)\.getArguments\(\)|tree\.get\(0\)\.getArguments\(\)|(?:\w+\.)?getArguments\(\)|\w+)'
pattern=re.compile(r'('+receiver+r')\.getTVariables\(mind\)|(?<![\w.])getTVariables\(mind\)')
for filename in sources:
    p=Path(filename);original=p.read_text();lines=original.splitlines(True);changed=[]
    for number,line in enumerate(lines,1):
        def replace(m):
            site=p.stem+':'+str(number)
            expression=m.group(1) or 'this'
            sites.append(dict(site=site,path=filename,line=number,original=line.strip()))
            return 'org.kanger.VariableTraversalProfile.variables('+expression+', mind, "'+site+'")'
        changed.append(pattern.sub(replace,line))
    s=''.join(changed)
    if p.name=='ArgumentsList.java':
        start=s.index('    public List<TVariable> getTVariables(IMind mind)')
        end=s.index('    public List<ITerm> getCVariables(',start)
        body=s[start:end].replace('        for (IArgument a : this) {','        for (IArgument a : this) {\n            org.kanger.VariableTraversalProfile.argument();',1)
        s=s[:start]+body+s[end:]
    if p.name=='Domain.java':
        signatures=[('isProduced','    public boolean isProduced(Mind mind) throws Exception {'),('isCalculated','    public boolean isCalculated(ArgumentsList arguments, Mind mind) throws Exception {'),('unCalculated','    public void unCalculated(Mind mind) throws Exception {')]
        for name,signature in signatures:
            start=s.index(signature)
            end=s.index('\n    public ',start+len(signature))
            body=s[start:end]
            body=body.replace(signature,signature+'\n        org.kanger.VariableTraversalProfile.stamp("'+name+'", 0);',1)
            body=body.replace('                if (arguments.equalsStamp(mind, list)) {','                org.kanger.VariableTraversalProfile.stamp("'+name+'", 1);\n                if (arguments.equalsStamp(mind, list)) {',1)
            s=s[:start]+body+s[end:]
    if p.name=='Mind.java':
        start=s.index('    public boolean isUnitDeleted(IUnit unit)')
        end=s.index('    public void setUnitDeleted(',start)
        body=s[start:end].replace('        long unitId = unit.getId();','        long unitId = unit.getId();\n        org.kanger.VariableTraversalProfile.deletion();',1)
        s=s[:start]+body+s[end:]
    if s!=original:
        dest=build/filename;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(s)
        updated.append(str(dest))
        patch.extend(difflib.unified_diff(original.splitlines(True),s.splitlines(True),fromfile='a/'+filename,tofile='b/'+filename,n=0))
    else:updated.append(filename)
assert len(sites)==18,sites
root.joinpath('callsites.json').write_text(json.dumps(sites,indent=2)+'\n')
root.joinpath('instrumentation.patch').write_text(''.join(patch))
updated.append(str(root/'VariableTraversalProfile.java'))
build.joinpath('sources.txt').write_text('\n'.join(updated)+'\n')
classes='../build/variable-traversal-classes'
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',classes,'@'+str(build/'sources.txt')],check=True)
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',classes,'-sourcepath','kanger-qualification/src','-d',classes,str(root/'VariableTraversalProfileRunner.java')],check=True)
