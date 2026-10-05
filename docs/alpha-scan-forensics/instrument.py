from pathlib import Path
import subprocess,difflib
root=Path(__file__).parent
build=Path('../build/alpha-scan-source');build.mkdir(exist_ok=True)
filename='kanger/src/org/kanger/GeneratedCVarMaterializer.java'
original=Path(filename).read_text();s=original
start=s.index('    public static IRule findAlphaEquivalent(')
end=s.index('    public static ArgumentsList rebindForGeneratedRule(',start)
body=s[start:end]
body=body.replace('        if (source.isQuery(mind) || !containsCVariable(source, mind)) {',
'''        org.kanger.AlphaScanProfile.enter();
        try {
        if (org.kanger.AlphaScanProfile.test("sourceQuery", source.isQuery(mind))
                || !org.kanger.AlphaScanProfile.test("sourceContainsCVar", containsCVariable(source, mind))) {''',1)
body=body.replace('            return null;', '            org.kanger.AlphaScanProfile.finish("gateRejected");\n            return null;',1)
body=body.replace('        for (Object value : factory) {','        org.kanger.AlphaScanProfile.event("eligible");\n        for (Object value : factory) {',1)
body=body.replace('            IRule rule = (IRule) value;', '            IRule rule = (IRule) value;\n            org.kanger.AlphaScanProfile.visit();',1)
body=body.replace('if (!rule.isStored() || rule.isQuery()) {',
'''if (org.kanger.AlphaScanProfile.test("notStored", !rule.isStored())
                    || org.kanger.AlphaScanProfile.test("candidateQuery", rule.isQuery())) {
                org.kanger.AlphaScanProfile.event("flagsRejected");''',1)
body=body.replace('            Domain candidate = ((Rule) rule).getDomain();', '            Domain candidate = ((Rule) rule).getDomain();\n            org.kanger.AlphaScanProfile.event("shapeCalls");',1)
body=body.replace('                return rule;', '                org.kanger.AlphaScanProfile.finish("hit");\n                return rule;',1)
body=body.replace('        return null;\n    }','        org.kanger.AlphaScanProfile.finish("miss");\n        return null;\n        } finally { org.kanger.AlphaScanProfile.leave(); }\n    }',1)
s=s[:start]+body+s[end:]
s=s.replace('left.isAntc() != right.isAntc()', 'org.kanger.AlphaScanProfile.test("polarityMismatch", left.isAntc() != right.isAntc())',1)
s=s.replace('left.getPredicateId() != right.getPredicateId()', 'org.kanger.AlphaScanProfile.test("predicateMismatch", left.getPredicateId() != right.getPredicateId())',1)
s=s.replace('left.getRange() != right.getRange()', 'org.kanger.AlphaScanProfile.test("arityMismatch", left.getRange() != right.getRange())',1)
s=s.replace('        Map<Long, Long> forward = new HashMap<>();','        org.kanger.AlphaScanProfile.event("shapeAccepted");\n        Map<Long, Long> forward = new HashMap<>();',1)
s=s.replace('            IArgument leftArgument = left.get(i);','            org.kanger.AlphaScanProfile.event("argumentPositions");\n            IArgument leftArgument = left.get(i);',1)
s=s.replace('                return false;\n            }\n\n            ITerm leftValue', '                org.kanger.AlphaScanProfile.event("emptyArgument");\n                return false;\n            }\n\n            ITerm leftValue',1)
s=s.replace('            if (leftCVar != rightCVar) {','            if (leftCVar != rightCVar) {\n                org.kanger.AlphaScanProfile.event("cvarKindMismatch");',1)
s=s.replace('                if (leftTerm.isDomini() != rightTerm.isDomini()) {','                if (leftTerm.isDomini() != rightTerm.isDomini()) {\n                    org.kanger.AlphaScanProfile.event("dominiMismatch");',1)
s=s.replace('                    return false;\n                }\n            } else if', '                    org.kanger.AlphaScanProfile.event("bijectionMismatch");\n                    return false;\n                }\n            } else if',1)
s=s.replace('            } else if (!((Term) leftValue).equalsTo((Term) rightValue)) {','            } else if (!((Term) leftValue).equalsTo((Term) rightValue)) {\n                org.kanger.AlphaScanProfile.event("termMismatch");',1)
dest=build/filename;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(s)
root.joinpath('instrumentation.patch').write_text(''.join(difflib.unified_diff(original.splitlines(True),s.splitlines(True),fromfile='a/'+filename,tofile='b/'+filename,n=0)))
sources=Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
updated=[str(dest) if x==filename else x for x in sources]+[str(root/'AlphaScanProfile.java')]
build.joinpath('sources.txt').write_text('\n'.join(updated)+'\n')
classes='../build/alpha-scan-classes'
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',classes,'@'+str(build/'sources.txt')],check=True)
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',classes,'-sourcepath','kanger-qualification/src','-d',classes,str(root/'AlphaScanProfileRunner.java')],check=True)
