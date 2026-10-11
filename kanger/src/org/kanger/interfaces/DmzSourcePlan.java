/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.interfaces;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Historical source preflight observations, not an execution or replay permission.
 * Sources are explicit published pins; this DTO carries no claims or native owners.
 */
public final class DmzSourcePlan {
    public enum Role { AUTHORITY_Q, AUTHORITY_COMPONENT, EXTERNAL }
    public static final class Selection {
        public final String locator;
        public final IContextResults.Revision revision;
        public Selection(String locator, IContextResults.Revision revision) {
            if(locator==null || revision==null) throw new NullPointerException();
            if(locator.isEmpty() || !locator.equals(locator.trim()) || locator.contains("/")
                    || locator.contains("\\") || locator.contains("@") || locator.equals(".") || locator.equals(".."))
                throw new IllegalArgumentException("One catalog Context name and explicit revision required");
            if(revision.getCommune()!=null || !revision.getCommuneMembers().isEmpty())
                throw new IllegalArgumentException("Physical Context pin required");
            this.locator=locator; this.revision=revision;
        }
    }
    public static final class Request {
        public final IContextResults.Revision expectedQ;
        public final List<Selection> external;
        public final int maximumRows;
        public Request(IContextResults.Revision expectedQ,List<Selection> external,int maximumRows) {
            if(expectedQ==null || external==null)throw new NullPointerException();
            if(maximumRows<1 || maximumRows>10000 || external.size()>64)
                throw new IllegalArgumentException("Bounded explicit source selection required");
            if(expectedQ.getCommune()!=null || !expectedQ.getCommuneMembers().isEmpty())
                throw new IllegalArgumentException("Physical Q pin required");
            java.util.Set<java.util.UUID> contexts=new java.util.HashSet<java.util.UUID>();
            contexts.add(expectedQ.getContextId());
            for(Selection selection:external)
                if(selection==null || !contexts.add(selection.revision.getContextId()))
                    throw new IllegalArgumentException("Unique source Contexts required");
            this.expectedQ=expectedQ; this.external=Collections.unmodifiableList(new ArrayList<Selection>(external));
            this.maximumRows=maximumRows;
        }
    }
    public static final class Source {
        public final String locator;
        public final IContextResults.Revision revision;
        /** Semantic participant role, independent of replay-journal authority labels. */
        public final Role role;
        public final boolean configured;
        public final int primaryRules, inspectedRows;
        /** Digest of ordered source-local primary rule IDs and origins; not a proof certificate. */
        public final String primaryDigest;
        public Source(String locator,IContextResults.Revision revision,Role role,int primaryRules,int inspectedRows,String digest) {
            this(locator,revision,role,primaryRules,inspectedRows,digest,false);
        }
        public Source(String locator,IContextResults.Revision revision,Role role,int primaryRules,int inspectedRows,String digest,boolean configured) {
            if(locator==null || revision==null || role==null || digest==null)throw new NullPointerException();
            if(primaryRules<0 || inspectedRows<primaryRules || inspectedRows>10000 || !digest.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Source metadata");
            this.locator=locator; this.revision=revision; this.role=role;this.configured=configured;
            this.primaryRules=primaryRules; this.inspectedRows=inspectedRows; primaryDigest=digest;
        }
    }
    public final Source authority;
    public final List<Source> external;
    public final List<Source> authorityComponents;
    public final int inspectedRows, maximumRows, qTransactionLevel;
    public DmzSourcePlan(Source authority,List<Source> external,int maximumRows,int qTransactionLevel) {
        this(authority,java.util.Collections.<Source>emptyList(),external,maximumRows,qTransactionLevel);
    }
    public DmzSourcePlan(Source authority,List<Source> components,List<Source> external,int maximumRows,int qTransactionLevel) {
        if(authority==null || components==null || external==null)throw new NullPointerException();
        if(authority.role!=Role.AUTHORITY_Q || maximumRows<1 || maximumRows>10000 || qTransactionLevel<0 || external.size()>64 || components.size()>64)
            throw new IllegalArgumentException("Q metadata");
        int rows=authority.inspectedRows;
        java.util.Set<java.util.UUID> identities=new java.util.HashSet<java.util.UUID>();
        identities.add(authority.revision.getContextId());
        for(Source source:components) {
            if(source==null || source.role!=Role.AUTHORITY_COMPONENT || !identities.add(source.revision.getContextId()))
                throw new IllegalArgumentException("Distinct authority components required");
            rows+=source.inspectedRows;
        }
        authorityComponents=Collections.unmodifiableList(new ArrayList<Source>(components));
        for(Source source:external) {
            if(source==null || source.role!=Role.EXTERNAL || !identities.add(source.revision.getContextId()))
                throw new IllegalArgumentException("External metadata");
            rows+=source.inspectedRows;
        }
        if(rows>maximumRows)throw new IllegalArgumentException("Source row budget exceeded");
        this.authority=authority; this.external=Collections.unmodifiableList(new ArrayList<Source>(external));
        inspectedRows=rows; this.maximumRows=maximumRows; this.qTransactionLevel=qTransactionLevel;
    }
}
