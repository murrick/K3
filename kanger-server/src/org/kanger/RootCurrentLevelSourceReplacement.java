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
import org.kanger.units.Operation;
import org.kanger.units.Rule;

import java.util.ArrayList;
import java.util.List;

/** Atomically replaces the source-representable declarative projection of U0. */
final class RootCurrentLevelSourceReplacement {

    private RootCurrentLevelSourceReplacement() {
    }

    static Outcome replace(IUser user, String exactSource) throws Exception {
        if (user == null || user.getCurrentMind() == null) {
            throw new IllegalArgumentException("Current user Mind is required");
        }
        Mind root = (Mind) user.getCurrentMind();
        if (root.getTransactionLevel() != 0 || root.getNext() != null) {
            throw new IllegalArgumentException(
                    "Root source replacement requires explicit transaction level U0");
        }
        String boundaryRejection = DeclarativeSourceBoundary.rejection(exactSource);
        if (boundaryRejection != null) {
            return new Outcome(false, boundaryRejection, root);
        }

        try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(root)) {
            Mind work = tx.mind();
            clearSourceProjection(work);

            Boolean compiled = exactSource != null && exactSource.isEmpty()
                    ? Boolean.TRUE
                    : work.compile(exactSource == null ? "" : exactSource);
            String description = analyzerDescription(work);
            if (!Boolean.TRUE.equals(compiled)) {
                tx.rollback();
                return new Outcome(false, description, root);
            }

            boolean committed = tx.commit();
            if (!committed) {
                return new Outcome(false, description, root);
            }
            user.setCurrentMind(root);
            return new Outcome(true, description, root);
        }
    }

    private static void clearSourceProjection(Mind work) throws Exception {
        List<Rule> rules = new ArrayList<Rule>();
        for (IRule candidate : work.getRules()) {
            Rule rule = (Rule) candidate;
            if (!rule.isGenerated() && !rule.isDeleted(work)) {
                rules.add(rule);
            }
        }

        work.getComments().add(CommentFactory.HEADER_ID, "");
        work.getComments().add(CommentFactory.FOOTER_ID, "");
        for (Rule rule : rules) {
            work.getComments().add(rule.getId(), "");
        }
        for (Rule rule : rules) {
            String origin = rule.getOrigin();
            if (origin == null || origin.length() < 2) {
                throw new IllegalStateException(
                        "Cannot derive source removal for Rule " + rule.getId());
            }
            work.query("-" + origin.substring(1));
            if (!rule.isDeleted(work)) {
                throw new IllegalStateException(
                        "Cannot hide current root Rule " + rule.getId());
            }
        }

        List<Operation> operations = new ArrayList<Operation>();
        for (IOperation candidate : work.getLibrary()) {
            Operation operation = (Operation) candidate;
            if (!operation.isDeleted(work)) {
                operations.add(operation);
            }
        }
        for (Operation operation : operations) {
            operation.setDeleted(true, work);
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
}
