"""Temporary exact entry counters, single main-thread Son diagnostic; restores sources."""
from pathlib import Path
import subprocess
import difflib
root=Path('.')
d=Path('docs/value-read-forensics-evidence')
paths={
 'variable':Path('kanger/src/org/kanger/units/TVariable.java'),
 'argument':Path('kanger/src/org/kanger/primitives/Argument.java'),
 'list':Path('kanger/src/org/kanger/primitives/ArgumentsList.java'),
 'domain':Path('kanger/src/org/kanger/units/Domain.java'),
 'runner':Path('kanger-qualification/src/org/kanger/SonProfileRunner.java'),
}
original={k:p.read_text() for k,p in paths.items()}
changed=dict(original)
def insert(key,token,value):
 assert changed[key].count(token)==1,(key,token)
 changed[key]=changed[key].replace(token,token+value)
insert('variable','public class TVariable implements Comparable<Object>, IUnit<TVariable> {','''
    public static long diagValue, diagCurrent, diagActive, diagSet, diagSame, diagDifferent, diagUnset;
    public static long diagValueInBase, diagCurrentInBase;
''')
insert('variable','    public ITerm getValue() throws Exception {','''
        diagValue++;
        if (org.kanger.primitives.ArgumentsList.diagDepth > 0) diagValueInBase++;
''')
insert('variable','    public TValue getCurrent() {','''
        diagCurrent++;
        if (org.kanger.primitives.ArgumentsList.diagDepth > 0) diagCurrentInBase++;
''')
insert('variable','    private Mind activeMind() {','\n        diagActive++;\n')
insert('variable','    public TVariable setMind(Mind mind) {','''
        diagSet++;
        WeakReference<Mind> previous = runtimeMind.get();
        if (previous == null) diagUnset++;
        else if (previous.get() == mind) diagSame++;
        else diagDifferent++;
''')
insert('argument','public class Argument implements IArgument {','''
    public static long diagValue, diagEmpty, diagValueInBase, diagEmptyInBase;
''')
insert('argument','    public ITerm getValue(IMind mind) throws Exception {','''
        diagValue++;
        if (ArgumentsList.diagDepth > 0) diagValueInBase++;
''')
insert('argument','    public boolean isEmpty(Mind mind) {','''
        diagEmpty++;
        if (ArgumentsList.diagDepth > 0) diagEmptyInBase++;
''')
insert('list','public class ArgumentsList extends ArrayList<IArgument> implements IList {','''
    public static long diagCalls, diagDepth, diagIterations;
''')
start=changed['list'].index('    public boolean equalsBase(Mind mind, Object o) {')
end=changed['list'].index('    public ArgumentsList convert(Mind mind)',start)
method=changed['list'][start:end]
method=method.replace('    public boolean equalsBase(Mind mind, Object o) {','''    public boolean equalsBase(Mind mind, Object o) {
        diagCalls++; diagDepth++;
        try {''')
method=method.replace('                    for (; i < arg.size(); ++i) {','''                    for (; i < arg.size(); ++i) {
                        diagIterations++;''')
last=method.rfind('    }')
method=method[:last]+'''        } finally { diagDepth--; }
'''+method[last:]
changed['list']=changed['list'][:start]+method+changed['list'][end:]
insert('domain','public class Domain extends Solve implements IUnit<Domain>, Comparable<Domain> {','\n    public static long diagSet;\n')
insert('domain','    public Domain setMind(Mind mind) throws Exception {','\n        diagSet++;\n')
insert('runner','            try { mind.optimizeHypothesis(); }','') # ensure unique site
changed['runner']=changed['runner'].replace('            try { mind.optimizeHypothesis(); }','''            resetCounters();
            try { mind.optimizeHypothesis(); }''')
insert('runner','            long optimizeNs = System.nanoTime() - start;','\n            dumpCounters(i);\n')
insert('runner','public final class SonProfileRunner {','''
    private static final Class<?>[] COUNTER_TYPES = {
        org.kanger.units.TVariable.class, org.kanger.primitives.Argument.class,
        org.kanger.primitives.ArgumentsList.class, org.kanger.units.Domain.class
    };
    private static void resetCounters() throws Exception {
        for (Class<?> type : COUNTER_TYPES)
            for (java.lang.reflect.Field field : type.getDeclaredFields())
                if (field.getName().startsWith("diag") && field.getType() == long.class) field.setLong(null, 0);
    }
    private static void dumpCounters(int sample) throws Exception {
        for (Class<?> type : COUNTER_TYPES)
            for (java.lang.reflect.Field field : type.getDeclaredFields())
                if (field.getName().startsWith("diag") && field.getType() == long.class)
                    System.out.println("COUNT sample="+sample+" "+type.getSimpleName()+"."+field.getName()+"="+field.getLong(null));
    }
''')
# Persist the instrumentation as a reviewable patch, never ship it in production.
patch=''.join(''.join(difflib.unified_diff(original[k].splitlines(True),changed[k].splitlines(True),fromfile='a/'+str(paths[k]),tofile='b/'+str(paths[k]))) for k in paths)
(d/'entry-counters.patch').write_text(patch)
try:
 for k,p in paths.items(): p.write_text(changed[k])
 subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d','../build/read-counter-classes','@docs/value-read-forensics-evidence/sources.txt'],check=True)
 flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup']
 cmd=['java','-Xmx512m','-Dbench.samples=2']+['-Dkanger.experiment.'+f+'=true' for f in flags]+['-cp','../build/read-counter-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.SonProfileRunner']
 with (d/'counts.log').open('w') as out,(d/'counts.err').open('w') as err:
  subprocess.run(cmd,stdout=out,stderr=err,check=True)
finally:
 for k,p in paths.items(): p.write_text(original[k])
print('COUNTER_PROBE_COMPLETE sources_restored=true',flush=True)
