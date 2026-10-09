/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import java.util.ArrayList;
import java.util.List;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.internal.IContextFederation;

/** Presentation of context revisions, opinions and proof provenance in the Console. */
final class ConsoleContextView {

    private ConsoleContextView() {
    }

    static void showContextExplain(
            IContextFederation.ExplainResult explain) {
        if (explain == null) {
            return;
        }
        IContextFederation.Snapshot snapshot =
                explain.getContext();

        System.out.printf(
                "Context %s@%d [explain]%n",
                snapshot.getSourceLocator(),
                snapshot.getSourceRevision());
        if (snapshot.getConnections().isEmpty()) {
            System.out.println("Pins: none");
        } else {
            System.out.println("Pins:");
            for (IContextFederation.Connection connection
                    : snapshot.getConnections()) {
                System.out.printf(
                        "  %s@%d%n",
                        connection.getLocator(),
                        connection.getPinnedRevision());
            }
        }

        System.out.println(
                "Local X: " + explain.getLocalTruth());

        for (IContextFederation.ExplainPass pass
                : explain.getPasses()) {
            IContextFederation.QueryResult continuation =
                    pass.getContinuation();
            System.out.println(
                    pass.getPolarity()
                            == IContextFederation.ExplainPolarity.FALSE_PASS
                            ? "FALSE pass:"
                            : "TRUE pass:");

            for (IContextFederation.FrontierObservation observation
                    : continuation.getObservations()) {
                System.out.printf(
                        "  wave %d  %s  => %s%n",
                        observation.getWave(),
                        observation.getQuerySource(),
                        observation.getTruth());
                showRevisionSources(
                        "TRUE",
                        observation.getTrueSources(),
                        snapshot);
                showRevisionSources(
                        "FALSE",
                        observation.getFalseSources(),
                        snapshot);
                showRevisionSources(
                        "UNKNOWN",
                        observation.getUnknownSources(),
                        snapshot);
            }

            for (IContextFederation.CausalStep step : continuation.getCausalSteps()) {
                System.out.printf("  causal wave %d  %s  %s => %s%n", step.getWave(),
                        sourceLabel(snapshot, step.getTarget()), step.getQuery(), step.getTruth());
                for (IContextFederation.EvidenceInjection fact : step.getSuppliedEvidence()) {
                    System.out.println("    supplied: " + fact.getStatement());
                    showRevisionSources("supports", fact.getSupports(), snapshot);
                }
                for (IContextFederation.CausalDemand demand : step.getDemands()) {
                    System.out.println("    needs " + demand.getChildQuery() + " for " + demand.getParentQuery());
                    StringBuilder mapping = new StringBuilder("    parent arguments <- child arguments: ");
                    for (int i = 0; i < demand.getParentToChild().size(); ++i) {
                        if (i > 0) mapping.append(", ");
                        int child = demand.getParentToChild().get(i);
                        mapping.append(i + 1).append(" <- ").append(child < 0 ? "local" : Integer.toString(child + 1));
                    }
                    System.out.println(mapping.toString());
                }
                for (IContextFederation.ValueRow row : step.getValues()) {
                    StringBuilder bindings = new StringBuilder("    native bindings: ");
                    for (java.util.Map.Entry<String, String> binding : row.getBindings().entrySet()) {
                        if (bindings.length() > "    native bindings: ".length()) bindings.append(", ");
                        bindings.append('$').append(binding.getKey()).append(" <- ").append(binding.getValue());
                    }
                    System.out.println(bindings.toString());
                }
            }

            for (IContextFederation.EvidenceInjection injection
                    : continuation.getEvidenceInjections()) {
                StringBuilder line =
                        new StringBuilder(
                                "  inject into X: ")
                                .append(
                                        injection.getStatement());
                if (!injection.getSubstitutions()
                        .isEmpty()) {
                    line.append("  ");
                    boolean first = true;
                    for (java.util.Map.Entry<String, String> binding
                            : injection.getSubstitutions()
                                    .entrySet()) {
                        if (!first) {
                            line.append(", ");
                        }
                        line.append('$')
                                .append(binding.getKey())
                                .append(" <- ")
                                .append(binding.getValue());
                        first = false;
                    }
                }
                if (!injection.getSupports().isEmpty()) {
                    line.append("  [");
                    boolean first = true;
                    for (IContextFederation.Revision support
                            : injection.getSupports()) {
                        if (!first) {
                            line.append(", ");
                        }
                        line.append(sourceLabel(snapshot, support));
                        first = false;
                    }
                    line.append(']');
                }
                System.out.println(line.toString());
            }

            System.out.printf(
                    "  continuation X: %s, waves=%d, evidence=%d%n",
                    continuation.isResolved()
                            ? "RESOLVED"
                            : "UNRESOLVED",
                    continuation.getWaves(),
                    continuation.getEvidenceCount());
        }

        System.out.println(
                "Final: " + explain.getFinalTruth());

        if (!explain.getValues().isEmpty()) {
            System.out.println("Values:");
            for (IContextFederation.ValueRow row
                    : explain.getValues()) {
                StringBuilder line =
                        new StringBuilder("  ");
                boolean first = true;
                for (java.util.Map.Entry<String, String> binding
                        : row.getBindings().entrySet()) {
                    if (!first) {
                        line.append(", ");
                    }
                    line.append('$')
                            .append(binding.getKey())
                            .append(" <- ")
                            .append(binding.getValue());
                    first = false;
                }
                System.out.println(line.toString());
            }
        }

        if (!explain.getSolutions().isEmpty()) {
            System.out.println("Solutions:");
            for (String solution
                    : explain.getSolutions()) {
                System.out.println(
                        "  " + solution);
            }
        }
    }

    static void showContextVersion(
            IContextFederation.VersionHistory history) {
        if (history == null) {
            return;
        }
        System.out.println(
                "Context " + history.getLocator());
        if (history.hasPinnedRevision()) {
            System.out.println(
                    "Pinned: "
                            + history.getPinnedRevision());
        }
        System.out.println(
                "Current: "
                        + history.getCurrentRevision());
        System.out.println();
        System.out.println(
                "Revision   Description");
        for (IContextFederation.RevisionVersion revision
                : history.getRevisions()) {
            StringBuilder markers =
                    new StringBuilder();
            if (revision.getRevision()
                    == history.getCurrentRevision()) {
                markers.append(" [CURRENT]");
            }
            if (history.hasPinnedRevision()
                    && revision.getRevision()
                            == history.getPinnedRevision()) {
                markers.append(" [PINNED]");
            }
            System.out.printf(
                    "%-10d %s%s%n",
                    revision.getRevision(),
                    revision.getDescription(),
                    markers.toString());
        }
    }

    static void showContextOpinions(java.util.Map<String, IContextFederation.Opinion> opinions,
            org.kanger.command.CommandIntent intent, IContextFederation.Snapshot snapshot) {
        if (opinions.isEmpty()) System.out.println("No meaningful source opinions");
        boolean first = true;
        for (IContextFederation.Opinion opinion : opinions.values()) {
            if (!first) System.out.println();
            first = false;
            if (opinion.getSource().getCommune() != null) {
                System.out.println("Commune " + opinion.getSource().getCommune() + " [joint opinion, "
                        + (opinion.isConfigured() ? "configured by X" : "pinned") + "]");
                for (IContextFederation.Revision member : opinion.getSource().getCommuneMembers())
                    System.out.println("  " + sourceLabel(snapshot, member));
            }
            else System.out.printf("Context %s@%d [%s, isolated opinion]%n", opinion.getLocator(),
                    opinion.getSource().getRevision(), opinion.isWorking() ? "live X"
                            : opinion.isConfigured() ? "pinned, configured by X" : "pinned");
            System.out.println("Result: " + opinion.getResult().getResultTruth());
            if ((intent == org.kanger.command.CommandIntent.CTX_OPINIONS && opinion.getResult().isResolved()) || intent == org.kanger.command.CommandIntent.CTX_SOLVES) {
                System.out.println("Solutions (" + opinion.getSolutions().size() + "):");
                for (IContextFederation.RuleRow solution : opinion.getSolutions()) {
                    System.out.println("  Solution " + solution.id + ": " + solution.statement);
                    if (intent == org.kanger.command.CommandIntent.CTX_SOLVES)
                        showOpinionCauses(solution.causes, "    ", opinion.getLocator());
                }
            }
            if ((intent == org.kanger.command.CommandIntent.CTX_OPINIONS && opinion.getResult().isResolved()) || intent == org.kanger.command.CommandIntent.CTX_VALUES) {
                System.out.println("Values (" + opinion.getResult().getValues().size() + "):");
                for (IContextFederation.ValueRow row : opinion.getResult().getValues()) System.out.println("  " + row.getBindings());
            }
            if (intent == org.kanger.command.CommandIntent.CTX_OPINIONS || intent == org.kanger.command.CommandIntent.CTX_WHEN) {
                System.out.println("Hypotheses (" + opinion.getResult().getProvisionalHypotheses().size() + "):");
                for (IContextFederation.ProvisionalHypothesis hypothesis : opinion.getResult().getProvisionalHypotheses())
                    System.out.println("  " + hypothesis.getStatement());
            }
        }
    }

    static boolean hasContextProof(List<IContextFederation.ProofCause> causes) {
        for (IContextFederation.ProofCause cause : causes)
            if (cause.commune != null || cause.contextSource != null || hasContextProof(cause.causes)) return true;
        return false;
    }

    static void showProofCauses(List<IContextFederation.ProofCause> causes, String indent, IMind mind) throws Exception {
        for (IContextFederation.ProofCause cause : causes) {
            if (cause.commune != null) {
                System.out.println(indent + "Commune " + cause.commune);
                for (IContextFederation.Revision member : cause.communeMembers)
                    System.out.println(indent + "  Member: " + member.getContextId() + "@" + member.getRevision());
                System.out.println(indent + "  Statement: " + cause.ruleStatement);
            } else if (cause.contextSource != null) {
                String locator = cause.contextSource.getContextId().toString();
                if (((User) mind.getUser()).getData() instanceof IContextFederation) {
                    IContextFederation.Snapshot snapshot = ((IContextFederation) ((User) mind.getUser()).getData()).federationSnapshot();
                    if (snapshot.getSourceContextId().equals(cause.contextSource.getContextId())) locator = snapshot.getSourceLocator();
                    for (IContextFederation.Connection connection : snapshot.getConnections())
                        if (connection.getTargetContextId().equals(cause.contextSource.getContextId())) locator = connection.getLocator();
                }
                System.out.println(indent + "Context " + locator + "@" + cause.contextSource.getRevision()
                        + (cause.configuredByX ? " [configured by X]" : ""));
                System.out.println(indent + "  Statement: " + cause.ruleStatement);
            } else {
                System.out.println(indent + "Rule " + cause.ruleId + ": " + cause.ruleStatement);
                System.out.println(indent + "Donor: " + cause.donorStatement + (cause.cycle ? " [cycle]" : ""));
            }
            showProofCauses(cause.causes, indent + "    ", mind);
        }
    }

    private static void showOpinionCauses(List<IContextFederation.ProofCause> causes, String indent, String locator) {
        for (IContextFederation.ProofCause cause : causes) {
            System.out.println(indent + "Rule " + cause.ruleId + ": " + cause.ruleStatement);
            System.out.println(indent + "Donor: " + cause.donorStatement + (cause.cycle ? " [cycle]" : cause.hypothesis ? " [hypothesis, not proven]" : ""));
            if (cause.hypothesis) {
                System.out.println(indent + "    Hypothesis: " + locator + "@" + cause.hypothesisSource.getRevision());
                System.out.println(indent + "    Introduced by: " + locator + "@" + cause.hypothesisSource.getRevision() + " Rule " + cause.ruleId);
                System.out.println(indent + "    Required for: " + cause.requiredFor);
            }
            showOpinionCauses(cause.causes, indent + "    ", locator);
        }
    }

    static void showIsolatedContextQuery(
            IContextFederation.Snapshot snapshot,
            IContextFederation.QueryResult query,
            String locator) {
        if (query == null) {
            return;
        }
        long revision = isolatedRevision(snapshot, locator);
        boolean configured = false;
        for (IContextFederation.Connection connection : snapshot.getConnections())
            if (connection.getLocator().equals(locator)) configured = !connection.getInitialization().isEmpty();
        System.out.printf("Context %s%s [%s]%n", locator, revision < 0 ? "" : "@" + revision,
                configured ? "isolated, configured by X" : "isolated");
        System.out.println("Result: " + query.getResultTruth());

        if (!query.getValues().isEmpty()) {
            System.out.println("Values:");
            for (IContextFederation.ValueRow row : query.getValues()) {
                StringBuilder line = new StringBuilder("  ");
                boolean first = true;
                for (java.util.Map.Entry<String, String> binding
                        : row.getBindings().entrySet()) {
                    if (!first) {
                        line.append(", ");
                    }
                    line.append("$")
                            .append(binding.getKey())
                            .append(" = ")
                            .append(binding.getValue());
                    first = false;
                }
                System.out.println(line.toString());
            }
        }

        if (!query.getProvisionalHypotheses().isEmpty()) {
            System.out.println("Hypotheses:");
            for (IContextFederation.ProvisionalHypothesis hypothesis
                    : query.getProvisionalHypotheses()) {
                System.out.println("  " + hypothesis.getStatement());
            }
        }
    }

    private static long isolatedRevision(
            IContextFederation.Snapshot snapshot,
            String locator) {
        if (snapshot.getSourceLocator().equals(locator)) {
            return snapshot.getSourceRevision();
        }
        for (IContextFederation.Connection connection
                : snapshot.getConnections()) {
            if (connection.getLocator().equals(locator)) {
                return connection.getPinnedRevision();
            }
        }
        return -1L;
    }
    static void showContextRules(java.util.List<IContextFederation.RuleBlock> blocks,String selection, IContextFederation.Snapshot snapshot) {
        boolean first = true;
        for(IContextFederation.RuleBlock block:blocks) {
            if (block.revision.getCommune() == null
                    && !block.revision.getContextId().equals(snapshot.getSourceContextId())) {
                boolean represented = false;
                for (IContextFederation.RuleBlock layer : blocks) {
                    if (layer.revision.getCommune() == null) continue;
                    for (IContextFederation.Revision member : layer.revision.getCommuneMembers()) {
                        if (member.getContextId().equals(block.revision.getContextId())
                                && member.getRevision() == block.revision.getRevision()) {
                            represented = true;
                            break;
                        }
                    }
                    if (represented) break;
                }
                if (represented) continue;
            }
            if (!first) System.out.println();
            first = false;
            if (block.revision.getCommune() != null) {
                System.out.println("Commune " + block.revision.getCommune() + " [technical layer"
                        + (block.configured ? ", configured by X" : "") + "]");
                for (IContextFederation.Revision member : block.revision.getCommuneMembers())
                    System.out.println("  " + sourceLabel(snapshot, member));
            } else System.out.printf("Context %s@%d [%s]%n",block.locator,block.revision.getRevision(),block.working?"live X":block.configured?"pinned, configured by X":"pinned");
            if(block.rules.isEmpty()) System.out.println("No rules selected");
            for(IContextFederation.RuleRow rule:block.rules) {
                System.out.printf("Rule %03d%s: %s%n",rule.id,rule.generated?" G":"",rule.statement);
                if("COMMENT".equals(selection)) System.out.println(rule.comment);
                for(java.util.List<String> row:rule.tree) System.out.println(String.join(" ",row));
            }
        }
    }

    static void showFederation(
            IContextFederation.Snapshot snapshot,
            IContextFederation.QueryResult query) {
        System.out.printf("Context %s@%d%n",
                snapshot.getSourceLocator(),
                snapshot.getSourceRevision());
        System.out.println(snapshot.hasWorkingChanges()?"Connections: working changes [not saved; use ctx publish]":"Connections: saved");
        for(IContextFederation.DependencyNotice notice:snapshot.getDependencyNotices()) {
            System.out.printf("%s@%d declares %s@%d: %s%s%n",notice.owner.getLocator(),notice.owner.getRevision(),
                    notice.dependency.getLocator(),notice.dependency.getRevision(),notice.getStatus(),
                    notice.actualRevision==null?" — you may connect it to expand available knowledge":
                            notice.getStatus().equals("DIFFERENT_REVISION")?" [connected @"+notice.actualRevision+"]":"");
        }
        if (snapshot.getConnections().isEmpty()) {
            System.out.println("Direct connections: none");
        } else {
            System.out.println("Direct connections:");
            for (IContextFederation.Connection connection
                    : snapshot.getConnections()) {
                System.out.printf(
                        "  %s@%d  %-10s  %s%s%n",
                        connection.getLocator(),
                        connection.getPinnedRevision(),
                        connection.getCompatibilityStatus(),
                        connection.getPinPolicy(),
                        connection.hasNewerRevision()
                                ? "  [CURRENT="
                                        + connection.getCurrentRevision()
                                        + "]"
                                : "");
                if (connection.getTrustGroup() != null)
                    System.out.println("    Trust commune: " + connection.getTrustGroup());
                if (!connection.getInitialization().isEmpty())
                    System.out.println("    Configured by X: " + connection.getInitialization().size()
                            + " initialization command(s)");
            }
        }

        if (query == null) {
            return;
        }
        System.out.printf(
                "Federated query: %s, waves=%d, evidence=%d%n",
                query.isResolved() ? "RESOLVED" : "UNRESOLVED",
                query.getWaves(),
                query.getEvidenceCount());
        for (IContextFederation.FrontierObservation observation
                : query.getObservations()) {
            System.out.printf("  wave %d  %s  => %s%n",
                    observation.getWave(),
                    observation.getQuerySource(),
                    observation.getTruth());
            showRevisionSources("TRUE",
                    observation.getTrueSources(), snapshot);
            showRevisionSources("FALSE",
                    observation.getFalseSources(), snapshot);
            showRevisionSources("UNKNOWN",
                    observation.getUnknownSources(), snapshot);
        }
        if (!query.getProvisionalHypotheses().isEmpty()) {
            System.out.println("  provisional hypotheses:");
            for (IContextFederation.ProvisionalHypothesis hypothesis
                    : query.getProvisionalHypotheses()) {
                System.out.printf("    %s  %s%n", sourceLabel(snapshot, hypothesis.getSource()),
                        hypothesis.getStatement());
            }
        }
    }

    private static void showRevisionSources(
            String label,
            List<IContextFederation.Revision> revisions,
            IContextFederation.Snapshot snapshot) {
        for (IContextFederation.Revision revision : revisions) {
            System.out.printf("      %s: %s%n", label, sourceLabel(snapshot, revision));
        }
    }

    private static String sourceLabel(IContextFederation.Snapshot snapshot, IContextFederation.Revision source) {
        if (source.getCommune() == null)
            return contextLocator(snapshot, source.getContextId()) + "@" + source.getRevision();
        List<String> members = new ArrayList<>();
        for (IContextFederation.Revision member : source.getCommuneMembers())
            members.add(contextLocator(snapshot, member.getContextId()) + "@" + member.getRevision());
        return "commune " + source.getCommune() + " [" + String.join(", ", members) + "]";
    }

    private static String contextLocator(
            IContextFederation.Snapshot snapshot,
            java.util.UUID contextId) {
        if (snapshot.getSourceContextId().equals(contextId)) {
            return snapshot.getSourceLocator();
        }
        for (IContextFederation.Connection connection
                : snapshot.getConnections()) {
            if (connection.getTargetContextId().equals(contextId)) {
                return connection.getLocator();
            }
        }
        return "<unknown-context>";
    }

}
