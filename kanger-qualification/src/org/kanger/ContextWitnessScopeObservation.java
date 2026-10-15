/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.*;
import org.kanger.units.*;

/** Isolated primary-source metadata only. Never a witness transport certificate. */
final class ContextWitnessScopeObservation {
    static final class Witness {
        final long nativeTerm, ownerRule;
        final int index;
        final boolean domini;
        final Long parent;
        Witness(Term term,Mind mind) {
            nativeTerm = term.getId(); ownerRule = term.getRuleId(); index = term.getIndex(); domini = term.isDomini();
            ITerm p = term.getParent(mind); parent = p == null ? null : p.getId();
        }
    }
    static final class Use {
        final int branch, literal, argument, witness;
        final String predicate;
        final boolean positive;
        Use(int branch,int literal,int argument,int witness,Domain domain,Mind mind) throws Exception {
            this.branch = branch; this.literal = literal; this.argument = argument; this.witness = witness;
            predicate = domain.getPredicate(mind).getName(mind); positive = domain.isAntc();
        }
    }
    static final class Scope {
        /** Native IDs are meaningful only together with this fresh observation token. */
        final UUID replay = UUID.randomUUID();
        final Source source;
        final long nativeRule;
        final List<Witness> witnesses;
        final List<Use> uses;
        final Set<Long> universals;
        Scope(Source source,long nativeRule,List<Witness> witnesses,List<Use> uses,Set<Long> universals) {
            this.source = source; this.nativeRule = nativeRule;
            this.witnesses = Collections.unmodifiableList(new ArrayList<>(witnesses));
            this.uses = Collections.unmodifiableList(new ArrayList<>(uses));
            this.universals = Collections.unmodifiableSet(new LinkedHashSet<>(universals));
        }
    }
    static Scope inspect(Declaration declaration,int maximumWork) throws Exception {
        if (maximumWork < 1) throw new IllegalArgumentException("Bounded observation required");
        int[] remaining = {maximumWork};
        Runnable work = () -> { if (--remaining[0] < 0) throw new IllegalStateException("Witness scope observation work limit"); };
        User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind);
        try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,maximumWork)) {
            work.run(); capture.replay(mind,declaration.source,declaration.statement);
            Snapshot snapshot = capture.snapshot();
            if (snapshot.inputs.size() != 1 || snapshot.inputs.get(0).source == null)
                throw new IllegalStateException("One isolated primary input required");
            Rule rule = (Rule)mind.getRules().get(snapshot.inputs.get(0).nativeRule);
            List<Witness> witnesses = new ArrayList<>(); List<Use> uses = new ArrayList<>();
            Set<Long> universals = new LinkedHashSet<>(); Map<Long,Integer> ids = new HashMap<>();
            for (int b=0;b<rule.getTree().size();++b) {
                work.run(); List<Domain> branch = rule.getTree().get(b);
                for (int l=0;l<branch.size();++l) {
                    work.run(); Domain domain = branch.get(l); int a = 0;
                    for (IArgument argument : domain.getArguments()) {
                        work.run();
                        if (argument.getType() == ArgumentType.TVARIABLE) universals.add(argument.getId());
                        else {
                            ITerm value = argument.getValue(mind);
                            if (value != null && value.isCVariable()) {
                                if (!(value instanceof Term)) throw new IllegalStateException("Native Term witness required");
                                Integer index = ids.get(value.getId());
                                if (index == null) { index = witnesses.size(); ids.put(value.getId(),index); witnesses.add(new Witness((Term)value,mind)); }
                                uses.add(new Use(b,l,a,index,domain,mind));
                            } else if (!SemanticTermSnapshot.isOrdinaryValue(value))
                                throw new IllegalStateException("Unsupported witness scope argument");
                        }
                        ++a;
                    }
                }
            }
            return new Scope(declaration.source,rule.getId(),witnesses,uses,universals);
        }
    }
}
