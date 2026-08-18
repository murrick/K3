/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IRule;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Temporary qualification diagnostic for compiled alternative tree shape. */
public final class KangerHypothesisTreeShapeRunner {

    private static final String SOURCE =
            "!@x $y parent(y,x);" +
            "!@x ~parent(x,x);" +
            "!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
            "!@x @y daughter(x,y) -> female(x), child(x,y);" +
            "!@x @y son(x,y) -> male(x), child(x,y);" +
            "!@x @y father(x,y) -> male(x), parent(x,y);" +
            "!@x @y mother(x,y) -> female(x), parent(x,y);" +
            "!@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y));" +
            "!@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y));" +
            "!@x @y ~(parent(x,y), parent(y,x));" +
            "!@x @y ($z parent(z,x) && parent(z,y)) && x != y -> sibling(x,y);" +
            "!@x @y ~(sibling(x,y), parent(x,y));" +
            "!@x @y sibling(x,y) -> sibling(y,x);" +
            "!@x @y ($z parent(x,z), parent(y,z)), x != y -> spouse(x,y) || divorced(x,y);" +
            "!father(John, Tom);" +
            "!daughter(Sarah, John);" +
            "!mother(Mary,Sarah);" +
            "!child(Tom,Mary);" +
            "!age(John, 37);" +
            "!age(Tom, 12);" +
            "!age(Sarah, 4);";

    private KangerHypothesisTreeShapeRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path home = Files.createTempDirectory("kanger-hypothesis-tree-");
            System.setProperty("user.home", home.toAbsolutePath().toString());
            User user = (User) UserFactory.createUser("hypothesis-tree", "hypothesis-tree");
            new UDF().init(user);
            new DB().init(user);
            Mind mind = (Mind) new Mind(user).clearWorkspace();
            if (!mind.compile(SOURCE)) {
                throw new AssertionError("Qualification source compilation rejected");
            }

            boolean found = false;
            for (IRule candidate : mind.getRules()) {
                if (candidate == null || candidate.isDeleted(mind)) {
                    continue;
                }
                Rule rule = (Rule) candidate;
                String origin = rule.getOrigin();
                if (origin == null || !origin.contains("spouse(x,y) || divorced(x,y)")) {
                    continue;
                }
                found = true;
                System.out.printf("HYPOTHESIS_TREE_RULE id=%d branches=%d origin=%s%n",
                        rule.getId(), rule.getTree().size(), origin);
                for (int branchIndex = 0; branchIndex < rule.getTree().size(); ++branchIndex) {
                    List<Domain> branch = rule.getTree().get(branchIndex);
                    System.out.printf("HYPOTHESIS_TREE_BRANCH index=%d size=%d%n",
                            branchIndex, branch.size());
                    for (int domainIndex = 0; domainIndex < branch.size(); ++domainIndex) {
                        Domain domain = branch.get(domainIndex);
                        System.out.printf("HYPOTHESIS_TREE_DOMAIN branch=%d index=%d id=%d predicateId=%d antc=%s text=%s%n",
                                branchIndex, domainIndex, domain.getId(),
                                domain.getPredicateId(), Boolean.toString(domain.isAntc()),
                                domain.toString(mind));
                    }
                }
            }
            if (!found) {
                throw new AssertionError("Target spouse/divorced rule not found");
            }
            System.out.println("HYPOTHESIS_TREE_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }
}
