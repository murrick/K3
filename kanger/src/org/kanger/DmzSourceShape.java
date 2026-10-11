/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

/** Read-only declaration-shape audit; source acquisition and proof qualification are separate. */
public final class DmzSourceShape {
    private DmzSourceShape() {}
    public static final class Declaration {
        public final long sourceRule;
        public final String statement;
        private Declaration(long id,String origin) { sourceRule=id; statement=origin; }
    }
    public static final class Observation {
        public final int primaryRules, inspectedRows;
        public final String primaryDigest;
        public final java.util.List<Declaration> declarations;
        private Observation(int rows,String digest,java.util.List<Declaration> inputs) {
            declarations=java.util.Collections.unmodifiableList(new java.util.ArrayList<Declaration>(inputs));
            primaryRules=declarations.size(); inspectedRows=rows; primaryDigest=digest;
        }
    }
    public static Observation inspect(Mind source,int maximumRows) throws Exception {
        if(source==null || maximumRows<0 || maximumRows>10000)throw new IllegalArgumentException("Bounded source inspection required");
        java.util.SortedMap<Long,Rule> primary=new java.util.TreeMap<Long,Rule>();
        int rows=0;
        for(IRule item:source.getRules()) {
            if(++rows>maximumRows)throw new IllegalStateException("DMZ source row budget exceeded");
            if(item.isDeleted(source))continue;
            Rule rule=(Rule)item;
            if(rule.isQuery())throw new IllegalArgumentException("DMZ source query rule is not qualified");
            if(source.getRules().isGenerated(rule))continue;
            if(rule.getTree().size()!=1)throw new IllegalArgumentException("DMZ source branch shape is not qualified");
            java.util.List<Domain> clause=rule.getTree().get(0);
            if(clause.size()<1 || clause.size()>2)throw new IllegalArgumentException("DMZ source unit clause required");
            long variable=-1;
            for(Domain literal:clause) {
                if(literal.isSystem(source) || literal.isCalculated(source) || literal.isQuery(source) || literal.getArguments().size()!=1)
                    throw new IllegalArgumentException("DMZ source ordinary unary domain required");
                IArgument argument=literal.getArguments().get(0);
                if(clause.size()==2) {
                    if(argument.getType()!=ArgumentType.TVARIABLE || variable>=0 && variable!=argument.getId())
                        throw new IllegalArgumentException("DMZ source shared-variable unit production required");
                    variable=argument.getId();
                } else {
                    if(argument.getType()!=ArgumentType.TERM)throw new IllegalArgumentException("DMZ source ground integer required");
                    Object value=argument.getValue(source).getValue();
                    if(!(value instanceof Number))throw new IllegalArgumentException("DMZ source ground integer required");
                    try { new java.math.BigDecimal(value.toString()).toBigIntegerExact(); }
                    catch(ArithmeticException | NumberFormatException unsupported) { throw new IllegalArgumentException("DMZ source ground integer required",unsupported); }
                }
            }
            if(rule.getOrigin()==null || rule.getOrigin().trim().isEmpty())throw new IllegalArgumentException("DMZ source declaration origin required");
            primary.put(rule.getId(),rule);
        }
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        java.util.List<Declaration> inputs=new java.util.ArrayList<Declaration>();
        for(Rule rule:primary.values()) {
            inputs.add(new Declaration(rule.getId(),rule.getOrigin()));
            byte[] origin=rule.getOrigin().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            digest.update(java.nio.ByteBuffer.allocate(12).putLong(rule.getId()).putInt(origin.length).array());
            digest.update(origin);
        }
        StringBuilder hex=new StringBuilder();
        for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return new Observation(rows,hex.toString(),inputs);
    }
}
