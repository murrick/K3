from pathlib import Path
import difflib,hashlib,json,subprocess

root=Path(__file__).parent
base='1c943d02d39bd33ebd91fabb7c4190112ab49459'
assert not subprocess.check_output(['git','diff',base,'--','kanger/src','kanger-udf/src','kanger-qualification/src','.github'])
temporary=Path('../build/hypothesis-phase-source');temporary.mkdir(parents=True,exist_ok=True)
sources=Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
patch=[]
helper='org.kanger.HypothesisPhaseProfile'
def replace(s,a,b):
    assert s.count(a)==1,(a,s.count(a))
    return s.replace(a,b)
def timed(body,statement,label,variable=None):
    indent=statement[:len(statement)-len(statement.lstrip())]
    declaration=''
    if variable:
        typename,name=variable
        declaration=indent+typename+' '+name+';\n'
        statement=statement.replace(typename+' '+name+' = ',name+' = ',1)
    replacement=declaration+indent+helper+'.Mark profile_'+label+' = '+helper+'.start("'+label+'");\n'+indent+'try {\n'+statement+'\n'+indent+'} finally { '+helper+'.end(profile_'+label+'); }'
    # declaration changes only the generated replacement, not the needle.
    needle=statement if not variable else statement.replace(variable[1]+' = ',variable[0]+' '+variable[1]+' = ',1)
    return replace(body,needle,replacement)

path='kanger/src/org/kanger/stores/HypothesisStore.java';original=Path(path).read_text();s=original
s=replace(s,'    public void optimize() throws Exception {','    public void optimize() throws Exception {\n        '+helper+'.event("store_entry", 1);')
s=replace(s,'            QueryReplayContext.Snapshot replay = QueryReplayContext.snapshot(mind);','            '+helper+'.event("root_before", root.size());\n            QueryReplayContext.Snapshot replay = QueryReplayContext.snapshot(mind);\n            '+helper+'.event(replay == null ? "without_replay" : "with_replay", 1);')
s=timed(s,'                Mind expanded = new Mind(mind);','expanded_create',('Mind','expanded'))
s=timed(s,'                    Boolean answer = expanded.query(\n                            replay.getSource(), replay.getExternals(), false);','expanded_query',('Boolean','answer'))
s=replace(s,'                    if (answer == null) {','                    '+helper+'.answer(answer, true);\n                    if (answer == null) {')
s=timed(s,'                        mergeExpanded(expanded);','expanded_merge')
s=timed(s,'                    mind.release(expanded);','expanded_release')
s=replace(s,'            List<IHypothesis> list = new ArrayList<>();','            '+helper+'.event("root_after_expansion", root.size());\n            List<IHypothesis> list = new ArrayList<>();')
s=replace(s,'            for (IHypothesis h : list) {','            '+helper+'.event("candidate_count", list.size());\n            for (IHypothesis h : list) {\n                '+helper+'.beginCandidate(h);')
s=timed(s,'                Mind m = new Mind(mind);','candidate_create',('Mind','m'))
s=replace(s,'((Hypothesis) h).toString(m), false, null);',helper+'.source(((Hypothesis) h).toString(m)), false, null);')
s=timed(s,'                    Rule r = (Rule) m.compileLine(\n                            '+helper+'.source(((Hypothesis) h).toString(m)), false, null);','candidate_render_compile',('Rule','r'))
s=replace(s,'                    if (r == null) {','                    '+helper+'.compiled(r);\n                    if (r == null) {')
s=timed(s,'                    m.link(r, false);','candidate_link')
s=timed(s,'                    Boolean collision = m.analyze(null, false);','candidate_analyze',('Boolean','collision'))
s=replace(s,'                    if (!collision) {','                    '+helper+'.collision(collision);\n                    if (!collision) {')
s=timed(s,'                            Boolean answer = m.query(\n                                    replay.getSource(), replay.getExternals(), false);','candidate_replay',('Boolean','answer'))
s=replace(s,'                            if (answer != null) {','                            '+helper+'.answer(answer, false);\n                            if (answer != null) {')
s=s.replace('success.add(h);','success.add(h);\n                            '+helper+'.accepted();')
s=timed(s,'                    mind.release(m);','candidate_release')
s=replace(s,'                    } finally { '+helper+'.end(profile_candidate_release); }','                    } finally { '+helper+'.end(profile_candidate_release); '+helper+'.endCandidate(); }')
s=replace(s,'            optimized = true;','            optimized = true;\n            '+helper+'.event("root_final", root.size());')
s=replace(s,'admitted.add(((Hypothesis) h).toString(mind));','admitted.add('+helper+'.mergeSource(((Hypothesis) h).toString(mind), false));')
s=replace(s,'String source = ((Hypothesis) h).toString(expanded);','String source = '+helper+'.mergeSource(((Hypothesis) h).toString(expanded), true);')
s=replace(s,'if (admitted.add(source)) {','if ('+helper+'.admission(admitted.add(source))) {')
modified={path:s}

path='kanger/src/org/kanger/Mind.java';s=Path(path).read_text()
for signature,kind in [('public Mind(IMind root)',0),('public Object compileLine(String line, boolean query, Queue<ITerm> externals)',1),('public void link(Rule r, boolean logging)',2),('public boolean analyze(Rule rule, boolean logging)',3),('public void release(IMind m)',4)]:
    needle='    '+signature+' throws Exception {'
    s=replace(s,needle,needle+'\n        '+helper+'.core('+str(kind)+');')
for name,label,end in [('queryCheckFalse','false','    public Boolean queryCheckTrue('),('queryCheckTrue','true','    public Boolean query(')]:
    start=s.index('    public Boolean '+name+'(');stop=s.index(end,start)
    b=s[start:stop]
    b=replace(b,' throws Exception {',' throws Exception {\n        '+helper+'.Mark profilePass = '+helper+'.startPass("'+label+'");\n        try {')
    b=replace(b,'            return res;','            return '+helper+'.passResult(res);')
    b=replace(b,'\n    }','\n        } finally { '+helper+'.end(profilePass); }\n    }')
    s=s[:start]+b+s[stop:]
modified[path]=s
for path,s in modified.items():
    dest=temporary/Path(path).name;dest.write_text(s);sources[sources.index(path)]=str(dest)
    patch+=list(difflib.unified_diff(Path(path).read_text().splitlines(True),s.splitlines(True),fromfile='a/'+path,tofile='b/'+path,n=0))
(root/'instrumentation.patch').write_text(''.join(patch));sources.append(str(root/'HypothesisPhaseProfile.java'))
(root/'sources.txt').write_text('\n'.join(sources)+'\n')
runner=Path('kanger-qualification/src/org/kanger/SonProfileRunner.java').read_text().replace('public final class SonProfileRunner','public final class HypothesisPhaseProfileRunner')
runner=replace(runner,'            try { mind.optimizeHypothesis(); }','            HypothesisPhaseProfile.begin();\n            try { mind.optimizeHypothesis(); }')
runner=replace(runner,'            long optimizeNs = System.nanoTime() - start;','            HypothesisPhaseProfile.finish(i);\n            long optimizeNs = System.nanoTime() - start;')
(root/'HypothesisPhaseProfileRunner.java').write_text(runner)
for mode in ['reference','classes']:
    classes='../build/hypothesis-phase-'+mode
    listing=root/'sources.txt' if mode=='classes' else Path('docs/resident-base-comparison-evidence/sources.txt')
    subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',classes,'@'+str(listing)],check=True,timeout=120)
    runnerpath=root/'HypothesisPhaseProfileRunner.java' if mode=='classes' else Path('kanger-qualification/src/org/kanger/SonProfileRunner.java')
    subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',classes,'-d',classes,str(runnerpath)],check=True,timeout=30)
    hashes={str(p.relative_to(classes)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(Path(classes).rglob('*.class'))}
    (root/(mode+'-class-sha256.json')).write_text(json.dumps(hashes,indent=2)+'\n')
print('HYPOTHESIS_PHASE_BUILD_READY')
