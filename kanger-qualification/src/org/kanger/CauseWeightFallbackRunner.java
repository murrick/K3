package org.kanger;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.*;
import org.kanger.primitives.*;
import org.kanger.units.*;
import org.kanger.storage.DB;
import org.kanger.enums.ArgumentType;

/** Expected resolution failures must retain the reference's caught-error behavior. */
public final class CauseWeightFallbackRunner {
    private static final class ThrowingArgument extends Argument {
        ThrowingArgument(ITerm term) { super(term); }
        @Override public ITerm getValue(IMind mind) throws Exception { throw new IOException("CAUSE_WEIGHT_EXPECTED_FAULT"); }
    }
    private static ArgumentsList args(ITerm value) {
        ArgumentsList list = new ArgumentsList(); list.add(new Argument(value)); return list;
    }
    private static void field(Object target, String name, Object value) throws Exception {
        Field f = Argument.class.getDeclaredField(name); f.setAccessible(true); f.set(target,value);
    }
    public static void main(String[] ignored) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("cause-fallback-").toString());
        User user = (User) UserFactory.createUser("fault", "fault");
        new DB().init(user);
        Mind mind = (Mind) new Mind(user).useStorage("cause-fault");
        try {
            for (String scenario : Arrays.asList("custom", "missing")) {
                for (String mode : Arrays.asList("off", "on", "verify")) {
                    System.setProperty("kanger.experiment.resolvedCauseWeights", String.valueOf(!mode.equals("off")));
                    System.setProperty("kanger.experiment.compactCauseWeights", "true");
                    System.setProperty("kanger.experiment.shadowCauseWeights", String.valueOf(mode.equals("verify")));
                    ITerm term = mind.getTerms().add("a");
                    Predicate predicate = mind.getPredicates().add(mind.getTerms().add("p"),1);
                    Rule rule = new Rule(mind);
                    CachedDomain domain = new CachedDomain(predicate,false,args(term),rule);
                    Cause good = new Cause(domain,new Domain(predicate,true,args(term),rule),mind);
                    Cause bad = new Cause(domain,new Domain(predicate,true,args(term),rule),mind);
                    Argument broken = new ThrowingArgument(mind.getTerms().add("broken"));
                    String message = "CAUSE_WEIGHT_EXPECTED_FAULT";
                    if (scenario.equals("missing")) {
                        broken = new Argument(); field(broken,"type",ArgumentType.TERM);
                        field(broken,"id",Long.MAX_VALUE); field(broken,"o",null);
                        message = "missing TERM id=" + Long.MAX_VALUE;
                    }
                    bad.getDonor().getArguments().set(0,broken);
                    Map<ArgumentsList,Set<ICause>> map = new HashMap<>();
                    Set<ICause> sources = Collections.newSetFromMap(new IdentityHashMap<ICause,Boolean>());
                    sources.add(good); sources.add(bad);
                    if (new HashSet<ICause>(sources).size() != 2)
                        throw new AssertionError("fault fixture must contain two distinct causes");
                    map.put(domain.getArguments().convertBase(mind),sources);
                    mind.getDomainCauses().put(domain,map);
                    long before = CachedDomain.experimentalCauseWeightProfile()[5];
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    PrintStream previous = System.err;
                    Set<ICause> result;
                    try { System.setErr(new PrintStream(bytes)); result = domain.getCauses(mind); }
                    finally { System.setErr(previous); }
                    if (result.size()!=1 || !result.contains(good)) throw new AssertionError("fallback result " + scenario + mode);
                    String log = bytes.toString("UTF-8");
                    if (!log.contains(message) || log.indexOf(message)!=log.lastIndexOf(message))
                        throw new AssertionError("expected one reference diagnostic: " + log);
                    if (!mode.equals("off") && CachedDomain.experimentalCauseWeightProfile()[5]!=before+1)
                        throw new AssertionError("fallback not entered");
                    System.out.println("CAUSE_FALLBACK_PASS " + scenario + " " + mode);
                }
            }
        } finally { mind.closeStorage(); }
    }
}
