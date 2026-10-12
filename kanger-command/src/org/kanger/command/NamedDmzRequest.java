/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.command;

import java.util.*;
import org.kanger.interfaces.DmzSourcesQuery;
import org.kanger.interfaces.internal.IContextFederation;

/** Detached name/revision request. Names are resolved only at execution. */
public final class NamedDmzRequest {
    public final String authority, query;
    public final List<String> options;
    public NamedDmzRequest(String authority,String query,List<String> options) {
        namePin(authority);
        this.authority=authority;this.query=query;
        this.options=Collections.unmodifiableList(new ArrayList<String>(options));
        List<String> checked=new ArrayList<String>();
        for(String option:options) {
            if(option.startsWith("from=")) {
                String value=option.substring(5);namePin(value);
                checked.add("from="+name(value)+"@"+UUID.nameUUIDFromBytes(name(value).getBytes(java.nio.charset.StandardCharsets.UTF_8))+"@"+revision(value));
            } else checked.add(option);
        }
        DmzCommandArguments.parse(UUID.nameUUIDFromBytes(name(authority).getBytes(java.nio.charset.StandardCharsets.UTF_8))+"@"+revision(authority),query,checked);
    }
    public DmzSourcesQuery.Request resolve(IContextFederation federation) throws Exception {
        List<String> resolved=new ArrayList<String>();
        for(String option:options)resolved.add(option.startsWith("from=")
                ? "from="+name(option.substring(5))+"@"+pin(federation,option.substring(5)) : option);
        return DmzCommandArguments.parse(pin(federation,authority),query,resolved);
    }
    private static String pin(IContextFederation federation,String text) throws Exception {
        org.kanger.interfaces.IContextResults.Revision pin=federation.resolveDmzRevision(name(text),revision(text));
        if(pin.getRevision()!=revision(text))throw new IllegalArgumentException("Exact revision was not resolved: "+text);
        return pin.getContextId()+"@"+pin.getRevision();
    }
    private static void namePin(String text) {
        if(text==null || text.lastIndexOf('@')<1 || text.indexOf('@')!=text.lastIndexOf('@'))
            throw new IllegalArgumentException("Expected Context name@revision");
        if(name(text).trim().isEmpty())throw new IllegalArgumentException("Context name required");
        revision(text);
    }
    private static String name(String text) { return text.substring(0,text.lastIndexOf('@')); }
    private static long revision(String text) {
        String value=text.substring(text.lastIndexOf('@')+1);
        if(!value.matches("[0-9]+"))throw new IllegalArgumentException("Exact nonnegative revision required");
        try { return Long.parseLong(value); }
        catch(NumberFormatException invalid) { throw new IllegalArgumentException("Revision overflow",invalid); }
    }
}
