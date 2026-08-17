/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.UnitType;
import org.kanger.factory.CommentFactory;
import org.kanger.interfaces.IOperation;
import org.kanger.interfaces.IRule;
import org.kanger.units.Comment;
import org.kanger.units.Operation;
import org.kanger.units.Rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Storage-independent own-state of one explicit user transaction level.
 *
 * <p>The live Core remains authoritative whenever a semantic state can be
 * represented by ordinary Rule/UDF/comment overlays.  A small residue is kept
 * only for relative state that cannot be assigned a valid storage-local id on
 * the current baseline (for example a deletion of a Rule that the replacement
 * U0 does not contain).  Subsequent rebase capture combines that residue with
 * the level's current native own-state.</p>
 */
final class PortableMindLayer {

    private final Map<String, String> ruleSources = new LinkedHashMap<>();
    private final Set<String> ruleDeleted = new LinkedHashSet<>();
    private final Set<String> ruleRestored = new LinkedHashSet<>();
    private final Map<String, String> udfSources = new LinkedHashMap<>();
    private final Set<String> udfDeleted = new LinkedHashSet<>();
    private final Set<String> udfRestored = new LinkedHashSet<>();
    private final Map<String, String> ruleComments = new LinkedHashMap<>();
    private boolean headerChanged;
    private String header = "";
    private boolean footerChanged;
    private String footer = "";

    static PortableMindLayer empty() {
        return new PortableMindLayer();
    }

    PortableMindLayer copy() {
        PortableMindLayer copy = new PortableMindLayer();
        copy.ruleSources.putAll(ruleSources);
        copy.ruleDeleted.addAll(ruleDeleted);
        copy.ruleRestored.addAll(ruleRestored);
        copy.udfSources.putAll(udfSources);
        copy.udfDeleted.addAll(udfDeleted);
        copy.udfRestored.addAll(udfRestored);
        copy.ruleComments.putAll(ruleComments);
        copy.headerChanged = headerChanged;
        copy.header = header;
        copy.footerChanged = footerChanged;
        copy.footer = footer;
        return copy;
    }

    boolean isEmpty() {
        return ruleSources.isEmpty()
                && ruleDeleted.isEmpty()
                && ruleRestored.isEmpty()
                && udfSources.isEmpty()
                && udfDeleted.isEmpty()
                && udfRestored.isEmpty()
                && ruleComments.isEmpty()
                && !headerChanged
                && !footerChanged;
    }

    /**
     * Folds one committed child level into the surviving parent level.
     *
     * <p>The residue is intentionally allowed to duplicate state that was also
     * materialized natively by the ordinary factory commit.  The next capture
     * normalizes such duplication against the parent's native own-state.  This
     * keeps commit composition storage-independent and, critically, lets a
     * child marker that could not be assigned an id on the current baseline
     * survive exactly one level upward.  Committing into U0 is different: any
     * unresolved relative state has reached its semantic target and therefore
     * collapses harmlessly instead of leaking into a future storage baseline.</p>
     */
    static void mergeCommittedChild(Mind parent, PortableMindLayer childState) {
        if (parent.getNext() == null) {
            parent.setPortableRebaseResidue(PortableMindLayer.empty());
            return;
        }

        PortableMindLayer merged = parent.getPortableRebaseResidue().copy();

        for (Map.Entry<String, String> entry : childState.ruleSources.entrySet()) {
            merged.ruleSources.put(entry.getKey(), entry.getValue());
            // A newer declaration/restoration on a child supersedes a latent
            // visibility decision inherited from the surviving parent level.
            merged.ruleDeleted.remove(entry.getKey());
            merged.ruleRestored.remove(entry.getKey());
        }
        overlayMarkers(merged.ruleDeleted, merged.ruleRestored,
                childState.ruleDeleted, childState.ruleRestored);

        for (Map.Entry<String, String> entry : childState.udfSources.entrySet()) {
            merged.udfSources.put(entry.getKey(), entry.getValue());
            merged.udfDeleted.remove(entry.getKey());
            merged.udfRestored.remove(entry.getKey());
        }
        overlayMarkers(merged.udfDeleted, merged.udfRestored,
                childState.udfDeleted, childState.udfRestored);

        merged.ruleComments.putAll(childState.ruleComments);
        if (childState.headerChanged) {
            merged.headerChanged = true;
            merged.header = childState.header;
        }
        if (childState.footerChanged) {
            merged.footerChanged = true;
            merged.footer = childState.footer;
        }
        parent.setPortableRebaseResidue(merged);
    }

    private static void overlayMarkers(Set<String> targetDeleted,
                                       Set<String> targetRestored,
                                       Set<String> childDeleted,
                                       Set<String> childRestored) {
        Set<String> touched = new LinkedHashSet<>();
        touched.addAll(childDeleted);
        touched.addAll(childRestored);
        for (String key : touched) {
            targetDeleted.remove(key);
            targetRestored.remove(key);
            if (childDeleted.contains(key)) {
                targetDeleted.add(key);
            }
            if (childRestored.contains(key)) {
                targetRestored.add(key);
            }
        }
    }

    static PortableMindLayer captureRoot(Mind root) throws Exception {
        PortableMindLayer state = new PortableMindLayer();
        for (IRule candidate : root.getRules()) {
            Rule rule = (Rule) candidate;
            if (!rule.isGenerated() && !rule.isDeleted(root)) {
                state.ruleSources.put(rule.getOrigin(), rule.getOrigin());
                Comment comment = root.getComments().get(rule.getId());
                if (comment != null && !comment.isDeleted(root)
                        && comment.getComment() != null
                        && !comment.getComment().isEmpty()) {
                    state.ruleComments.put(rule.getOrigin(), comment.getComment());
                }
            }
        }
        for (IOperation candidate : root.getLibrary()) {
            Operation operation = (Operation) candidate;
            if (!operation.isDeleted(root)) {
                state.udfSources.put(operation.toString(), operation.asString());
            }
        }
        Comment headerComment = root.getComments().get(CommentFactory.HEADER_ID);
        if (headerComment != null && !headerComment.isDeleted(root)
                && headerComment.getComment() != null
                && !headerComment.getComment().isEmpty()) {
            state.headerChanged = true;
            state.header = headerComment.getComment();
        }
        Comment footerComment = root.getComments().get(CommentFactory.FOOTER_ID);
        if (footerComment != null && !footerComment.isDeleted(root)
                && footerComment.getComment() != null
                && !footerComment.getComment().isEmpty()) {
            state.footerChanged = true;
            state.footer = footerComment.getComment();
        }
        return state;
    }

    static PortableMindLayer capture(Mind child) throws Exception {
        PortableMindLayer state = child.getPortableRebaseResidue().copy();
        long mindId = child.getId();

        Set<String> nativeOwnedRules = new HashSet<>();
        for (IRule candidate : child.getRules()) {
            Rule rule = (Rule) candidate;
            if (rule.getMindId() == mindId && !rule.isGenerated()) {
                String origin = rule.getOrigin();
                nativeOwnedRules.add(origin);
                state.ruleSources.put(origin, origin);
                state.ruleDeleted.remove(origin);
                state.ruleRestored.remove(origin);
            }
        }

        applyNativeRuleMarkers(child, state, nativeOwnedRules);

        Set<String> nativeUdfSources = new HashSet<>();
        for (IOperation candidate : child.getLibrary()) {
            Operation operation = (Operation) candidate;
            if (operation.getMindId() == mindId) {
                String signature = operation.toString();
                nativeUdfSources.add(signature);
                state.udfSources.put(signature, operation.asString());
                state.udfDeleted.remove(signature);
                state.udfRestored.remove(signature);
            }
        }
        applyNativeUdfMarkers(child, state, nativeUdfSources);

        Iterator comments = child.getComments().iterator();
        while (comments.hasNext()) {
            Comment comment = (Comment) comments.next();
            if (comment.getMindId() != mindId) {
                continue;
            }
            String text = comment.getComment() == null ? "" : comment.getComment();
            if (comment.getId() == CommentFactory.HEADER_ID) {
                state.headerChanged = true;
                state.header = text;
            } else if (comment.getId() == CommentFactory.FOOTER_ID) {
                state.footerChanged = true;
                state.footer = text;
            } else {
                Rule rule = child.getRules().get(comment.getId());
                if (rule != null) {
                    state.ruleComments.put(rule.getOrigin(), text);
                }
            }
        }
        return state;
    }

    private static void applyNativeRuleMarkers(Mind child,
                                               PortableMindLayer state,
                                               Set<String> nativeOwnedRules)
            throws Exception {
        Set<Long> deleted = localIds(child, UnitType.RULE, true);
        Set<Long> restored = localIds(child, UnitType.RULE, false);
        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(deleted);
        ids.addAll(restored);
        for (Long id : ids) {
            Rule rule = child.getRules().get(id);
            if (rule == null) {
                continue;
            }
            String origin = rule.getOrigin();
            if (nativeOwnedRules.contains(origin)
                    || deleted.contains(id)
                    || restored.contains(id)) {
                state.ruleDeleted.remove(origin);
                state.ruleRestored.remove(origin);
                if (deleted.contains(id)) {
                    state.ruleDeleted.add(origin);
                }
                if (restored.contains(id)) {
                    state.ruleRestored.add(origin);
                }
            }
        }
    }

    private static void applyNativeUdfMarkers(Mind child,
                                              PortableMindLayer state,
                                              Set<String> nativeUdfSources)
            throws Exception {
        Set<Long> deleted = localIds(child, UnitType.SYSOP, true);
        Set<Long> restored = localIds(child, UnitType.SYSOP, false);
        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(deleted);
        ids.addAll(restored);
        for (Long id : ids) {
            Operation operation = child.getLibrary().get(id);
            if (operation == null) {
                continue;
            }
            String signature = operation.toString();
            if (nativeUdfSources.contains(signature)
                    || deleted.contains(id)
                    || restored.contains(id)) {
                state.udfDeleted.remove(signature);
                state.udfRestored.remove(signature);
                if (deleted.contains(id)) {
                    state.udfDeleted.add(signature);
                }
                if (restored.contains(id)) {
                    state.udfRestored.add(signature);
                }
            }
        }
    }

    private static Set<Long> localIds(Mind mind, UnitType type, boolean deleted) {
        Map<UnitType, Set<Long>> source = deleted ? mind.getDeleted() : mind.getRestored();
        Set<Long> ids = source.get(type);
        return ids == null ? new LinkedHashSet<Long>() : new LinkedHashSet<Long>(ids);
    }

    void apply(Mind parent, Mind child) throws Exception {
        PortableMindLayer residue = new PortableMindLayer();

        for (Map.Entry<String, String> entry : udfSources.entrySet()) {
            String signature = entry.getKey();
            String source = entry.getValue();
            Boolean result = child.query(source);
            Operation operation = child.getLibrary().find(signature);
            if (operation == null) {
                throw new IllegalStateException("Cannot replay UDF: " + source);
            }
            if (!Boolean.TRUE.equals(result) && operation.isDeleted(child)) {
                throw new IllegalStateException("Cannot replay UDF: " + source);
            }
            if (operation.getMindId() != child.getId()) {
                residue.udfSources.put(signature, source);
            }
        }

        for (Map.Entry<String, String> entry : ruleSources.entrySet()) {
            String origin = entry.getKey();
            Boolean result = child.query(entry.getValue());
            Rule rule = findRule(child, origin);
            if (rule == null) {
                throw new IllegalStateException("Cannot replay Rule declaration: " + origin);
            }
            if (!Boolean.TRUE.equals(result) && rule.isDeleted(child)) {
                throw new IllegalStateException("Cannot replay Rule declaration: " + origin);
            }
            if (rule.getMindId() != child.getId()) {
                residue.ruleSources.put(origin, entry.getValue());
            }
        }

        Set<String> ruleKeys = new LinkedHashSet<>();
        ruleKeys.addAll(ruleDeleted);
        ruleKeys.addAll(ruleRestored);
        for (String origin : ruleKeys) {
            boolean deletes = ruleDeleted.contains(origin);
            boolean restores = ruleRestored.contains(origin);
            Rule rule = findRule(child, origin);
            if (rule == null) {
                if (deletes) {
                    residue.ruleDeleted.add(origin);
                }
                if (restores) {
                    residue.ruleRestored.add(origin);
                }
                continue;
            }
            applyRuleVisibility(child, rule, deletes, restores);
            Set<Long> localDeleted = localIds(child, UnitType.RULE, true);
            Set<Long> localRestored = localIds(child, UnitType.RULE, false);
            if (deletes && !localDeleted.contains(rule.getId())) {
                residue.ruleDeleted.add(origin);
            }
            if (restores && !localRestored.contains(rule.getId())) {
                residue.ruleRestored.add(origin);
            }
        }

        Set<String> udfKeys = new LinkedHashSet<>();
        udfKeys.addAll(udfDeleted);
        udfKeys.addAll(udfRestored);
        for (String signature : udfKeys) {
            boolean deletes = udfDeleted.contains(signature);
            boolean restores = udfRestored.contains(signature);
            Operation operation = child.getLibrary().find(signature);
            if (operation == null) {
                if (deletes) {
                    residue.udfDeleted.add(signature);
                }
                if (restores) {
                    residue.udfRestored.add(signature);
                }
                continue;
            }
            applyUdfVisibility(child, operation, deletes, restores);
            Set<Long> localDeleted = localIds(child, UnitType.SYSOP, true);
            Set<Long> localRestored = localIds(child, UnitType.SYSOP, false);
            if (deletes && !localDeleted.contains(operation.getId())) {
                residue.udfDeleted.add(signature);
            }
            if (restores && !localRestored.contains(operation.getId())) {
                residue.udfRestored.add(signature);
            }
        }

        for (Map.Entry<String, String> entry : ruleComments.entrySet()) {
            Rule rule = findRule(child, entry.getKey());
            if (rule == null) {
                residue.ruleComments.put(entry.getKey(), entry.getValue());
            } else {
                child.getComments().add(rule.getId(), entry.getValue());
            }
        }
        if (headerChanged) {
            child.getComments().add(CommentFactory.HEADER_ID, header);
        }
        if (footerChanged) {
            child.getComments().add(CommentFactory.FOOTER_ID, footer);
        }

        child.setPortableRebaseResidue(residue);
    }

    private static void applyRuleVisibility(Mind child,
                                            Rule rule,
                                            boolean deletes,
                                            boolean restores) throws Exception {
        if (deletes && restores) {
            rule.setDeleted(true, child);
            rule.setDeleted(false, child);
            child.getDeleted().computeIfAbsent(UnitType.RULE, key -> new HashSet<Long>())
                    .add(rule.getId());
            child.getRestored().computeIfAbsent(UnitType.RULE, key -> new HashSet<Long>())
                    .add(rule.getId());
        } else if (deletes) {
            rule.setDeleted(true, child);
        } else if (restores) {
            rule.setDeleted(false, child);
        }
    }

    private static void applyUdfVisibility(Mind child,
                                           Operation operation,
                                           boolean deletes,
                                           boolean restores) {
        if (deletes && restores) {
            operation.setDeleted(true, child);
            operation.setDeleted(false, child);
            child.getDeleted().computeIfAbsent(UnitType.SYSOP, key -> new HashSet<Long>())
                    .add(operation.getId());
            child.getRestored().computeIfAbsent(UnitType.SYSOP, key -> new HashSet<Long>())
                    .add(operation.getId());
        } else if (deletes) {
            operation.setDeleted(true, child);
        } else if (restores) {
            operation.setDeleted(false, child);
        }
    }

    private static Rule findRule(Mind mind, String origin) throws Exception {
        Rule found = null;
        for (IRule candidate : mind.getRules()) {
            Rule rule = (Rule) candidate;
            if (!origin.equals(rule.getOrigin())) {
                continue;
            }
            if (found != null && found.getId() != rule.getId()) {
                throw new IllegalStateException("Ambiguous Rule origin: " + origin);
            }
            found = rule;
        }
        return found;
    }
}
