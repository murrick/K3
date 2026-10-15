/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.enums.QueryPass;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.Term;

/** Primary existential identity survives generated materialization. */
public final class ContextWitnessPrimaryIdentityRunner {
    private static int checks;
    private static void require(boolean value,String label) {
        ++checks; if (!value) throw new AssertionError(label);
    }
    private static Mind memory() throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m); return m;
    }
    private static java.util.Map<String,java.util.Set<Long>> witnesses(Mind mind) throws Exception {
        java.util.Map<String,java.util.Set<Long>> result = new java.util.HashMap<>();
        for (Object value : mind.getRules()) {
            Rule rule = (Rule)value;
            if (!rule.isStored() || rule.isDeleted(mind)) continue;
            Domain domain = rule.getDomain();
            if (!domain.isAntc() || domain.getRange() == 0 || !domain.get(0).getValue(mind).isCVariable()) continue;
            String name = domain.getPredicate(mind).getName(mind);
            result.computeIfAbsent(name,k -> new java.util.HashSet<>()).add(domain.get(0).getValue(mind).getId());
        }
        return result;
    }
    private static void nativeControls() throws Exception {
        Mind m = memory();
        require(m.compile("!$x p(x,1); !@x p(x,1)->q(x,1); !@x p(x,1)->r(x,1); !@x q(x,1)&&r(x,1)->out(x,1);",null,false),
                "consistent native fork accepted");
        java.util.Map<String,java.util.Set<Long>> baseline = witnesses(m);
        require(baseline.get("p").equals(baseline.get("q")) && baseline.get("p").equals(baseline.get("r"))
                && baseline.get("p").equals(baseline.get("out")),"native fork shares one durable primary witness");
        int count = m.getRules().size();
        for (int i=0;i<3;++i) {
            require(Boolean.TRUE.equals(m.query("?",null,false)),"repeated qualification remains consistent");
            require(count == m.getRules().size() && baseline.equals(witnesses(m)),"closure and witness identities remain stable");
        }
        require(!m.compile("!@x ~out(x,1);",null,false),"native denial rejected");
        require(count == m.getRules().size() && baseline.equals(witnesses(m)),"failed write rolls back witnesses and rules");
        require(Boolean.TRUE.equals(m.query("?",null,false)),"qualification after rollback remains consistent");
        Mind independent = memory();
        require(independent.compile("!$x left(x,1); !$y right(y,1); !@x left(x,1)->q(x,1); !@x right(x,1)->q(x,1); !@x q(x,1)->out(x,1);",null,false),
                "independent existential branches accepted");
        require(witnesses(independent).get("q").size() == 2 && witnesses(independent).get("out").size() == 2,
                "alpha lookup cannot collapse different primary witnesses");
        Mind separated = memory();
        require(separated.compile("!$x q(x,1); !$y r(y,1); !@x q(x,1)&&r(x,1)->out(x,1); !@x ~out(x,1);",null,false),
                "independent witnesses cannot manufacture a shared-origin contradiction");
        require(!witnesses(separated).containsKey("out"),"independent q and r cannot manufacture an existential out");
        Mind repeated = memory();
        require(repeated.compile("!$x p(x,1); !@x p(x,1)->pair(x,x,1); !@x @y pair(x,y,1)->out(x,1);",null,false),
                "repeated witness positions retain identity");
        require(!repeated.compile("!@x ~out(x,1);",null,false),"repeated witness consequence refutes universal denial");
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-loss-").toString());
        User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind);
        require(mind.compile("!$x out(x,1);",null,false),"consistent existential base");
        Rule primary = null;
        for (Object value : mind.getRules()) if (((Rule)value).isStored()) primary = (Rule)value;
        require(primary != null,"primary witness rule exists");
        Domain base = primary.getDomain(); Term root = (Term)base.get(0).getValue(mind);
        Rule producer = new Rule(mind); mind.getRules().register(producer);
        Term child = (Term)mind.getTerms().createCVar(producer,root.getName(mind),root);
        ArgumentsList arguments = new ArgumentsList();
        arguments.add(new Argument((org.kanger.interfaces.ITerm)child)); arguments.add(new Argument(base.get(1).getValue(mind)));
        Domain negative = mind.getDomains().add(base.getPredicate(),!base.isAntc(),arguments,producer);
        require(child.getParentId(mind) == root.getId(),"transient binding retains the actual witness");
        require(negative.equalsBase(base),"opposite transient instance compares equal before rebinding");
        Rule owner = new Rule(mind); mind.getRules().register(owner); mind.setQueryPass(QueryPass.ACCEPT);
        ArgumentsList rebound = GeneratedCVarMaterializer.rebindForGeneratedRule(arguments,negative,mind,owner);
        Domain durable = mind.getDomains().add(base.getPredicate(),negative.isAntc(),rebound,owner);
        Term replacement = (Term)durable.get(0).getValue(mind);
        require(replacement.getId() == root.getId() && replacement.getParent(mind) == null,
                "ACCEPT materialization preserves the durable primary witness");
        require(replacement.getRuleId() == primary.getId(),"primary witness remains owned by its primary source");
        require(durable.equalsBase(base),"opposite instance still compares equal after materialization");
        require(negative.equalsBase(base),"source binding itself remains intact");
        require(base.isAntc() != durable.isAntc(),"polarity was not the lost information");
        mind.setQueryPass(QueryPass.CHECK);
        require(GeneratedCVarMaterializer.rebindForGeneratedRule(arguments,negative,mind,owner) == arguments,
                "CHECK bypasses durable rebinding");
        nativeControls();
        System.out.println("CONTEXT_WITNESS_PRIMARY_IDENTITY_PASS checks="+checks);
        System.exit(0);
    }
}
