/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.primitives.Solve;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

/** Detached native ground identity, independent of rendering and operational IDs.
 * This inspection primitive does not authorize dispatch or a branch restriction.
 * Source UUID/revision/rule bindings remain separate provenance occurrences.
 */
final class DmzGroundIdentity {
    private final TerminalSupportCapture.Ground ground;

    private DmzGroundIdentity(TerminalSupportCapture.Ground ground) {
        this.ground = new TerminalSupportCapture.Ground(ground.predicate, ground.sign,
                new java.util.ArrayList<SemanticTermSnapshot>(ground.arguments));
    }

    static DmzGroundIdentity capture(IRule rule, Mind mind) throws Exception {
        if (!(rule instanceof Rule) || mind == null || rule.isDeleted(mind) || rule.isQuery()
                || ((Rule) rule).getTree().size() != 1 || ((Rule) rule).getTree().get(0).size() != 1)
            throw new IllegalArgumentException("One live native ground assertion required");
        Domain literal = ((Rule) rule).getTree().get(0).get(0);
        if (literal.isSystem(mind) || literal.isCalculated(mind) || literal.isQuery(mind))
            throw new IllegalArgumentException("Ordinary native ground literal required");
        for (IArgument argument : literal.getArguments())
            if (argument.getType() != ArgumentType.TERM
                    || !SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind)))
                throw new IllegalArgumentException("Ordinary materialized ground arguments required");
        return fromGround(TerminalSupportCapture.Ground.capture(new Solve(literal.getPredicate(),
                literal.isAntc(), literal.getArguments().convertBase(mind)), mind));
    }

    static DmzGroundIdentity fromGround(TerminalSupportCapture.Ground ground) {
        if (ground == null) throw new IllegalArgumentException("Detached native ground required");
        return new DmzGroundIdentity(ground);
    }

    boolean equivalent(DmzGroundIdentity other) {
        return other != null && ground.equivalent(other.ground);
    }

    boolean opposes(DmzGroundIdentity other) {
        return other != null && ground.sign != other.ground.sign
                && ground.equivalent(new TerminalSupportCapture.Ground(
                        other.ground.predicate, ground.sign, other.ground.arguments));
    }
}
