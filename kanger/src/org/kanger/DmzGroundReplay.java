/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.kanger.compiler.Token;
import org.kanger.enums.Tools;
import org.kanger.interfaces.IContextResults;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;

/** Checked detached replay input. The caller owns disposal of its private target
 * on failure; this object never grants proof completeness or a branch cut.
 */
final class DmzGroundReplay {
    static final int MAXIMUM_CHARACTERS = 65536;
    final long sourceRule;
    final String statement;
    final DmzGroundIdentity identity;

    private DmzGroundReplay(long id, String statement, DmzGroundIdentity identity) {
        sourceRule = id; this.statement = statement; this.identity = identity;
    }

    static DmzGroundReplay prepare(Mind source, IRule rule) throws Exception {
        if (source == null || rule == null || source.getRules().isGenerated(rule))
            throw new IllegalArgumentException("Native primary source ground required");
        boolean visible = false;
        for (IRule candidate : source.getRules()) visible |= candidate == rule;
        if (!visible || rule.getId() < 0)
            throw new IllegalArgumentException("Ground belongs to another source boundary");
        return prepareCapturedPrimary(source, rule);
    }

    /** Caller has already enumerated this live primary in its bounded source audit. */
    static DmzGroundReplay prepareCapturedPrimary(Mind source, IRule rule) throws Exception {
        DmzGroundIdentity expected = DmzGroundIdentity.capture(rule, source);
        String origin = rule.getOrigin();
        DmzGroundIdentity parsed = parseIdentity(origin);
        if (!expected.equivalent(parsed))
            throw new IllegalArgumentException("DMZ ground replay origin disagrees with native identity");
        return new DmzGroundReplay(rule.getId(), origin, expected);
    }

    /** Native statement extraction and compilation in a fresh dictionary. Nested
     * capture scopes prevent validation from entering the caller's provenance.
     */
    static DmzGroundIdentity parseIdentity(String statement) throws Exception {
        if (statement == null || statement.length() > MAXIMUM_CHARACTERS)
            throw new IllegalArgumentException("Bounded single ground assertion required");
        String text = statement.trim();
        if (!text.startsWith("!"))
            throw new IllegalArgumentException("Ground replay assertion required");
        Token token = null;
        int tokens = 0, depth = 0;
        while ((token = org.kanger.compiler.Parser.nextToken(text, token)) != null) {
            if (++tokens > 1024) throw new IllegalArgumentException("Ground replay token budget exceeded");
            String value = token.getToken(text);
            if (value.equals("(") || value.equals("[")) {
                if (++depth > 32) throw new IllegalArgumentException("Ground replay nesting budget exceeded");
            } else if (value.equals(")") || value.equals("]")) --depth;
        }
        String input = text + "\n";
        Token line = Tools.extractLine(input, null);
        if (line == null || !line.getToken(input).equals(text) || Tools.extractLine(input, line) != null)
            throw new IllegalArgumentException("Exactly one complete ground assertion required");
        User user = new User(); Mind probe = new Mind(user); user.setCurrentMind(probe);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin();
                TerminalSupportCapture capture = TerminalSupportCapture.begin(probe)) {
            Object result = probe.compileLine(text, false, new java.util.LinkedList<ITerm>());
            if (!(result instanceof IRule))
                throw new IllegalArgumentException("Native ground origin reconstruction refused");
            return DmzGroundIdentity.capture((IRule) result, probe);
        }
    }

    /** Checks only the new native input occurrence. Existing/generated atoms are
     * not searched by rendered origin and no generated cache becomes a source.
     */
    void verifyReplay(Mind target, DmzReplayProvenance journal, IContextResults.Revision pin,
            DmzReplayProvenance.Authority authority, int priorBindings) throws Exception {
        journal.requireActiveJournal();
        List<DmzReplayProvenance.Binding> bindings = journal.snapshot();
        if (pin == null || pin.getCommune() != null || !pin.getCommuneMembers().isEmpty()
                || priorBindings < 0 || bindings.size() != priorBindings + 1)
            throw new IllegalStateException("Exactly one new atomic ground replay binding required");
        DmzReplayProvenance.Binding binding = bindings.get(priorBindings);
        if (!binding.context.equals(pin.getContextId()) || binding.revision != pin.getRevision()
                || binding.sourceRule != sourceRule || binding.authority != authority)
            throw new IllegalStateException("Ground replay source identity changed");
        IRule actual = null;
        for (IRule rule : target.getRules())
            if (!rule.isDeleted(target) && rule.getId() == binding.nativeRule) actual = rule;
        if (actual == null || !identity.equivalent(DmzGroundIdentity.capture(actual, target)))
            throw new IllegalStateException("Materialized ground replay identity changed");
        boolean live = false;
        for (DmzReplayProvenance.SourceObservation observed : DmzReplayProvenance.observedSources(target, binding.nativeRule))
            live |= observed.binding == binding && (observed.outcome == DmzReplayProvenance.Outcome.PENDING
                    || observed.outcome == DmzReplayProvenance.Outcome.ACCEPTED);
        if (!live) throw new IllegalStateException("Ground replay source occurrence is not live");
    }

    enum Relation { SAME, OPPOSITE }
    static final class PrimaryMatch {
        final Relation relation;
        final List<DmzReplayProvenance.Binding> sources;
        final boolean pending;
        private PrimaryMatch(Relation relation, List<DmzReplayProvenance.Binding> sources, boolean pending) {
            this.relation = relation;
            this.sources = Collections.unmodifiableList(new ArrayList<DmzReplayProvenance.Binding>(sources));
            this.pending = pending;
        }
    }
    /** Read-only sourced primary matches, never a classification of all proofs.
     * Generated results without an explicit primary occurrence are excluded.
     */
    List<PrimaryMatch> primaryMatches(Mind target, int budget, boolean allowPending) throws Exception {
        if (target == null || budget < 1 || budget > 10000)
            throw new IllegalArgumentException("Bounded primary ground scan required");
        String state = DmzObservationStateFingerprint.capture(target);
        DmzReplayProvenance.SourceCheckpoint checkpoint = DmzReplayProvenance.sourceCheckpoint(target);
        List<PrimaryMatch> matches = new ArrayList<PrimaryMatch>();
        for (IRule rule : target.getRules()) {
            if (--budget < 0) throw new IllegalStateException("Ground primary scan budget exceeded");
            if (rule.isDeleted(target)) continue;
            DmzGroundIdentity candidate;
            try { candidate = DmzGroundIdentity.capture(rule, target); }
            catch (IllegalArgumentException nonGround) { continue; }
            Relation relation = identity.equivalent(candidate) ? Relation.SAME
                    : identity.opposes(candidate) ? Relation.OPPOSITE : null;
            if (relation == null) continue;
            List<DmzReplayProvenance.Binding> sources = new ArrayList<DmzReplayProvenance.Binding>();
            boolean pending = false;
            for (DmzReplayProvenance.SourceObservation observed : DmzReplayProvenance.observedSources(target, rule.getId())) {
                if (--budget < 0) throw new IllegalStateException("Ground primary source budget exceeded");
                if (observed.outcome == DmzReplayProvenance.Outcome.PENDING) {
                    if (!allowPending) throw new IllegalStateException("Pending primary ground is not authorized");
                    pending = true;
                } else if (observed.outcome != DmzReplayProvenance.Outcome.ACCEPTED)
                    throw new IllegalStateException("Unsettled primary ground occurrence");
                sources.add(observed.binding);
            }
            if (sources.isEmpty()) {
                if (!target.getRules().isGenerated(rule))
                    throw new IllegalStateException("Primary ground has no explicit source occurrence");
                continue;
            }
            matches.add(new PrimaryMatch(relation, sources, pending));
        }
        if (!checkpoint.isCurrent(target) || !state.equals(DmzObservationStateFingerprint.capture(target)))
            throw new IllegalStateException("Ground primary boundary changed");
        return Collections.unmodifiableList(matches);
    }
}
