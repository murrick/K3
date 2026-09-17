/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.*;
import org.kanger.storage.*;
import org.kanger.udf.UDF;
import org.kanger.units.*;

/** Normal inference and storage lifecycle; no synthetic TValue injection. */
public final class TValueIndexReopenRunner {
    private static void require(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }
    private static List<String> snapshot(Mind mind, String label) throws Exception {
        Field f = mind.getTValues().getClass().getDeclaredField("cache"); f.setAccessible(true);
        Escalera cache = (Escalera) f.get(mind.getTValues());
        List<String> values = new ArrayList<>();
        for (Object o : cache) {
            TValue v = (TValue) o;
            require(v.getTVar(mind) != null && v.getValue(mind) != null, "hydration");
            require(cache.find(v.getHash()).contains(v.getId()), "hash membership");
            values.add(v.getId() + ":" + v.getTVarId() + ":" + v.getValueId() + ":" + v.isDeleted(mind));
        }
        require(!values.isEmpty(), "nonempty inference TValue fixture");
        cache.mark(); cache.release();
        Field valid = Escalera.class.getDeclaredField("indexValid"); valid.setAccessible(true);
        require(valid.getBoolean(cache) == Boolean.getBoolean("kanger.experiment.preserveTValueIndex"), "release guard");
        Collections.sort(values);
        System.out.println(label + " tvalues=" + values);
        List<String> rules = new ArrayList<>();
        for (IRule r : mind.getRules()) if (!r.isDeleted(mind)) rules.add(((Rule) r).toString(mind));
        Collections.sort(rules); System.out.println(label + " rules=" + rules);
        List<String> result = new ArrayList<>(values); result.addAll(rules); return result;
    }
    private static List<String> query(Mind mind, String label) throws Exception {
        require(Boolean.TRUE.equals(mind.query("?$x $y path(x,y);")), label);
        List<String> rows = new ArrayList<>();
        for (Map<String, ITerm> row : mind.getValues()) {
            Map<String, String> ordered = new TreeMap<>();
            for (Map.Entry<String, ITerm> e : row.entrySet()) ordered.put(e.getKey(), e.getValue().toString());
            rows.add(ordered.toString());
        }
        Collections.sort(rows); System.out.println(label + " rows=" + rows); return rows;
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("tvalue-reopen-").toString());
        User user = (User) UserFactory.createUser("reopen", "reopen");
        new UDF().init(user); new DB().init(user);
        Mind root = new Mind(user); user.setCurrentMind(root);
        root = (Mind) root.useStorage("reopen"); user.setCurrentMind(root);
        try {
            require(root.compile("!edge(A,B); !@x @y edge(x,y) -> edge(y,x); !@x @y edge(x,y) -> path(x,y);"), "compile");
            List<String> rows = query(root, "initial");
            List<String> state = snapshot(root, "before-close");
            for (int i = 0; i < 2; i++) {
                root = (Mind) root.closeStorage(); user.setCurrentMind(root);
                root = (Mind) root.useStorage("reopen"); user.setCurrentMind(root);
                require(snapshot(root, "reopened-" + i).equals(state), "reopen state identity");
                require(query(root, "query-reopened-" + i).equals(rows), "reopen result");
            }
            Mind child = new Mind(root);
            require(child.compile("!edge(B,C);"), "child compile");
            root.release(child);
            require(query(root, "rollback").equals(rows), "rollback result");
            child = new Mind(root);
            require(child.compile("!edge(B,D);"), "commit compile");
            require(root.commit(child), "commit");
            rows = query(root, "committed"); state = snapshot(root, "committed-before-close");
            root = (Mind) root.closeStorage(); user.setCurrentMind(root);
            root = (Mind) root.useStorage("reopen"); user.setCurrentMind(root);
            require(snapshot(root, "committed-reopened").equals(state), "committed reopen state");
            require(query(root, "committed-reopened-query").equals(rows), "committed reopen rows");
            require(!Boolean.TRUE.equals(root.query("!~edge(B,D);")), "collision rejected");
            require(query(root, "after-collision").equals(rows), "collision result");
            System.out.println("TVALUE_INDEX_REOPEN_PASS");
        } finally { user.setCurrentMind(user.getCurrentMind().closeStorage()); }
    }
}
