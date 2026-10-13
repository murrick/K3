/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.command;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.kanger.interfaces.DmzSourcesQuery;

/** Explicit traversal limit replacements for a saved selection; no acquisition changes. */
public final class DmzLimitOverrides {
    private static final String[] KEYS={"preparation","audit","lookup","recognition","projection","application","query","depth","nodes","work","visits"};
    private final Map<String,Integer> values;
    private DmzLimitOverrides(Map<String,Integer> values) { this.values=new LinkedHashMap<String,Integer>(values); }
    static DmzLimitOverrides parse(List<String> options) {
        Map<String,Integer> values=new LinkedHashMap<String,Integer>();
        for(String option:options) {
            int equals=option.indexOf('=');
            if(equals<1)throw new IllegalArgumentException("Expected traversal limit=value");
            String key=option.substring(0,equals),value=option.substring(equals+1);
            boolean known=false;for(String allowed:KEYS)known |= allowed.equals(key);
            if(!known || values.containsKey(key))throw new IllegalArgumentException("Unknown or repeated selection limit: "+key);
            if(!value.matches("[0-9]+"))throw new IllegalArgumentException("Nonnegative integer selection limit required");
            try { values.put(key,Integer.valueOf(value)); }
            catch(NumberFormatException invalid) { throw new IllegalArgumentException("Selection limit overflow"); }
        }
        DmzLimitOverrides result=new DmzLimitOverrides(values);
        result.apply(new DmzSourcesQuery.Limits(10000,10000,10000,10000,10000,10000,10000,8,128,1000000,1000));
        return result;
    }
    private int get(String key,int fallback) { return values.containsKey(key)?values.get(key):fallback; }
    public DmzSourcesQuery.Limits apply(DmzSourcesQuery.Limits l) {
        return new DmzSourcesQuery.Limits(get("preparation",l.preparation),get("audit",l.audit),get("lookup",l.lookup),
                get("recognition",l.recognition),get("projection",l.projection),get("application",l.application),get("query",l.query),
                get("depth",l.depth),get("nodes",l.nodes),get("work",l.work),get("visits",l.maximumVisits));
    }
    public String format() {
        StringBuilder out=new StringBuilder();
        for(String key:KEYS)if(values.containsKey(key))out.append(' ').append(key).append('=').append(values.get(key));
        return out.toString();
    }
}
