/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;

/** Immutable candidate statement/bounds shared by explicit diagnostic and write operations. */
public final class ContextUniversalCandidateRequest {
    public static final Map<String,Integer> DEFAULTS;
    static {
        Map<String,Integer> d=new LinkedHashMap<>();
        d.put("rows",1000); d.put("rounds",16); d.put("local",16); d.put("depth",16);
        d.put("instances",20000); d.put("models",1000000); d.put("preparation",64000000);
        d.put("events",500000); d.put("work",64000000); d.put("audit",64000000);
        DEFAULTS=Collections.unmodifiableMap(d);
    }
    public final String statement;
    public final Map<String,Integer> limits;
    public ContextUniversalCandidateRequest(String statement,Map<String,Integer> overrides) {
        if(statement==null || !statement.startsWith("!")) throw new IllegalArgumentException("Candidate assertion beginning with ! required");
        Map<String,Integer> values=new LinkedHashMap<>(DEFAULTS);
        for(Map.Entry<String,Integer> e:overrides.entrySet()) {
            String key=e.getKey(); Integer value=e.getValue();
            if(!DEFAULTS.containsKey(key) || value==null || value<0 || value==0 && !key.equals("depth") || key.equals("rows") && value>10000)
                throw new IllegalArgumentException("Invalid candidate limit: "+key);
            values.put(key,value);
        }
        this.statement=statement; limits=Collections.unmodifiableMap(values);
    }
    public int limit(String key) {
        Integer value=limits.get(key); if(value==null) throw new IllegalArgumentException("Unknown candidate limit: "+key); return value;
    }
    @Override public boolean equals(Object o) {
        return o instanceof ContextUniversalCandidateRequest && statement.equals(((ContextUniversalCandidateRequest)o).statement) && limits.equals(((ContextUniversalCandidateRequest)o).limits);
    }
    @Override public int hashCode() { return Objects.hash(statement,limits); }
}
