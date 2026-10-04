from pathlib import Path
import difflib, subprocess

root = Path(__file__).parent
build = Path('../build/deletion-membership-source')
build.mkdir(exist_ok=True)
original = Path('kanger/src/org/kanger/Mind.java').read_text()
fields = '''
    // Diagnostic-only: main optimization thread; no timing inference from this build.
    private static Thread deletionProfileThread;
    private static long[] deletionProfileCounts = new long[15];
    private static long[] deletionProfileDepths = new long[65];
    public static void resetDeletionMembershipProfile() {
        deletionProfileCounts = new long[15];
        deletionProfileDepths = new long[65];
        deletionProfileThread = Thread.currentThread();
    }
    private static void finishDeletionMembershipProfile(int depth) {
        deletionProfileDepths[Math.min(depth, 64)]++;
    }
    public static void reportDeletionMembershipProfile(int sample) {
        deletionProfileThread = null;
        System.out.println("DELETION_COUNTS " + sample + " " + Arrays.toString(deletionProfileCounts));
        System.out.println("DELETION_DEPTHS " + sample + " " + Arrays.toString(deletionProfileDepths));
    }
'''
source = original.replace('    public boolean isUnitDeleted(IUnit unit) {', fields + '\n    public boolean isUnitDeleted(IUnit unit) {', 1)
start = source.index('    public boolean isUnitDeleted(IUnit unit) {')
end = source.index('    public void setUnitDeleted(', start)
body = source[start:end]
body = body.replace('        for (IMind level', '''        boolean count = Thread.currentThread() == deletionProfileThread;
        int depth = 0;
        boolean builtinSeen = false;
        if (count) deletionProfileCounts[0]++;
        for (IMind level''')
body = body.replace('            Mind current = (Mind) level;', '''            Mind current = (Mind) level;
            depth++;
            if (count) deletionProfileCounts[1]++;''')
for name, indexes in [('restored', (2, 4, 6, 8)), ('deleted', (3, 5, 7, 9))]:
    empty, absent, contains, hit = indexes
    line = '                Set<Long> ' + name + 'Ids = current.' + name + '.get(unitType);'
    insert = '''
                if (count) {
                    if (current.NAME.isEmpty()) deletionProfileCounts[EMPTY]++;
                    if (NAMEIds == null) deletionProfileCounts[ABSENT]++;
                    else {
                        deletionProfileCounts[CONTAINS]++;
                        if (NAMEIds.getClass() == HashSet.class) {
                            deletionProfileCounts[10]++;
                            if (builtinSeen && (unitId < -128 || unitId > 127)) deletionProfileCounts[13]++;
                            builtinSeen = true;
                        } else deletionProfileCounts[11]++;
                        if (unitId < -128 || unitId > 127) deletionProfileCounts[12]++;
                    }
                }'''.replace('NAME', name).replace('EMPTY', str(empty)).replace('ABSENT', str(absent)).replace('CONTAINS', str(contains))
    body = body.replace(line, line + insert)
    condition = '                if (' + name + 'Ids != null && ' + name + 'Ids.contains(unitId)) {'
    body = body.replace(condition, condition + '\n                    if (count) { deletionProfileCounts['+str(hit)+']++; finishDeletionMembershipProfile(depth); }')
body = body.replace('        return false;\n    }', '        if (count) { deletionProfileCounts[14]++; finishDeletionMembershipProfile(depth); }\n        return false;\n    }')
source = source[:start] + body + source[end:]
build.joinpath('Mind.java').write_text(source)
root.joinpath('instrumentation.patch').write_text(''.join(difflib.unified_diff(original.splitlines(True), source.splitlines(True), fromfile='a/kanger/src/org/kanger/Mind.java', tofile='b/kanger/src/org/kanger/Mind.java', n=0)))
sources = Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
sources = [str(build/'Mind.java') if x == 'kanger/src/org/kanger/Mind.java' else x for x in sources]
build.joinpath('sources.txt').write_text('\n'.join(sources)+'\n')
classes = '../build/deletion-membership-classes'
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',classes,'@'+str(build/'sources.txt')], check=True)
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',classes,'-sourcepath','kanger-qualification/src','-d',classes,str(root/'DeletionMembershipProfileRunner.java')], check=True)
