/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import org.kanger.primitives.TVariableSet;
import org.kanger.units.*;

/** Exercises untracked public-map/list aliases, including aliases retained across clear. */
public final class LatentSolveSyncSafetyRunner {
    private static final class ExtendedMind extends Mind {
        private final Map<TVariableSet, List<TSolve>> custom = new LinkedHashMap<>();
        ExtendedMind() throws Exception { super(new User()); }
        @Override public Map<TVariableSet, List<TSolve>> getRuleSolves() { return custom; }
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("solve-sync-safety-").toString());
        System.setProperty("kanger.experiment.versionedSolveSync", "true");
        System.setProperty("kanger.experiment.profileSolveScans", "true");
        Mind mind = new Mind(new User());
        Linker linker = new Linker(mind);
        Method sync = Linker.class.getDeclaredMethod("synchronizeSolveIndex");
        sync.setAccessible(true);
        Method clear = Linker.class.getDeclaredMethod("clearSolveIndex");
        clear.setAccessible(true);
        Method candidates = Linker.class.getDeclaredMethod("getSolveCandidates",
                TVariableSet.class, TVariable.class, TValue.class);
        candidates.setAccessible(true);
        TVariable variable = mind.getTVars().createTVar(new Rule(mind), mind.getTerms().add("x"));
        TValue one = mind.getTValues().add(variable, mind.getTerms().add(1));
        TValue two = mind.getTValues().add(variable, mind.getTerms().add(2));
        TSolve first = mind.addTSolve(Collections.singletonList(one));
        long version = mind.ruleSolvesVersion();
        require(mind.addTSolve(Collections.singletonList(one)) == first, "dedup identity");
        require(version == mind.ruleSolvesVersion(), "dedup changed version");
        sync.invoke(linker);
        sync.invoke(linker);
        require(Linker.experimentalSolveScanProfile()[0] == (Boolean.getBoolean("kanger.experiment.verifySolveSync") ? 2 : 1), "unchanged skip/reference");
        TSolve second = mind.addTSolve(Collections.singletonList(two));
        require(version != mind.ruleSolvesVersion(), "append did not change version");
        sync.invoke(linker);
        TVariableSet key = new TVariableSet(Collections.singletonList(one), mind);
        require(((List<?>) candidates.invoke(linker, key, variable, two)).contains(second), "new tuple missing");

        long beforeReset = Linker.experimentalSolveScanProfile()[0];
        clear.invoke(linker); // Same authoritative version, empty private index.
        sync.invoke(linker);
        require(Linker.experimentalSolveScanProfile()[0] == beforeReset + 1, "index reset skipped scan");
        require(((List<?>) candidates.invoke(linker, key, variable, two)).contains(second), "reset tuple missing");

        Map<TVariableSet, List<TSolve>> alias = mind.getRuleSolves();
        TSolve external = new TSolve(Collections.singletonList(two), mind);
        version = mind.ruleSolvesVersion();
        alias.get(key).add(external);
        require(version == mind.ruleSolvesVersion(), "external mutation should bypass counter");
        sync.invoke(linker);
        require(((List<?>) candidates.invoke(linker, key, variable, two)).contains(external), "external tuple missing");
        require(mind.ruleSolvesExposed(), "alias not tracked");

        Method clearMap = Mind.class.getDeclaredMethod("clearRuleSolves");
        clearMap.setAccessible(true); clearMap.invoke(mind);
        clear.invoke(linker);
        alias.put(key, new ArrayList<>(Collections.singletonList(external)));
        sync.invoke(linker);
        sync.invoke(linker);
        require(mind.ruleSolvesExposed(), "clear forgot outstanding alias");
        
        require(((List<?>) candidates.invoke(linker, key, variable, two)).contains(external), "retained alias tuple missing");
        ExtendedMind extension = new ExtendedMind();
        TSolve empty = extension.addTSolve(Collections.<TValue>emptyList());
        require(extension.custom.size() == 1, "overridden getter bypassed on add");
        require(extension.findTSolve(Collections.<TValue>emptyList()) == empty, "overridden getter bypassed on find");
        Linker extendedLinker = new Linker(extension);
        long beforeExtension = Linker.experimentalSolveScanProfile()[0];
        sync.invoke(extendedLinker);
        sync.invoke(extendedLinker);
        require(Linker.experimentalSolveScanProfile()[0] == beforeExtension + 2,
                "subclass did not force reference");
        System.out.println("LATENT_SOLVE_SYNC_SAFETY_PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
