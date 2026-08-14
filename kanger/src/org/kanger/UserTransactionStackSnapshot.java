/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.factory.CommentFactory;
import org.kanger.interfaces.IOperation;
import org.kanger.interfaces.IRule;
import org.kanger.units.Comment;
import org.kanger.units.Operation;
import org.kanger.units.Rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Storage-independent semantic snapshot of the explicit user transaction stack.
 *
 * <p>The snapshot deliberately excludes storage-local ids and derived runtime
 * structures. Each level records only authorial semantic delta relative to its
 * parent: primary Rule additions/deletions, UDF upserts/deletions and comment
 * overrides. Domains, generated Rules, TVariables/TValues/FValues, inference
 * caches and query-local bindings are reconstructed by normal Core execution
 * while the delta is replayed over a new base.</p>
 */
final class UserTransactionStackSnapshot {

    private final LevelDelta rootLevel;
    private final List<LevelDelta> levels;

    private UserTransactionStackSnapshot(LevelDelta rootLevel, List<LevelDelta> levels) {
        this.rootLevel = rootLevel;
        this.levels = Collections.unmodifiableList(levels);
    }

    static UserTransactionStackSnapshot capture(Mind top) throws Exception {
        if (top == null) {
            throw new IllegalArgumentException("Transaction stack requires a current Mind");
        }

        List<Mind> lineage = new ArrayList<>();
        for (Mind current = top; current != null; current = (Mind) current.getNext()) {
            lineage.add(current);
        }
        Collections.reverse(lineage);

        List<LevelDelta> deltas = new ArrayList<>();
        for (int i = 1; i < lineage.size(); ++i) {
            deltas.add(LevelDelta.capture(lineage.get(i - 1), lineage.get(i)));
        }
        return new UserTransactionStackSnapshot(null, deltas);
    }

    static UserTransactionStackSnapshot captureOffline(Mind top) throws Exception {
        if (top == null) {
            throw new IllegalArgumentException("Transaction stack requires a current Mind");
        }

        List<Mind> lineage = new ArrayList<>();
        for (Mind current = top; current != null; current = (Mind) current.getNext()) {
            lineage.add(current);
        }
        Collections.reverse(lineage);

        LevelDelta root = LevelDelta.captureRoot(lineage.get(0));
        List<LevelDelta> deltas = new ArrayList<>();
        for (int i = 1; i < lineage.size(); ++i) {
            deltas.add(LevelDelta.capture(lineage.get(i - 1), lineage.get(i)));
        }
        return new UserTransactionStackSnapshot(root, deltas);
    }

    int depth() {
        return levels.size();
    }

    Mind replay(Mind root) throws Exception {
        return replayLevels(root);
    }

    Mind replayOverBaseline(Mind root) throws Exception {
        Mind current = root;
        if (rootLevel != null && !rootLevel.isEmpty()) {
            Mind workspace = new Mind(current);
            boolean applied = false;
            try {
                rootLevel.apply(current, workspace);
                applied = true;
            } finally {
                if (!applied) {
                    current.release(workspace);
                }
            }
            current = workspace;
        }
        return replayLevels(current);
    }

    Mind restoreOffline(Mind root) throws Exception {
        if (rootLevel != null && !rootLevel.isEmpty()) {
            rootLevel.apply(root, root);
        }
        return replayLevels(root);
    }

    private Mind replayLevels(Mind root) throws Exception {
        Mind current = root;
        try {
            for (LevelDelta delta : levels) {
                Mind child = new Mind(current);
                boolean applied = false;
                try {
                    delta.apply(current, child);
                    applied = true;
                } finally {
                    if (!applied) {
                        current.release(child);
                    }
                }
                current = child;
            }
            return current;
        } catch (Throwable failure) {
            Throwable propagated = failure;
            try {
                rollbackToRoot(current);
            } catch (Throwable cleanupFailure) {
                if (cleanupFailure != failure) {
                    propagated.addSuppressed(cleanupFailure);
                }
            }
            rethrow(propagated);
            throw new AssertionError("unreachable");
        }
    }

    static Mind rollbackToRoot(Mind top) throws Exception {
        Mind current = top;
        while (current.getNext() != null) {
            Mind parent = (Mind) current.getNext();
            parent.release(current);
            current = parent;
        }
        return current;
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

    private static final class LevelDelta {
        private final List<String> ruleAdditions;
        private final List<String> ruleDeletions;
        private final Map<String, String> udfUpserts;
        private final Set<String> udfDeletions;
        private final Map<String, String> ruleCommentOverrides;
        private final String headerCommentOverride;
        private final boolean headerChanged;
        private final String footerCommentOverride;
        private final boolean footerChanged;

        private LevelDelta(List<String> ruleAdditions,
                           List<String> ruleDeletions,
                           Map<String, String> udfUpserts,
                           Set<String> udfDeletions,
                           Map<String, String> ruleCommentOverrides,
                           String headerCommentOverride,
                           boolean headerChanged,
                           String footerCommentOverride,
                           boolean footerChanged) {
            this.ruleAdditions = ruleAdditions;
            this.ruleDeletions = ruleDeletions;
            this.udfUpserts = udfUpserts;
            this.udfDeletions = udfDeletions;
            this.ruleCommentOverrides = ruleCommentOverrides;
            this.headerCommentOverride = headerCommentOverride;
            this.headerChanged = headerChanged;
            this.footerCommentOverride = footerCommentOverride;
            this.footerChanged = footerChanged;
        }

        static LevelDelta captureRoot(Mind root) throws Exception {
            Map<String, Rule> rules = activePrimaryRules(root);
            List<String> additions = new ArrayList<>(rules.keySet());

            Map<String, String> upserts = new LinkedHashMap<>();
            for (Map.Entry<String, Operation> entry : activeUdf(root).entrySet()) {
                upserts.put(entry.getKey(), entry.getValue().asString());
            }

            Map<String, String> comments = new LinkedHashMap<>();
            for (Map.Entry<String, Rule> entry : rules.entrySet()) {
                String comment = commentText(root, entry.getValue().getId());
                if (!comment.isEmpty()) {
                    comments.put(entry.getKey(), comment);
                }
            }

            String header = commentText(root, CommentFactory.HEADER_ID);
            String footer = commentText(root, CommentFactory.FOOTER_ID);
            return new LevelDelta(
                    additions,
                    new ArrayList<String>(),
                    upserts,
                    new LinkedHashSet<String>(),
                    comments,
                    header,
                    !header.isEmpty(),
                    footer,
                    !footer.isEmpty());
        }

        static LevelDelta capture(Mind parent, Mind child) throws Exception {
            Map<String, Rule> parentRules = activePrimaryRules(parent);
            Map<String, Rule> childRules = activePrimaryRules(child);

            List<String> additions = new ArrayList<>();
            for (String origin : childRules.keySet()) {
                if (!parentRules.containsKey(origin)) {
                    additions.add(origin);
                }
            }

            List<String> deletions = new ArrayList<>();
            for (String origin : parentRules.keySet()) {
                if (!childRules.containsKey(origin)) {
                    deletions.add(origin);
                }
            }

            Map<String, Operation> parentUdf = activeUdf(parent);
            Map<String, Operation> childUdf = activeUdf(child);
            Map<String, String> upserts = new LinkedHashMap<>();
            for (Map.Entry<String, Operation> entry : childUdf.entrySet()) {
                Operation before = parentUdf.get(entry.getKey());
                String source = entry.getValue().asString();
                if (before == null || !source.equals(before.asString())) {
                    upserts.put(entry.getKey(), source);
                }
            }

            Set<String> udfDeletes = new LinkedHashSet<>();
            for (String signature : parentUdf.keySet()) {
                if (!childUdf.containsKey(signature)) {
                    udfDeletes.add(signature);
                }
            }

            Map<String, String> comments = new LinkedHashMap<>();
            for (Map.Entry<String, Rule> entry : childRules.entrySet()) {
                Rule childRule = entry.getValue();
                Rule parentRule = parentRules.get(entry.getKey());
                String childComment = commentText(child, childRule.getId());
                String parentComment = parentRule == null
                        ? ""
                        : commentText(parent, parentRule.getId());
                if (!childComment.equals(parentComment)) {
                    comments.put(entry.getKey(), childComment);
                }
            }

            String parentHeader = commentText(parent, CommentFactory.HEADER_ID);
            String childHeader = commentText(child, CommentFactory.HEADER_ID);
            String parentFooter = commentText(parent, CommentFactory.FOOTER_ID);
            String childFooter = commentText(child, CommentFactory.FOOTER_ID);

            return new LevelDelta(
                    additions,
                    deletions,
                    upserts,
                    udfDeletes,
                    comments,
                    childHeader,
                    !childHeader.equals(parentHeader),
                    childFooter,
                    !childFooter.equals(parentFooter));
        }

        boolean isEmpty() {
            return ruleAdditions.isEmpty()
                    && ruleDeletions.isEmpty()
                    && udfUpserts.isEmpty()
                    && udfDeletions.isEmpty()
                    && ruleCommentOverrides.isEmpty()
                    && !headerChanged
                    && !footerChanged;
        }

        void apply(Mind parent, Mind child) throws Exception {
            for (String source : udfUpserts.values()) {
                Boolean result = child.query(source);
                if (!Boolean.TRUE.equals(result)) {
                    Operation operation = child.getLibrary().find(signatureFromSource(source));
                    if (operation == null || operation.isDeleted(child)) {
                        throw new IllegalStateException("Cannot replay UDF: " + source);
                    }
                }
            }

            for (String origin : ruleAdditions) {
                Boolean result = child.query(origin);
                if (!Boolean.TRUE.equals(result)
                        && !activePrimaryRules(child).containsKey(origin)) {
                    throw new IllegalStateException("Cannot replay Rule addition: " + origin);
                }
            }

            for (String origin : ruleDeletions) {
                if (!activePrimaryRules(child).containsKey(origin)) {
                    continue;
                }
                String delete = deleteCommand(origin);
                child.query(delete);
                if (activePrimaryRules(child).containsKey(origin)) {
                    throw new IllegalStateException("Cannot replay Rule deletion: " + origin);
                }
            }

            for (String signature : udfDeletions) {
                Operation operation = child.getLibrary().find(signature);
                if (operation != null && !operation.isDeleted(child)) {
                    operation.setDeleted(true, child);
                }
            }

            Map<String, Rule> rules = activePrimaryRules(child);
            for (Map.Entry<String, String> entry : ruleCommentOverrides.entrySet()) {
                Rule rule = rules.get(entry.getKey());
                if (rule == null) {
                    throw new IllegalStateException(
                            "Cannot replay comment because Rule is absent: " + entry.getKey());
                }
                child.getComments().add(rule.getId(), entry.getValue());
            }

            if (headerChanged) {
                child.getComments().add(CommentFactory.HEADER_ID, headerCommentOverride);
            }
            if (footerChanged) {
                child.getComments().add(CommentFactory.FOOTER_ID, footerCommentOverride);
            }
        }

        private static String deleteCommand(String origin) {
            if (origin == null || origin.length() < 2) {
                throw new IllegalArgumentException("Cannot derive delete command from " + origin);
            }
            return "-" + origin.substring(1);
        }

        private static String signatureFromSource(String source) {
            int open = source.indexOf('(');
            int close = source.indexOf(')', open + 1);
            if (open < 1 || close < open) {
                return source;
            }
            String name = source.substring(1, open).trim();
            String args = source.substring(open + 1, close).trim();
            int range = args.isEmpty() ? 0 : args.split(",", -1).length;
            return name + "(" + range + ")";
        }

        private static Map<String, Rule> activePrimaryRules(Mind mind) throws Exception {
            Map<String, Rule> rules = new LinkedHashMap<>();
            for (IRule candidate : mind.getRules()) {
                Rule rule = (Rule) candidate;
                if (rule.isGenerated() || rule.isDeleted(mind)) {
                    continue;
                }
                String origin = rule.getOrigin();
                Rule previous = rules.put(origin, rule);
                if (previous != null && previous.getId() != rule.getId()) {
                    throw new IllegalStateException("Ambiguous primary Rule origin: " + origin);
                }
            }
            return rules;
        }

        private static Map<String, Operation> activeUdf(Mind mind) throws Exception {
            Map<String, Operation> operations = new LinkedHashMap<>();
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
            if (comment == null || comment.isDeleted(mind)) {
                return "";
            }
            return comment.getComment() == null ? "" : comment.getComment();
        }
    }
}
