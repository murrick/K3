/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.factory.RuleFactory;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.Term;

import java.util.HashMap;
import java.util.Map;

/**
 * Canonical boundary between transient Linker C-variable projections and
 * durable generated statements.
 *
 * <p>A {@code *N} C-variable is a rule-scoped child used only while matching
 * and saturating the current Mind. It must never become the durable witness of
 * a generated stored Rule. When a complete produced Domain is materialized,
 * each C-variable identity is alpha-rebound to a fresh {@code %N} root owned by
 * that generated Rule. Repeated occurrences of the same source C-variable in
 * one Domain share the same new root, while different materialized Rules own
 * independent witnesses.</p>
 *
 * <p>Because runtime C-variable ids are deliberately fresh, exact Term-based
 * lookup cannot recognize a repeated abstract consequence. The companion
 * alpha lookup therefore compares stored statement shape with a bijection over
 * C-variable occurrences before a new generated Rule is allocated. This keeps
 * fixed-point convergence without making C-variable ids durable identity.</p>
 */
public final class GeneratedCVarMaterializer {

    private GeneratedCVarMaterializer() {
    }

    public static IRule findAlphaEquivalent(RuleFactory factory,
                                            Domain source,
                                            Mind mind) throws Exception {
        if (source.isQuery(mind) || !containsCVariable(source, mind)) {
            return null;
        }

        for (Object value : factory) {
            IRule rule = (IRule) value;
            if (!rule.isStored() || rule.isQuery()) {
                continue;
            }
            Domain candidate = ((Rule) rule).getDomain();
            if (alphaEquivalent(source, candidate, mind)) {
                return rule;
            }
        }
        return null;
    }

    public static ArgumentsList rebindForGeneratedRule(ArgumentsList source,
                                                        Domain sourceDomain,
                                                        Mind mind,
                                                        Rule owner) throws Exception {
        if (sourceDomain.isQuery(mind) || !containsCVariable(sourceDomain, mind)) {
            return source;
        }

        ArgumentsList result = new ArgumentsList();
        Map<Long, ITerm> replacements = new HashMap<>();
        boolean abstractive = false;

        for (IArgument argument : source) {
            ITerm value = argument.getValue(mind);
            if (value != null && value.isCVariable()) {
                abstractive = true;
                ITerm replacement = replacements.get(value.getId());
                if (replacement == null) {
                    Term original = (Term) value;
                    replacement = mind.getTerms().createCVar(
                            owner, original.getName(mind), null);
                    ((Term) replacement).setDomini(original.isDomini());
                    replacements.put(value.getId(), replacement);
                }
                result.add(new Argument(replacement));
            } else {
                result.add(new Argument(value));
            }
        }

        if (abstractive) {
            owner.setAbstractive(true);
        }
        return result;
    }

    private static boolean containsCVariable(Domain domain, Mind mind) throws Exception {
        for (IArgument argument : domain.getArguments()) {
            if (argument.isEmpty(mind)) {
                continue;
            }
            ITerm value = argument.getValue(mind);
            if (value != null && value.isCVariable()) {
                return true;
            }
        }
        return false;
    }

    private static boolean alphaEquivalent(Domain left,
                                           Domain right,
                                           Mind mind) throws Exception {
        if (left.isAntc() != right.isAntc()
                || left.getPredicateId() != right.getPredicateId()
                || left.getRange() != right.getRange()) {
            return false;
        }

        Map<Long, Long> forward = new HashMap<>();
        Map<Long, Long> reverse = new HashMap<>();

        for (int i = 0; i < left.getRange(); ++i) {
            IArgument leftArgument = left.get(i);
            IArgument rightArgument = right.get(i);
            if (leftArgument.isEmpty(mind) || rightArgument.isEmpty(mind)) {
                return false;
            }

            ITerm leftValue = leftArgument.getValue(mind);
            ITerm rightValue = rightArgument.getValue(mind);
            boolean leftCVar = leftValue.isCVariable();
            boolean rightCVar = rightValue.isCVariable();
            if (leftCVar != rightCVar) {
                return false;
            }

            if (leftCVar) {
                Term leftTerm = (Term) leftValue;
                Term rightTerm = (Term) rightValue;
                if (leftTerm.isDomini() != rightTerm.isDomini()) {
                    return false;
                }

                Long mapped = forward.get(leftValue.getId());
                Long reverseMapped = reverse.get(rightValue.getId());
                if (mapped == null && reverseMapped == null) {
                    forward.put(leftValue.getId(), rightValue.getId());
                    reverse.put(rightValue.getId(), leftValue.getId());
                } else if (mapped == null
                        || reverseMapped == null
                        || mapped.longValue() != rightValue.getId()
                        || reverseMapped.longValue() != leftValue.getId()) {
                    return false;
                }
            } else if (!((Term) leftValue).equalsTo((Term) rightValue)) {
                return false;
            }
        }
        return true;
    }
}
