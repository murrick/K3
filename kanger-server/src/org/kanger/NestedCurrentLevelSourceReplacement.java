/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.LogMode;
import org.kanger.factory.CommentFactory;
import org.kanger.interfaces.IOperation;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.IUser;
import org.kanger.units.Comment;
import org.kanger.units.Operation;
import org.kanger.units.Rule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Atomically replaces source-representable content of one nested explicit U_n.
 *
 * <p>The candidate is a temporary sibling of the published current Mind. It is
 * rebuilt from U_{n-1}, receives only transaction-control delta that has no
 * standalone source representation, then compiles the new Editor source. The
 * old sibling remains published until the candidate is fully accepted.</p>
 */
final class NestedCurrentLevelSourceReplacement {

    private NestedCurrentLevelSourceReplacement() {
    }

    static Outcome replace(IUser user, String exactSource) throws Exception {
        if (user == null || user.getCurrentMind() == null) {
            throw new IllegalArgumentException("Current user Mind is required");
        }
        Mind current = (Mind) user.getCurrentMind();
        if (current.getTransactionLevel() <= 0 || current.getNext() == null) {
            throw new IllegalArgumentException(
                    "Nested source replacement requires transaction level above U0");
        }

        Mind parent = (Mind) current.getNext();
        ControlDelta control = ControlDelta.capture(parent, current);
        Mind candidate = new Mind(parent);
        boolean candidateReservationOpen = true;
        try {
            control.apply(candidate);

            Boolean compiled = exactSource != null && exactSource.isEmpty()
                    ? Boolean.TRUE
                    : candidate.compile(exactSource == null ? "" : exactSource);
            String description = analyzerDescription(candidate);
            if (!Boolean.TRUE.equals(compiled)) {
                parent.release(candidate);
                candidateReservationOpen = false;
                return new Outcome(false, description, current);
            }

            parent.release(current);
            user.setCurrentMind(candidate);
            candidateReservationOpen = false;
            return new Outcome(true, description, candidate);
        } catch (Throwable failure) {
            if (candidateReservationOpen) {
                try {
                    parent.release(candidate);
                } catch (Throwable cleanupFailure) {
                    if (cleanupFailure != failure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            rethrow(failure);
            throw new AssertionError("unreachable");
        }
    }

    private static String analyzerDescription(Mind mind) throws Exception {
        if (mind.getCurrentLogRecord(LogMode.ANALYZER) == null) {
            return "";
        }
        String record = mind.getCurrentLogRecord(LogMode.ANALYZER).getRecord();
        return record == null ? "" : record;
    }

    static final class Outcome {
        private final boolean accepted;
        private final String description;
        private final Mind mind;

        private Outcome(boolean accepted, String description, Mind mind) {
            this.accepted = accepted;
            this.description = description == null ? "" : description;
            this.mind = mind;
        }

        boolean isAccepted() {
            return accepted;
        }

        String getDescription() {
            return description;
        }

        Mind getMind() {
            return mind;
        }
    }

    private static final class ControlDelta {
        private final List<String> ruleDeletions;
        private final Set<String> udfDeletions;
        private final Map<String, String> inheritedRuleComments;
        private final boolean preserveHeaderClear;
        private final boolean preserveFooterClear;

        private ControlDelta(List<String> ruleDeletions,
                             Set<String> udfDeletions,
                             Map<String, String> inheritedRuleComments,
                             boolean preserveHeaderClear,
                             boolean preserveFooterClear) {
            this.ruleDeletions = ruleDeletions;
            this.udfDeletions = udfDeletions;
            this.inheritedRuleComments = inheritedRuleComments;
            this.preserveHeaderClear = preserveHeaderClear;
            this.preserveFooterClear = preserveFooterClear;
        }

        static ControlDelta capture(Mind parent, Mind current) throws Exception {
            Map<String, Rule> parentRules = activePrimaryRules(parent);
            Map<String, Rule> currentRules = activePrimaryRules(current);
            List<String> ruleDeletes = new ArrayList<String>();
            for (String origin : parentRules.keySet()) {
                if (!currentRules.containsKey(origin)) {
                    ruleDeletes.add(origin);
                }
            }

            Map<String, Operation> parentUdf = activeUdf(parent);
            Map<String, Operation> currentUdf = activeUdf(current);
            Set<String> udfDeletes = new LinkedHashSet<String>();
            for (String signature : parentUdf.keySet()) {
                if (!currentUdf.containsKey(signature)) {
                    udfDeletes.add(signature);
                }
            }

            Map<String, String> comments = new LinkedHashMap<String, String>();
            for (Map.Entry<String, Rule> entry : parentRules.entrySet()) {
                Rule currentRule = currentRules.get(entry.getKey());
                if (currentRule == null) {
                    continue;
                }
                String before = commentText(parent, entry.getValue().getId());
                String after = commentText(current, currentRule.getId());
                if (!before.equals(after)) {
                    comments.put(entry.getKey(), after);
                }
            }

            String parentHeader = commentText(parent, CommentFactory.HEADER_ID);
            String currentHeader = commentText(current, CommentFactory.HEADER_ID);
            String parentFooter = commentText(parent, CommentFactory.FOOTER_ID);
            String currentFooter = commentText(current, CommentFactory.FOOTER_ID);

            return new ControlDelta(
                    ruleDeletes,
                    udfDeletes,
                    comments,
                    !parentHeader.equals(currentHeader) && currentHeader.isEmpty(),
                    !parentFooter.equals(currentFooter) && currentFooter.isEmpty());
        }

        void apply(Mind candidate) throws Exception {
            for (String origin : ruleDeletions) {
                if (!activePrimaryRules(candidate).containsKey(origin)) {
                    continue;
                }
                candidate.query(deleteCommand(origin));
                if (activePrimaryRules(candidate).containsKey(origin)) {
                    throw new IllegalStateException(
                            "Cannot preserve Rule deletion: " + origin);
                }
            }

            for (String signature : udfDeletions) {
                Operation operation = candidate.getLibrary().find(signature);
                if (operation != null && !operation.isDeleted(candidate)) {
                    operation.setDeleted(true, candidate);
                }
            }

            Map<String, Rule> candidateRules = activePrimaryRules(candidate);
            for (Map.Entry<String, String> entry : inheritedRuleComments.entrySet()) {
                Rule rule = candidateRules.get(entry.getKey());
                if (rule != null) {
                    candidate.getComments().add(rule.getId(), entry.getValue());
                }
            }

            if (preserveHeaderClear) {
                candidate.getComments().add(CommentFactory.HEADER_ID, "");
            }
            if (preserveFooterClear) {
                candidate.getComments().add(CommentFactory.FOOTER_ID, "");
            }
        }
    }

    private static Map<String, Rule> activePrimaryRules(Mind mind) throws Exception {
        Map<String, Rule> rules = new LinkedHashMap<String, Rule>();
        for (IRule candidate : mind.getRules()) {
            Rule rule = (Rule) candidate;
            if (!rule.isGenerated() && !rule.isDeleted(mind)) {
                rules.put(rule.getOrigin(), rule);
            }
        }
        return rules;
    }

    private static Map<String, Operation> activeUdf(Mind mind) throws Exception {
        Map<String, Operation> operations = new LinkedHashMap<String, Operation>();
        for (IOperation candidate : mind.getLibrary()) {
            Operation operation = (Operation) candidate;
            if (!operation.isDeleted(mind)) {
                operations.put(operation.toString(), operation);
            }
        }
        return operations;
    }

    private static String commentText(Mind mind, long id) throws Exception {
        Comment comment = mind.getComments().get(id);
        if (comment == null || comment.isDeleted(mind)
                || comment.getComment() == null) {
            return "";
        }
        return comment.getComment();
    }

    private static String deleteCommand(String origin) {
        if (origin == null || origin.length() < 2) {
            throw new IllegalArgumentException("Cannot derive delete command from " + origin);
        }
        return "-" + origin.substring(1);
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new RuntimeException(failure);
    }
}
