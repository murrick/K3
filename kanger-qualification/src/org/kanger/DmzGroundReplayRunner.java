/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.interfaces.IRule;
import org.kanger.units.Rule;
import org.kanger.units.Term;

/** Checked origin/replay and sourced primary matching, not general proof coverage. */
public final class DmzGroundReplayRunner {
    private static int checks;
    private interface Attempt { void run() throws Exception; }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home", Files.createTempDirectory("dmz-ground-replay-").toString());
            for (String atom : new String[]{"father(John,Tom)", "marker(John)", "marker(\"John\")",
                    "marker(1)", "marker(2.5)", "marker(\"1\")", "ready()", "family([])",
                    "family([John,Tom,Sarah])", "tuple(John,4,\"Tom\",[Mary,Sarah])", "marker(\"a;b\")"}) {
                replay(atom, false); replay(atom, true);
            }
            originRefusals(); generatedCache();
            System.out.println("DMZ_GROUND_REPLAY_PASS checks=" + checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static Mind source(String statement) throws Exception {
        User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind);
        require(mind.compile(statement, null, false), "native source accepted"); return mind;
    }
    private static IRule primary(Mind mind, String statement) throws Exception {
        for (IRule rule : mind.getRules())
            if (!mind.getRules().isGenerated(rule) && statement.equals(rule.getOrigin())) return rule;
        throw new AssertionError("Source primary absent: " + statement);
    }
    private static IContextResults.Revision pin(long revision) {
        return new IContextResults.Revision(UUID.randomUUID(), revision);
    }
    private static void replay(String atom, boolean negative) throws Exception {
        String statement = "!" + (negative ? "~" : "") + atom + ";";
        String opposite = "!" + (negative ? "" : "~") + atom + ";";
        Mind nativeSource = source(statement), opposingSource = source(opposite), q = source("!authority(1);");
        String sourceState = DmzObservationStateFingerprint.capture(nativeSource), qState = DmzObservationStateFingerprint.capture(q);
        IContextResults.Revision a = pin(7), alias = pin(12), b = pin(20);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin();
                TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            TerminalSupportCapture.Checkpoint outer = capture.baselineCheckpoint(q);
            int baseline = journal.snapshot().size();
            DmzGroundReplay ticket = DmzGroundReplay.prepare(nativeSource, primary(nativeSource, statement));
            DmzGroundReplay opposition = DmzGroundReplay.prepare(opposingSource, primary(opposingSource, opposite));
            require(capture.isCurrent(outer, q) && journal.snapshot().size() == baseline,
                    "origin validation restores outer capture and journal without events");
            require(ticket.identity.opposes(opposition.identity), "checked origins preserve typed opposition");
            require(ticket.sourceRule == primary(nativeSource, statement).getId(), "source-local ID detached exactly");
            try (TechnicalMindTransaction branch = TechnicalMindTransaction.beginIsolated(q)) {
                Mind target = branch.mind();
                int prior = journal.snapshot().size();
                require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(target, a, ticket.sourceRule,
                        DmzReplayProvenance.Authority.EXTERNAL, ticket.statement)), "native sourced replay accepted");
                ticket.verifyReplay(target, journal, a, DmzReplayProvenance.Authority.EXTERNAL, prior); ++checks;
                final int firstPrior=prior;
                reject(() -> ticket.verifyReplay(target, journal, b, DmzReplayProvenance.Authority.EXTERNAL, firstPrior));
                reject(() -> ticket.verifyReplay(target, journal, new IContextResults.Revision(a.getContextId(), 8),
                        DmzReplayProvenance.Authority.EXTERNAL, firstPrior));
                require(Boolean.valueOf(!negative).equals(target.query("?" + atom + ";", null, false)), "replayed native answer");
                String targetState = DmzObservationStateFingerprint.capture(target);
                java.util.List<DmzGroundReplay.PrimaryMatch> same = ticket.primaryMatches(target, 10000, true);
                java.util.List<DmzGroundReplay.PrimaryMatch> different = opposition.primaryMatches(target, 10000, true);
                require(same.size() == 1 && same.get(0).relation == DmzGroundReplay.Relation.SAME
                        && same.get(0).pending && same.get(0).sources.size() == 1, "same typed primary has pending exact source");
                require(different.size() == 1 && different.get(0).relation == DmzGroundReplay.Relation.OPPOSITE,
                        "opposing typed primary found without display parsing");
                require(targetState.equals(DmzObservationStateFingerprint.capture(target)), "primary scan is read-only");
                reject(() -> ticket.primaryMatches(target, 10000, false));
                reject(() -> ticket.primaryMatches(target, 1, true));
                try { same.clear(); throw new AssertionError("Mutable primary match list"); }
                catch (UnsupportedOperationException expected) { ++checks; }
                prior = journal.snapshot().size();
                DmzReplayProvenance.replayRule(target, alias, ticket.sourceRule,
                        DmzReplayProvenance.Authority.EXTERNAL, ticket.statement);
                require(journal.snapshot().size()==prior+1, "native replay retains a second exact source occurrence");
                ticket.verifyReplay(target, journal, alias, DmzReplayProvenance.Authority.EXTERNAL, prior); ++checks;
                same = ticket.primaryMatches(target, 10000, true);
                require(same.size() == 1 && same.get(0).sources.size() == 2
                        && !same.get(0).sources.get(0).context.equals(same.get(0).sources.get(1).context),
                        "semantic equality never merges distinct source occurrences");
                targetState = DmzObservationStateFingerprint.capture(target);
                require(!Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(target, b, opposition.sourceRule,
                        DmzReplayProvenance.Authority.EXTERNAL, opposition.statement)), "opposing acceptance refuses atomically");
                require(targetState.equals(DmzObservationStateFingerprint.capture(target)), "refusal preserves existing typed primary");
            }
            require(journal.settlementSnapshot().accepted.isEmpty(), "rollback promotes no ground source into Q");
            capture.requireActive(); journal.requireActiveJournal(); ++checks;
        }
        require(sourceState.equals(DmzObservationStateFingerprint.capture(nativeSource)), "checked replay leaves source unchanged");
        require(qState.equals(DmzObservationStateFingerprint.capture(q)), "checked replay leaves Q unchanged");
    }
    private static void originRefusals() throws Exception {
        Mind original = source("!father(John,Tom);"); Rule rule = (Rule) primary(original, "!father(John,Tom);");
        for (String wrong : new String[]{"!father(Tom,John);", "!~father(John,Tom);", "!mother(John,Tom);",
                "!father(John);", "!father(John,Tom); !extra(1);", "!father(John,Tom); trailing",
                "?father(John,Tom);", "!@x father(x,Tom);", "!@x p(x) -> q(x);"}) {
            rule.setOrigin(new Term(wrong, original));
            reject(() -> DmzGroundReplay.prepare(original, rule));
        }
        rule.setOrigin(new Term("!father(\"John\", \"Tom\");", original));
        require(DmzGroundReplay.prepare(original, rule).identity.equivalent(DmzGroundIdentity.capture(rule, original)),
                "native-equivalent origin spelling accepted");
        rule.setDeleted(true, original); reject(() -> DmzGroundReplay.prepare(original, rule)); rule.setDeleted(false, original);
        Mind other = source("!father(John,Tom);"); reject(() -> DmzGroundReplay.prepare(other, rule));
        reject(() -> DmzGroundReplay.parseIdentity("!marker(1)"));
        reject(() -> DmzGroundReplay.parseIdentity("!p(1); !p(1);"));
        reject(() -> DmzGroundReplay.parseIdentity("!marker(" + String.join("", java.util.Collections.nCopies(33,"["))
                + "1" + String.join("", java.util.Collections.nCopies(33,"]")) + ");"));
        reject(() -> DmzGroundReplay.parseIdentity("!marker(\"" + String.join("", java.util.Collections.nCopies(65536,"x")) + "\");"));
    }
    private static void generatedCache() throws Exception {
        Mind ground = source("!child(Tom,John);");
        DmzGroundReplay ticket = DmzGroundReplay.prepare(ground, primary(ground, "!child(Tom,John);"));
        Mind q = source("!authority(1);");
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin();
                TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, pin(12), 3,
                    DmzReplayProvenance.Authority.EXTERNAL, "!@x @y father(x,y) -> child(y,x);")), "binary Q production accepted");
            try (TechnicalMindTransaction branch = TechnicalMindTransaction.beginIsolated(q)) {
                Mind target = branch.mind();
                require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(target, pin(7), 3,
                        DmzReplayProvenance.Authority.EXTERNAL, "!father(John,Tom);")), "binary donor accepted");
                require(Boolean.TRUE.equals(target.query("?child(Tom,John);", null, false)), "generated atom exists");
                require(ticket.primaryMatches(target, 10000, true).isEmpty(), "generated cache does not impersonate a primary source");
                IRule generated = null;
                for (IRule item : target.getRules()) if (target.getRules().isGenerated(item) && !item.isDeleted(target)) generated = item;
                final IRule cached = generated;
                require(cached != null, "native generated cache present");
                reject(() -> DmzGroundReplay.prepare(target, cached));
                IContextResults.Revision explicit = pin(20);
                int prior = journal.snapshot().size();
                DmzReplayProvenance.replayRule(target, explicit, ticket.sourceRule,
                        DmzReplayProvenance.Authority.EXTERNAL, ticket.statement);
                require(journal.snapshot().size()==prior+1, "explicit replay records a primary alias of a generated atom");
                ticket.verifyReplay(target, journal, explicit, DmzReplayProvenance.Authority.EXTERNAL, prior); ++checks;
                require(ticket.primaryMatches(target, 10000, true).get(0).sources.size() == 1,
                        "only explicit new source becomes a primary occurrence");
            }
        }
    }
    private static void reject(Attempt attempt) throws Exception {
        try { attempt.run(); throw new AssertionError("Expected ground replay refusal"); }
        catch (IllegalArgumentException | IllegalStateException expected) { ++checks; }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); ++checks;
    }
}
