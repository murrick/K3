/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.interfaces.IContextResults;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Text-indexed diagnostic source candidates; never authoritative application ownership. */
final class DmzSourceCandidates {
    enum Status { UNRESOLVED, SINGLE_CANDIDATE, MULTIPLE_CANDIDATES }
    static final class Source {
        final UUID context;
        final long revision;
        final long rule;
        final boolean configured;
        Source(IContextResults.ProofCause cause) {
            context = cause.contextSource.getContextId(); revision = cause.contextSource.getRevision();
            rule = cause.ruleId; configured = cause.configuredByX;
        }
        boolean same(Source other) {
            return context.equals(other.context) && revision == other.revision
                    && rule == other.rule && configured == other.configured;
        }
    }
    final List<Source> sources;
    final Status status;
    final int unsupported;
    private DmzSourceCandidates(List<Source> sources, int unsupported) {
        this.sources = Collections.unmodifiableList(new ArrayList<Source>(sources));
        this.unsupported = unsupported;
        status = sources.isEmpty() ? Status.UNRESOLVED
                : sources.size() == 1 ? Status.SINGLE_CANDIDATE : Status.MULTIPLE_CANDIDATES;
    }
    static DmzSourceCandidates unknown() { return new DmzSourceCandidates(Collections.<Source>emptyList(), 0); }
    static DmzSourceCandidates capture(Mind mind, String origin) {
        List<Source> sources = new ArrayList<Source>(); int unsupported = 0;
        for (IContextResults.ProofCause cause : mind.getContextRuleOrigins(origin)) {
            // Joint routing identity, hypothesis and missing authorship are not exact atomic sources.
            if (cause.contextSource == null || cause.hypothesis || cause.commune != null
                    || !cause.communeMembers.isEmpty() || cause.contextSource.getCommune() != null
                    || !cause.contextSource.getCommuneMembers().isEmpty()) { ++unsupported; continue; }
            Source source = new Source(cause); boolean duplicate = false;
            for (Source previous : sources) if (previous.same(source)) { duplicate = true; break; }
            if (!duplicate) sources.add(source);
        }
        return new DmzSourceCandidates(sources, unsupported);
    }
}
