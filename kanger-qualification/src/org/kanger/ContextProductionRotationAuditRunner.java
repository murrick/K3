/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.*;
import org.kanger.ContextProductionInputs.Declaration;
import org.kanger.units.*;

/** Measures finite native bindings; does not certify multi-variable inference. */
public final class ContextProductionRotationAuditRunner {
    private static int checks;
    private static final Source X = new Source(new UUID(0,1),1,0,true,"base");
    private static Mind memory() throws Exception {
        User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind); return mind;
    }
    private static List<TVariable> variables(Mind mind) throws Exception {
        SortedSet<TVariable> result = new TreeSet<>();
        for (org.kanger.interfaces.IRule entry : mind.getRules()) for (List<Domain> branch : ((Rule)entry).getTree())
            for (Domain domain : branch) result.addAll(domain.getArguments().getTVariables(mind));
        return new ArrayList<>(result);
    }
    private static Snapshot measure(String statement,int seeded,int limit,boolean link) throws Exception {
        Mind mind = memory();
        try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,limit)) {
            capture.replay(mind,X,statement);
            org.kanger.interfaces.ITerm one = mind.getTerms().add(1), two = mind.getTerms().add("John");
            List<SemanticTermSnapshot> basis = Arrays.asList(SemanticTermSnapshot.capture(one),SemanticTermSnapshot.capture(two));
            for (TVariable variable : variables(mind)) {
                if (seeded > 0) mind.getTValues().add(variable,one);
                if (seeded > 1) mind.getTValues().add(variable,two);
            }
            capture.auditRotations(mind,basis);
            mind.setQueryPass(org.kanger.enums.QueryPass.ACCEPT);
            if (link) mind.link(null,false);
            Snapshot snapshot = capture.snapshot();
            require(snapshot.rotationsAudited,"explicitly configured measurement");
            require(snapshot.applications.isEmpty() && snapshot.residuals.isEmpty(),"bindings alone prove no missing premise");
            return snapshot;
        }
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("rotation-audit-").toString());
            for (int seeded=0;seeded<=2;++seeded) {
                Snapshot snapshot = measure("!@x @y p(x,y) -> q(y,x);",seeded,10000,true);
                RotationAudit audit = snapshot.rotations.get(0);
                require(snapshot.rotations.size() == 1 && audit.rank == 2 && audit.expected == 4,"two variables require all four ordered bindings");
                require(audit.visited == (seeded == 0 ? 0 : seeded == 1 ? 1 : 4),"counts actual bindings without inventing Cartesian visits");
                require(audit.admitted == audit.visited && snapshot.allRotationsAdmitted() == (seeded == 2),
                        "unconstrained admission and database observations agree");
                require(snapshot.allRotationsVisited() == (seeded == 2),"missing representatives are visible");
                require(snapshot.eventCount == snapshot.inputs.size()+snapshot.rotations.size()+audit.visited+audit.admitted,"both phases enter the event budget");
            }
            Snapshot pending = measure("!@x @y p(x,y) -> q(y,x);",2,10000,false);
            require(!pending.allRotationsVisited() && pending.rotations.get(0).visited == 0,"seeding is not evidence of traversal");
            Snapshot repeated = measure("!@x p(x,x) -> q(x);",2,10000,true);
            require(repeated.rotations.get(0).rank == 1 && repeated.rotations.get(0).expected == 2
                    && repeated.allRotationsVisited(),"repeated positions share one variable");
            Snapshot branches = measure("!@x @y (p(x) -> q(x)) && (r(y) -> s(y));",2,10000,true);
            require(branches.rotations.size() == 2 && branches.allRotationsVisited(),"every compiled branch is measured");
            for (RotationAudit audit : branches.rotations)
                require(audit.rank == 2 && audit.expected == 4 && audit.visited == 4,"rotation scope spans the whole source rule");
            boolean immutable = false;
            try { branches.rotations.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            require(immutable,"detached audit metadata is immutable");
            boolean stopped = false;
            try { measure("!@x @y p(x,y) -> q(y,x);",2,4,true); }
            catch (IllegalStateException expected) { stopped = expected.getMessage().contains("audit plan limit"); }
            require(stopped,"planned observations have an explicit finite budget");
            actualMatching();
            Mind normal = memory();
            try (ContextProductionCapture capture = ContextProductionCapture.begin(normal,1000)) {
                capture.replay(normal,X,"!@x @y p(x,y) -> q(y,x);");
                normal.setQueryPass(org.kanger.enums.QueryPass.ACCEPT); normal.link(null,false);
                require(!capture.snapshot().rotationsAudited && capture.snapshot().rotations.isEmpty()
                        && !capture.snapshot().allRotationsVisited(),"default capture never invents an audit certificate");
            }
            List<Declaration> local = Collections.singletonList(new Declaration(X,"!@x @y p(x,y);"));
            List<Declaration> target = Collections.singletonList(new Declaration(new Source(new UUID(0,2),1,0,false,"base"),
                    "!@x @y p(x,y) -> q(y,x);"));
            Result result = ContextProductionRoundsReference.inspectWithRotationAudit(local,Collections.singletonList(target),
                    new Limits(16,20000,500000,64));
            require(result.status == Status.INCOMPLETE && result.coverage == Coverage.NONE
                    && result.reason.contains("unproved-symbolic-coverage"),"measuring visits does not widen the structural guarantee: "+result.reason);
            require(result.observations.stream().anyMatch(s -> s.rotationsAudited),"the explicit reference experiment records actual cuts");
            require(result.products.stream().anyMatch(p -> p.clause.literals.size() == 1
                    && p.clause.literals.get(0).predicate.equals("q") && p.clause.variables.size() == 2),"actual generic products remain available");
            Result constrained = ContextProductionRoundsReference.inspectWithRotationAudit(
                    Collections.singletonList(new Declaration(X,"!p(1,John);")),Collections.singletonList(target),
                    new Limits(16,20000,500000,64));
            require(constrained.status == Status.INCOMPLETE && constrained.coverage == Coverage.NONE
                    && constrained.reason.contains("unproved-symbolic-coverage"),
                    "proved private closure cannot upgrade the experiment's structural certificate: "+constrained.reason);
            require(constrained.observations.stream().anyMatch(s -> s.rotationsAudited),
                    "the constrained replay retains its actual audit observations");
            int incomplete = 0, admissions = 0, total = 0;
            for (Snapshot snapshot : result.observations) if (snapshot.rotationsAudited)
                for (RotationAudit audit : snapshot.rotations) {
                    ++total; if (audit.visited != audit.expected) ++incomplete;
                    if (audit.admitted != audit.expected) ++admissions;
                }
            System.out.println("ROTATION_AUDIT_MEASURED branches="+total+" missing_admissions="+admissions+" missing_database="+incomplete);
            System.out.println("CONTEXT_PRODUCTION_ROTATION_AUDIT_PASS checks="+checks);
            System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static void actualMatching() throws Exception {
        Mind mind = memory();
        try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,10000)) {
            capture.replay(mind,X,"!@x @y p(x,y) -> q(y,x);");
            capture.replay(mind,new Source(new UUID(0,1),1,1,true,"base"),"!p(1,John);");
            org.kanger.interfaces.ITerm one = mind.getTerms().add(1), name = mind.getTerms().add("John");
            for (TVariable variable : variables(mind)) {
                mind.getTValues().add(variable,one); mind.getTValues().add(variable,name);
            }
            capture.auditRotations(mind,Arrays.asList(SemanticTermSnapshot.capture(one),SemanticTermSnapshot.capture(name)));
            mind.setQueryPass(org.kanger.enums.QueryPass.ACCEPT); mind.link(null,false);
            RotationAudit audit = capture.snapshot().rotations.get(0);
            require(audit.expected == 4 && audit.visited > 0 && audit.visited < 4,
                    "actual evidence constrains database bindings even when every representative was seeded");
            require(audit.admitted > 0 && audit.admitted <= 4,"terminal admission is measured separately");
            require(!capture.snapshot().allRotationsVisited(),"conservative audit reports the incomplete Cartesian observation");
            System.out.println("ROTATION_MATCHING_MEASURED expected="+audit.expected+" admitted="+audit.admitted+" database="+audit.visited);
        }
        require(Boolean.TRUE.equals(mind.query("?q(John,1);",null,false))
                && mind.query("?q(1,John);",null,false) == null,"missing Cartesian observations do not prove missing logical consequences");
    }
    private static void require(boolean value,String message) { ++checks; if (!value) throw new AssertionError(message); }
}
