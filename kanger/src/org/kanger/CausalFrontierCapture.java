/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Opt-in observation of actual native root-query matches. This surface never
 * searches rule sets, creates substitutions/Causes or changes inference.
 * Initially supports one ordinary residual Domain in a two-literal branch;
 * compound residuals and calculated/function/C-variable arguments stay local.
 * Runtime references exist only inside this canonical call. Returned demands
 * are detached candidates for later operation-local evidence and native reproof.
 */
public final class CausalFrontierCapture {
    private final Mind owner;
    private final boolean enumerating;
    private final List<FrontierDemand> demands = new ArrayList<FrontierDemand>();
    private Mind frameMind;
    private Rule frameRule;
    private Domain root;
    private final Map<Domain, List<FrontierDemand>> observed =
            new IdentityHashMap<Domain, List<FrontierDemand>>();
    private Exception failure;

    private CausalFrontierCapture(Mind owner, boolean enumerating) {
        this.owner = owner;
        this.enumerating = enumerating;
    }

    /** Detached ordinary atomic query shape, captured before inference binds its variables. */
    public static FrontierDomain describeAtomic(Mind mind, Rule rule) throws Exception {
        if (rule.getTree().size() != 1 || rule.getTree().get(0).size() != 1) return null;
        Domain domain = rule.getDomain();
        if (domain.isSystem(mind) || domain.isCalculated(mind)
                || "rule(1)".equals(domain.getPredicate(mind).toString(mind))) return null;
        return FrontierDomain.capture(domain, mind);
    }

    public static Result query(Mind owner, String query,
                               Queue<ITerm> externals, boolean logging) throws Exception {
        return query(owner, query, externals, logging, false);
    }

    public static Result enumerate(Mind owner, String query,
            Queue<ITerm> externals, boolean logging) throws Exception {
        return query(owner, query, externals, logging, true);
    }

    private static Result query(Mind owner, String query, Queue<ITerm> externals,
            boolean logging, boolean enumerating) throws Exception {
        if (owner.getCausalFrontierCapture() != null) {
            throw new IllegalStateException("A causal capture is already active");
        }
        CausalFrontierCapture capture = new CausalFrontierCapture(owner, enumerating);
        owner.setCausalFrontierCapture(capture);
        try {
            Boolean truth = owner.queryCanonical(query, externals, logging);
            if (capture.failure != null) {
                throw capture.failure;
            }
            return new Result(truth, truth == null || enumerating ? capture.demands
                    : Collections.<FrontierDemand>emptyList());
        } finally {
            owner.setCausalFrontierCapture(null);
            capture.clearFrame();
        }
    }

    boolean enumerates(Rule query, Mind mind) throws Exception {
        FrontierDomain shape = enumerating ? describeAtomic(mind, query) : null;
        return shape != null && !shape.isGround();
    }

    void beginLink(Mind mind, Rule rule) {
        // Ignore recursive function queries and unrelated technical children.
        if (mind.getNext() != owner) {
            return;
        }
        clearFrame();
        try {
            if (rule != null && rule.isQuery() && rule.getTree().size() == 1
                    && rule.getTree().get(0).size() == 1) {
                frameMind = mind;
                frameRule = rule;
                root = rule.getTree().get(0).get(0);
            }
        } catch (Exception exception) {
            failure = exception;
        }
    }

    void observePair(Mind mind, Domain slave, Domain master, List<Domain> branch) {
        if (mind != frameMind || slave != root || branch.size() != 2) {
            return;
        }
        try {
            Domain child = branch.get(0) == master ? branch.get(1) : branch.get(0);
            if (child.getPredicate(mind).isSystem(mind) || child.isCalculated(mind)) {
                return;
            }
            FrontierDemand demand = new Binding(mind).detach(slave, master, child);
            if (demand != null) {
                List<FrontierDemand> list = observed.get(child);
                if (list == null) {
                    list = new ArrayList<FrontierDemand>();
                    observed.put(child, list);
                }
                list.add(demand);
            }
        } catch (Exception exception) {
            // Observation must not alter the native matching execution path.
            // The opt-in caller receives the failure after native query cleanup.
            failure = exception;
        }
    }

    void afterAnalyze(Mind mind, Rule rule, boolean proven) {
        if (mind != frameMind || rule != frameRule) {
            return;
        }
        try {
            if (!proven || enumerating) {
                for (Map.Entry<Domain, List<FrontierDemand>> entry : observed.entrySet()) {
                    if (!entry.getKey().isUsed(mind) && !entry.getKey().isCalculated(mind)) {
                        for (FrontierDemand demand : entry.getValue()) {
                            boolean duplicate = false;
                            for (FrontierDemand previous : demands) {
                                if (previous.semanticallyEquivalent(demand)) {
                                    duplicate = true;
                                    break;
                                }
                            }
                            if (!duplicate) {
                                demands.add(demand);
                            }
                        }
                    }
                }
            }
        } catch (Exception exception) {
            failure = exception;
        } finally {
            clearFrame();
        }
    }

    private void clearFrame() {
        frameMind = null;
        frameRule = null;
        root = null;
        observed.clear();
    }

    public static final class Result {
        private final Boolean truth;
        private final List<FrontierDemand> demands;

        private Result(Boolean truth, List<FrontierDemand> demands) {
            this.truth = truth;
            this.demands = Collections.unmodifiableList(new ArrayList<FrontierDemand>(demands));
        }

        public Boolean getTruth() {
            return truth;
        }

        public List<FrontierDemand> getDemands() {
            return demands;
        }
    }

    /** Temporary positional equality binder for a pair already selected by Linker. */
    private static final class Binding {
        private final Mind mind;
        private final Map<Long, Node> variables = new HashMap<Long, Node>();
        private boolean supported = true;

        Binding(Mind mind) {
            this.mind = mind;
        }

        FrontierDemand detach(Domain parent, Domain matched, Domain child) throws Exception {
            if (parent.getRange() != matched.getRange()) {
                return null;
            }
            List<Node> parents = slots(parent);
            List<Node> matches = slots(matched);
            List<Node> children = slots(child);
            if (!supported) {
                return null;
            }
            for (int i = 0; i < parents.size(); ++i) {
                if (!unite(parents.get(i), matches.get(i))) {
                    return null;
                }
            }
            FrontierDomain detachedParent = FrontierDomain.capture(parent, mind);
            if (detachedParent == null) {
                return null;
            }
            Map<Node, Integer> groups = new IdentityHashMap<Node, Integer>();
            List<FrontierProjection.Slot> parentSlots = detachSlots(parents, groups);
            List<FrontierProjection.Slot> childSlots = detachSlots(children, groups);
            List<FrontierDomain.ArgumentState> arguments = new ArrayList<FrontierDomain.ArgumentState>();
            for (FrontierProjection.Slot slot : childSlots) {
                arguments.add(slot.fixed != null
                        ? FrontierDomain.ArgumentState.fixed(slot.fixed)
                        : FrontierDomain.ArgumentState.variable("v" + slot.group, slot.group));
            }
            FrontierDomain query = FrontierDomain.projected(
                    child.getPredicate(mind).getName(mind), child.isAntc(), arguments);
            return new FrontierDemand(detachedParent, query,
                    new FrontierProjection(parentSlots, childSlots));
        }

        private List<Node> slots(Domain domain) throws Exception {
            List<Node> result = new ArrayList<Node>();
            for (IArgument argument : domain.getArguments()) {
                Node node;
                ITerm value;
                if (argument.getType() == ArgumentType.TVARIABLE) {
                    TVariable variable = (TVariable) argument.getObject(mind);
                    node = variables.get(variable.getId());
                    if (node == null) {
                        node = new Node();
                        variables.put(variable.getId(), node);
                    }
                    TValue current = variable.getCurrent();
                    value = current == null ? null : current.getValue(mind);
                } else {
                    node = new Node();
                    if (argument.getType() == ArgumentType.FUNCTION) {
                        supported = false;
                        result.add(node);
                        continue;
                    }
                    value = argument.getValue(mind);
                    if (value == null) {
                        supported = false;
                    }
                }
                if (value != null) {
                    if (value.isCVariable()) {
                        supported = false;
                    } else {
                        SemanticTermSnapshot fixed = SemanticTermSnapshot.capture(value);
                        if (node.fixed != null && !node.fixed.semanticallyEquals(fixed)) {
                            supported = false;
                        }
                        node.fixed = fixed;
                    }
                }
                result.add(node);
            }
            return result;
        }

        private static boolean unite(Node left, Node right) {
            Node a = left.root();
            Node b = right.root();
            if (a == b) {
                return true;
            }
            if (a.fixed != null && b.fixed != null && !a.fixed.semanticallyEquals(b.fixed)) {
                return false;
            }
            b.parent = a;
            if (a.fixed == null) {
                a.fixed = b.fixed;
            }
            return true;
        }

        private static List<FrontierProjection.Slot> detachSlots(List<Node> nodes,
                                                                Map<Node, Integer> groups) {
            List<FrontierProjection.Slot> slots = new ArrayList<FrontierProjection.Slot>();
            for (Node node : nodes) {
                Node root = node.root();
                Integer group = groups.get(root);
                if (group == null) {
                    group = groups.size();
                    groups.put(root, group);
                }
                slots.add(new FrontierProjection.Slot(group, root.fixed));
            }
            return slots;
        }
    }

    private static final class Node {
        Node parent = this;
        SemanticTermSnapshot fixed;

        Node root() {
            if (parent != this) {
                parent = parent.root();
            }
            return parent;
        }
    }
}
