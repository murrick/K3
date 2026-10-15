/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.QueryPass;
import org.kanger.enums.ArgumentType;
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
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

/**
 * Canonical boundary between transient Linker C-variable projections and
 * durable generated statements.
 *
 * <p>A {@code *N} C-variable is a rule-scoped child used only while matching
 * and saturating the current Mind. It must never become the durable witness of
 * a generated stored Rule. At the ACCEPT materialization boundary, a transient
 * child that survives into a complete produced Domain is alpha-rebound to a
 * fresh {@code %N} root owned by that generated Rule. Durable root C-variables
 * already present in the Domain are preserved. Repeated occurrences of the
 * same transient child in one Domain share the same new root, while different
 * materialized Rules own independent witnesses unless they project a primary
 * existential witness from a closed conjunction. Such a witness retains its
 * durable primary root across conclusions; alpha lookup cannot identify it
 * with another primary witness.</p>
 *
 * <p>CHECK/HYPOTHESIS passes deliberately use rule-scoped children as runtime
 * projections while proving or disproving a query. Rebinding those children
 * would change query semantics, so this helper must not durableize them.</p>
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
        if (mind.getQueryPass() != QueryPass.ACCEPT
                || sourceDomain.isQuery(mind)
                || !containsCVariable(sourceDomain, mind)) {
            return source;
        }

        ArgumentsList result = new ArgumentsList();
        Map<Long, ITerm> replacements = new HashMap<>();
        boolean abstractive = false;

        for (IArgument argument : source) {
            ITerm value = argument.getValue(mind);
            if (value != null && value.isCVariable()) {
                abstractive = true;
                ITerm replacement = value;
                Term original = (Term) value;
                Term anchor = primaryOrigin(original,mind);
                if (anchor != null) {
                    replacement = anchor;
                } else if (original.getParent(mind) != null) {
                    replacement = replacements.get(value.getId());
                    if (replacement == null) {
                        replacement = mind.getTerms().createCVar(
                                owner, original.getName(mind), null);
                        ((Term) replacement).setDomini(original.isDomini());
                        replacements.put(value.getId(), replacement);
                    }
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

    /** A closed primary conjunction has stable witnesses, with no universal dependency. */
    private static Term primaryOrigin(Term term,Mind mind) throws Exception {
        Set<Long> seen = new HashSet<>();
        for (int depth=0;term.getParent(mind) != null;++depth) {
            if (depth == 8 || !seen.add(term.getId())) return null;
            term = (Term)term.getParent(mind);
        }
        if (term.isDomini()) return null;
        IRule source = mind.getRules().get(term.getRuleId());
        if (source == null || source.isGenerated() || source.isQuery() || source.isDeleted(mind)) return null;
        if (((Rule)source).getTree().isEmpty()) return null;
        boolean present = false;
        for (List<Domain> branch : ((Rule)source).getTree()) {
            if (branch.size() != 1) return null;
            Domain domain = branch.get(0);
            if (!domain.isComplete() || domain.isSystem(mind) || domain.isCalculated(mind)
                    || "rule(1)".equals(domain.getPredicate(mind).toString(mind))) return null;
            for (IArgument argument : domain.getArguments()) {
                if (argument.getType() != ArgumentType.TERM) return null;
                ITerm value = argument.getValue(mind);
                if (value == null) return null;
                if (value.getId() == term.getId()) present = true;
                if (value.isCVariable() && (((Term)value).isDomini() || ((Term)value).getParent(mind) != null
                        || ((Term)value).getRuleId() != source.getId())) return null;
            }
        }
        return present ? term : null;
    }

    /** Only closed primary witnesses: no fresh existential dependency is introduced. */
    public static boolean hasOnlyPrimaryWitnesses(Domain domain,Mind mind) throws Exception {
        if (domain.getRange() == 0) return false;
        for (IArgument argument : domain.getArguments()) {
            ITerm value = argument.getValue(mind);
            if (!(value instanceof Term) || !value.isCVariable() || primaryOrigin((Term)value,mind) == null) return false;
        }
        return true;
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
                Term leftAnchor = primaryOrigin(leftTerm,mind), rightAnchor = primaryOrigin(rightTerm,mind);
                if (leftAnchor != null || rightAnchor != null) {
                    if (leftAnchor == null || rightAnchor == null || leftAnchor.getId() != rightAnchor.getId()) return false;
                    continue;
                }
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
