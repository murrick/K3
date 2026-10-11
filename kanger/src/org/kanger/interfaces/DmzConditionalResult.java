/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.interfaces;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.math.BigInteger;
import org.kanger.enums.DataType;

/** Experimental signed unary-integer DMZ observations. No replay or cut capability.
 * COMPLETE concerns this packet-first traversal, not all consistent theories.
 * Hypotheses remain diagnostic strings and must never be accepted as evidence.
 */
public final class DmzConditionalResult {
    public enum Disposition { QUERIED, BRANCHED, DECLINED_BY_Q, WORK_LIMIT }
    public enum Stop { COMPLETE, DEPTH_LIMIT, NODE_LIMIT, WORK_LIMIT }
    public enum Side { SUPPORTING, OPPOSING }
    public enum PathDisposition { NOT_REQUESTED, QUERIED, NEEDS_BRANCHING, DECLINED_BY_Q }
    public enum ConflictKind { AUTHORITY, INTERNAL }
    public enum CandidateMode { NONE, NEW_INPUT, EXISTING_PRIMARY }
    public enum Policy { PACKET_FIRST_BREADTH_FIRST }
    private static <T> List<T> copy(List<T> values) {
        if (values == null) throw new NullPointerException("values");
        for (T value : values) if (value == null) throw new NullPointerException("element");
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
    public static final class Ground {
        public final String predicate;
        /* true is positive, false is explicitly negative. */
        public final boolean sign;
        public final BigInteger argument;
        public final DataType argumentType;
        public Ground(String predicate, boolean sign, BigInteger argument, DataType type) {
            if (argument == null || type == null) throw new NullPointerException("argument");
            if (predicate == null) throw new NullPointerException("predicate");
            this.predicate = predicate; this.sign = sign; this.argument = argument; argumentType = type;
        }
    }
    /** Source occurrence observation; no native rule or transaction identifier. */
    public static final class Source {
        public final UUID context;
        public final long revision, rule;
        public final boolean targetQ, duplicate;
        public Source(UUID context, long revision, long rule, boolean targetQ, boolean duplicate) {
            if (context == null) throw new NullPointerException("context");
            this.context = context; this.revision = revision; this.rule = rule;
            this.targetQ = targetQ; this.duplicate = duplicate;
        }
    }
    /** One support alternative's source observations, not an executable proof. */
    public static final class Support {
        public final List<Source> accepted, pending;
        public Support(List<Source> accepted, List<Source> pending) {
            this.accepted = copy(accepted); this.pending = copy(pending);
        }
    }
    public static final class Conflict {
        public final Ground ground;
        public final List<Support> supporting, opposing;
        public Conflict(Ground ground, List<Support> supporting, List<Support> opposing) {
            if (ground == null) throw new NullPointerException("ground");
            this.ground = ground; this.supporting = copy(supporting); this.opposing = copy(opposing);
        }
    }
    public static final class Packet {
        public final ConflictKind kind;
        public final List<Conflict> conflicts;
        public Packet(ConflictKind kind, List<Conflict> conflicts) {
            if (kind == null) throw new NullPointerException("kind");
            this.kind = kind; this.conflicts = copy(conflicts);
        }
    }
    public static final class Visibility {
        public final Ground ground;
        public final int proofs;
        public final boolean visible;
        public Visibility(Ground ground, int proofs, boolean visible) {
            if (ground == null) throw new NullPointerException("ground");
            if (proofs < 0) throw new IllegalArgumentException("proofs");
            this.ground = ground; this.proofs = proofs; this.visible = visible;
        }
    }
    public static final class Choice {
        public final Packet before;
        public final Ground conflict;
        public final Side side;
        public final List<Visibility> after;
        public Choice(Packet before, Ground conflict, Side side, List<Visibility> after) {
            if (before == null || conflict == null || side == null) throw new NullPointerException();
            this.before = before; this.conflict = conflict; this.side = side; this.after = copy(after);
        }
    }
    public static final class Path {
        public final Packet root, remaining;
        public final PathDisposition disposition;
        public final List<Choice> choices;
        public Path(Packet root, Packet remaining, PathDisposition disposition, List<Choice> choices) {
            if (root == null || disposition == null) throw new NullPointerException();
            this.root = root; this.remaining = remaining; this.disposition = disposition; this.choices = copy(choices);
        }
    }
    public static final class Answer {
        public final CandidateMode candidateMode;
        /** null means the native query did not determine a Boolean value. */
        public final Boolean value;
        public final List<String> hypotheses, hypothesisAssertions;
        public Answer(CandidateMode mode, Boolean value, List<String> hypotheses, List<String> assertions) {
            if (mode == null) throw new NullPointerException("mode");
            candidateMode = mode; this.value = value;
            this.hypotheses = copy(hypotheses); hypothesisAssertions = copy(assertions);
        }
    }
    public static final class Opinion {
        public final Path path;
        public final Answer answer;
        public Opinion(Path path, Answer answer) {
            if (path == null) throw new NullPointerException("path");
            this.path = path; this.answer = answer;
        }
    }
    public static final class Frontier {
        public final Path path;
        public final Stop reason;
        /** Historical observation, even when recognized during the completed call. */
        public final boolean recognizedInTraversal;
        public Frontier(Path path, Stop reason, boolean recognized) {
            if (path == null || reason == null || reason == Stop.COMPLETE) throw new IllegalArgumentException("frontier");
            this.path = path; this.reason = reason; recognizedInTraversal = recognized;
        }
    }
    public static final class RootFrontier {
        public final Packet packet;
        public final int conflictIndex, completedBranches;
        public final Stop reason;
        public RootFrontier(Packet packet, int index, int completed, Stop reason) {
            if (packet == null || reason == null || reason == Stop.COMPLETE || index < 0
                    || index >= packet.conflicts.size() || completed < 0 || completed > 2)
                throw new IllegalArgumentException("root frontier");
            this.packet = packet; conflictIndex = index; completedBranches = completed; this.reason = reason;
        }
    }
    /** Visit counters are not CPU time; family allowance is the remaining outer allowance. */
    public static final class Family {
        public final Policy policy = Policy.PACKET_FIRST_BREADTH_FIRST;
        public final Stop outcome;
        public final List<Opinion> opinions, declined;
        public final List<Frontier> frontier;
        public final RootFrontier unstartedRoot;
        public final int traversalWork, generatedNodes, maximumDepth, maximumNodes, workLimit;
        public Family(Stop outcome, List<Opinion> opinions, List<Opinion> declined, List<Frontier> frontier,
                RootFrontier root, int work, int nodes, int depth, int maximumNodes, int limit) {
            if (outcome == null || work < 0 || limit < work || nodes < 0 || depth < 1 || maximumNodes < nodes
                    || maximumNodes < 1) throw new IllegalArgumentException("family metadata");
            this.opinions = copy(opinions); this.declined = copy(declined); this.frontier = copy(frontier);
            if (outcome == Stop.COMPLETE && (!this.frontier.isEmpty() || root != null))
                throw new IllegalArgumentException("complete family has frontier");
            this.outcome = outcome; unstartedRoot = root; traversalWork = work; generatedNodes = nodes;
            maximumDepth = depth; this.maximumNodes = maximumNodes; workLimit = limit;
        }
    }
    public final Disposition disposition;
    public final Answer direct;
    public final Family family;
    public final Packet authorityConflict;
    public final boolean retryDiscovery, restartRequired;
    public final String candidate, queryStatement;
    public final IContextResults.Revision source;
    public final long sourceRule;
    public final int traversalWork, maximumDepth, maximumNodes, workLimit;
    public DmzConditionalResult(Disposition disposition, Answer direct, Family family, Packet authorityConflict,
            boolean retryDiscovery, boolean restartRequired, String candidate, String query,
            IContextResults.Revision source, long rule, int work, int depth, int nodes, int limit) {
        if (disposition == null || candidate == null || query == null || source == null) throw new NullPointerException();
        if ((disposition == Disposition.QUERIED) != (direct != null)
                || (disposition == Disposition.BRANCHED) != (family != null)
                || (authorityConflict != null && (disposition != Disposition.DECLINED_BY_Q
                    || authorityConflict.kind != ConflictKind.AUTHORITY))
                || retryDiscovery != (disposition == Disposition.WORK_LIMIT)
                || work < 0 || limit < work || depth < 1 || nodes < 1)
            throw new IllegalArgumentException("conditional result metadata");
        this.disposition = disposition; this.direct = direct; this.family = family; this.authorityConflict = authorityConflict;
        this.retryDiscovery = retryDiscovery; this.restartRequired = restartRequired;
        this.candidate = candidate; queryStatement = query; this.source = source; sourceRule = rule;
        traversalWork = work; maximumDepth = depth; maximumNodes = nodes; workLimit = limit;
    }
}
