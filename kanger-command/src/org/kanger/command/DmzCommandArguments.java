/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.kanger.interfaces.DmzSourcePlan;
import org.kanger.interfaces.DmzSourcesQuery;
import org.kanger.interfaces.IContextResults;

/** Pure command argument normalization. No provider lookup or execution. */
final class DmzCommandArguments {
    private DmzCommandArguments() { }
    static DmzSourcesQuery.Request parse(String q,String query,List<String> options) {
        Map<String,Integer> limits=new LinkedHashMap<String,Integer>();
        limits.put("rows",10000);limits.put("preparation",10000);limits.put("audit",10000);
        limits.put("lookup",10000);limits.put("recognition",10000);limits.put("projection",10000);
        limits.put("application",10000);limits.put("query",10000);limits.put("depth",8);
        limits.put("nodes",128);limits.put("work",1000000);limits.put("visits",1000);
        java.util.Set<String> specified=new java.util.HashSet<String>();
        List<DmzSourcePlan.Selection> sources=new ArrayList<DmzSourcePlan.Selection>();
        for(String option:options) {
            int equals=option.indexOf('=');
            if(equals<1)throw new IllegalArgumentException("Expected from=name@UUID@revision or limit=value");
            String key=option.substring(0,equals),value=option.substring(equals+1);
            if("from".equals(key)) {
                int at=value.indexOf('@');
                if(at<1)throw new IllegalArgumentException("Explicit source name@UUID@revision required");
                sources.add(new DmzSourcePlan.Selection(value.substring(0,at),pin(value.substring(at+1))));
            } else {
                if(!limits.containsKey(key) || !specified.add(key))throw new IllegalArgumentException("Unknown or repeated DMZ limit: "+key);
                if(!value.matches("[0-9]+"))throw new IllegalArgumentException("Nonnegative integer limit required: "+key);
                try { limits.put(key,Integer.valueOf(value)); }
                catch(NumberFormatException invalid) { throw new IllegalArgumentException("DMZ limit overflow: "+key); }
            }
        }
        return new DmzSourcesQuery.Request(new DmzSourcePlan.Request(pin(q),sources,limits.get("rows")),
                DmzSourcesQuery.Policy.FIXED_UNITS_THEN_ORDERED_GROUNDS_CONTEXT_UUID_RULE_ID,query,
                new DmzSourcesQuery.Limits(limits.get("preparation"),limits.get("audit"),limits.get("lookup"),
                        limits.get("recognition"),limits.get("projection"),limits.get("application"),limits.get("query"),
                        limits.get("depth"),limits.get("nodes"),limits.get("work"),limits.get("visits")));
    }
    static String pin(IContextResults.Revision revision) { return revision.getContextId()+"@"+revision.getRevision(); }
    private static IContextResults.Revision pin(String value) {
        int at=value.indexOf('@');
        if(at<1 || at!=value.lastIndexOf('@'))throw new IllegalArgumentException("Exact UUID@revision required");
        String id=value.substring(0,at),number=value.substring(at+1);
        UUID context;
        try { context=UUID.fromString(id); }
        catch(IllegalArgumentException invalid) { throw new IllegalArgumentException("Canonical Context UUID required"); }
        if(!context.toString().equalsIgnoreCase(id) || !number.matches("[0-9]+"))
            throw new IllegalArgumentException("Canonical UUID and nonnegative published revision required");
        try { return new IContextResults.Revision(context,Long.parseLong(number)); }
        catch(NumberFormatException invalid) { throw new IllegalArgumentException("Published revision overflow"); }
    }
}
