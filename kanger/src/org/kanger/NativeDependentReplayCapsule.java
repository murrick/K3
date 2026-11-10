/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.NativeDependentMatchFrame.*;
import org.kanger.NativeDependentWitnessTerms.*;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;
import org.kanger.units.Term;

/** Pinned historical replay, never a physical-provider lease or admission receipt. */
final class NativeDependentReplayCapsule {
    private static final int MAGIC=0x4b334450,VERSION=1,PAIR_VERSION=2;
    static final class Limits {
        final int bytes,rows,steps,work,nodes;
        Limits(int bytes,int rows,int steps,int work,int nodes) {
            if(bytes<1||bytes>16777216||rows<1||rows>10000||steps<1||steps>10000||work<1||nodes<1||nodes>1000000)
                throw new IllegalArgumentException("Bounded dependent capsule limits required");
            this.bytes=bytes;this.rows=rows;this.steps=steps;this.work=work;this.nodes=nodes;
        }
    }
    static final class Pin {
        private final byte[] digest;
        private Pin(byte[] digest){this.digest=digest.clone();}
        String hex(){StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
        static Pin fromHex(String text) {
            if(text==null||!text.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Exact SHA256 pin required");
            byte[] bytes=new byte[32];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);return new Pin(bytes);
        }
    }
    static final class Saved {
        private final byte[] packet;
        final Pin pin;
        private Saved(byte[] packet)throws Exception{this.packet=packet.clone();pin=new Pin(hash(packet));}
        byte[] bytes(){return packet.clone();}
    }
    static final class Reopened implements AutoCloseable {
        final NativeDependentMatchFrame frame;
        final Materialization materialization;
        /** Explicit occurrence bijection: saved inventory-local Source -> newly compiled Source. */
        final Map<Source,Source> sources;
        private Reopened(NativeDependentMatchFrame frame,Materialization materialization,Map<Source,Source> sources) {
            this.frame=frame;this.materialization=materialization;this.sources=Collections.unmodifiableMap(new LinkedHashMap<>(sources));
        }
        void verify()throws Exception{frame.verify(materialization);}
        @Override public void close(){frame.close();}
    }
    private static final class Budget {
        private int remaining;
        Budget(Limits limits){remaining=limits.work;}
        void spend(int n){if(n<0||n>remaining)throw new IllegalStateException("Dependent capsule work limit");remaining-=n;}
    }
    private static final class BoundedBytes extends ByteArrayOutputStream {
        private final int maximum;
        BoundedBytes(int maximum){super(Math.min(1024,maximum));this.maximum=maximum;}
        private void room(int n){if(n<0||n>maximum-count)throw new IllegalStateException("Dependent capsule byte limit");}
        @Override public synchronized void write(int value){room(1);super.write(value);}
        @Override public synchronized void write(byte[] bytes,int offset,int length){room(length);super.write(bytes,offset,length);}
    }
    private static byte[] hash(byte[] bytes)throws Exception{return MessageDigest.getInstance("SHA-256").digest(bytes);}
    private static void bytes(DataOutputStream out,byte[] bytes,Budget budget)throws Exception{budget.spend(bytes.length);out.writeInt(bytes.length);out.write(bytes);}
    private static byte[] bytes(DataInputStream in,Limits limits,Budget budget)throws Exception {
        int n=in.readInt();if(n<0||n>limits.bytes||n>in.available())throw new IOException("Invalid dependent capsule byte length");
        budget.spend(n);byte[] bytes=new byte[n];in.readFully(bytes);return bytes;
    }
    private static void text(DataOutputStream out,String text,Limits limits,Budget budget)throws Exception {
        if(text==null||text.length()>limits.bytes)throw new IOException("Invalid dependent capsule text length");
        ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
        byte[] bytes=new byte[encoded.remaining()];encoded.get(bytes);bytes(out,bytes,budget);
    }
    private static String text(DataInputStream in,Limits limits,Budget budget)throws Exception {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes(in,limits,budget))).toString();
    }
    private static int count(DataInputStream in,int maximum)throws Exception {
        int n=in.readInt();if(n<0||n>maximum)throw new IOException("Invalid dependent capsule count");return n;
    }
    private static int ordinal(Map<Long,Integer> rows,long id)throws Exception {
        Integer n=rows.get(id);if(n==null)throw new IOException("Missing whole dependent source");return n;
    }
    private static final class Transcript {
        final DataOutputStream out;
        final Limits limits;
        final Budget budget;
        final Map<Long,Integer> rows;
        final IdentityHashMap<Node,Integer> seen=new IdentityHashMap<>();
        Transcript(DataOutputStream out,Limits limits,Budget budget,Map<Long,Integer> rows){this.out=out;this.limits=limits;this.budget=budget;this.rows=rows;}
        void nodes(List<Node> nodes)throws Exception{out.writeInt(nodes.size());for(Node node:nodes)node(node);}
        void node(Node start)throws Exception {
            Deque<Node> pending=new ArrayDeque<>();pending.push(start);
            while(!pending.isEmpty()) {
                budget.spend(1);Node node=pending.pop();Integer id=seen.get(node);
                if(id!=null){out.writeByte(3);out.writeInt(id);continue;}
                if(seen.size()==limits.nodes)throw new IOException("Dependent capsule transcript node limit");seen.put(node,seen.size());
                switch(node.kind) {
                    case VARIABLE:out.writeByte(0);break; // Encounter identity, never arena/variable ID.
                    case VALUE:out.writeByte(1);value(node.value);break;
                    case APPLICATION:
                        out.writeByte(2);out.writeInt(ordinal(rows,node.symbol.nativeRule));out.writeInt(node.symbol.binder);out.writeInt(node.arguments.size());
                        for(int i=node.arguments.size()-1;i>=0;i--){budget.spend(1);pending.push(node.arguments.get(i));}break;
                    default:throw new IOException("Unknown dependent node kind");
                }
            }
        }
        private void value(SemanticTermSnapshot snapshot)throws Exception {
            Deque<Term> pending=new ArrayDeque<>();pending.push(snapshot.materialize());
            while(!pending.isEmpty()) {
                budget.spend(1);Term term=pending.pop();text(out,term.getType().name(),limits,budget);
                Object value=term.getValue();out.writeBoolean(value!=null);if(value==null)continue;
                switch(term.getType()) {
                    case NUMERIC:out.writeLong(Double.doubleToLongBits(((Number)value).doubleValue()));break;
                    case DATE:out.writeLong(((Date)value).getTime());break;
                    case STRING:case PERIOD:text(out,(String)value,limits,budget);break;
                    case BLOB:bytes(out,(byte[])value,budget);break;
                    case SET:case INTERVAL:
                        List<?> members=new ArrayList<>((Collection<?>)value);out.writeInt(members.size());
                        for(int i=members.size()-1;i>=0;i--){budget.spend(1);pending.push((Term)members.get(i));}break;
                    case TERM:pending.push((Term)value);break;
                    default:throw new IOException("Unsupported non-null ordinary payload");
                }
            }
        }
        void negatives(List<Negative> literals)throws Exception {
            out.writeInt(literals.size());for(Negative literal:literals){budget.spend(1);text(out,literal.predicate,limits,budget);out.writeInt(literal.originalLiteral);nodes(literal.arguments);}
        }
    }
    private static byte[] transcript(Materialization m,Map<Long,Integer> rows,Limits limits,Budget budget)throws Exception {
        BoundedBytes buffer=new BoundedBytes(limits.bytes);DataOutputStream out=new DataOutputStream(buffer);Transcript t=new Transcript(out,limits,budget,rows);
        out.writeInt(m.producers.size());
        for(int i=0;i<m.producers.size();i++) {
            budget.spend(1);Atom atom=m.producers.get(i);out.writeInt(ordinal(rows,atom.nativeRule));out.writeInt(ordinal(rows,m.producerSources.get(i).rule));
            text(out,atom.predicate,limits,budget);t.nodes(atom.arguments);t.nodes(atom.binders);
        }
        out.writeInt(ordinal(rows,m.receiverSource.rule));out.writeInt(m.receiverBranch);t.negatives(m.receiver);t.negatives(m.remaining);
        out.writeInt(m.support.depth);List<Integer> support=new ArrayList<>();for(Source source:m.support.sources){budget.spend(1);support.add(ordinal(rows,source.rule));}
        Collections.sort(support);out.writeInt(support.size());for(int source:support)out.writeInt(source);out.flush();return buffer.toByteArray();
    }
    /** Canonical typed graph for independent reconstruction; not a capsule or admission receipt. */
    static byte[] atomGraph(List<Atom> atoms,Map<Long,Integer> rows,Limits limits)throws Exception {
        Budget budget=new Budget(limits);BoundedBytes buffer=new BoundedBytes(limits.bytes);DataOutputStream out=new DataOutputStream(buffer);
        Transcript transcript=new Transcript(out,limits,budget,rows);out.writeInt(atoms.size());
        for(Atom atom:atoms){budget.spend(1);out.writeInt(ordinal(rows,atom.nativeRule));text(out,atom.predicate,limits,budget);transcript.nodes(atom.arguments);transcript.nodes(atom.binders);}
        out.flush();return buffer.toByteArray();
    }
    static Saved capture(NativeDependentMatchFrame frame,Match match,Limits limits)throws Exception {
        return captureProjected(frame,match,limits,null,null);
    }
    /** Metadata projection only, not provider authentication. Caller verifies every actual native row. */
    static Saved captureProjected(NativeDependentMatchFrame frame,Match match,Limits limits,ContextProductionInventory original,Map<Long,Long> originalToReplay)throws Exception {
        ReplayPlan plan=frame.replayPlan(match);ContextProductionInventory replay=plan.inventory;
        ContextProductionInventory inventory=original==null?replay:original;
        if(!inventory.initialization.isEmpty()||inventory.rows.size()!=inventory.primary.size()||!replay.initialization.isEmpty()||replay.rows.size()!=replay.primary.size())throw new IOException("Capsule requires primary-only inventory and empty initialization");
        if(inventory.rows.size()!=replay.rows.size()||inventory.rows.size()>limits.rows||plan.steps.isEmpty()||plan.steps.size()>limits.steps)throw new IOException("Dependent capsule shape limit");
        if(!inventory.context.equals(replay.context)||inventory.revision!=replay.revision||inventory.localX!=replay.localX)throw new IOException("Different projected context coordinate");
        if(original!=null && originalToReplay==null)throw new IOException("Complete Source projection required");
        return encode(frame,plan,inventory,inventory.rows,original==null?null:originalToReplay,limits,VERSION);
    }
    /** Capture exactly the verified roots, including from a view with generated rows. */
    static Saved capturePair(NativeDependentMatchFrame frame,Match match,Limits limits)throws Exception {
        ReplayPlan plan=frame.replayPlan(match);
        if(!plan.materialization.remaining.isEmpty() || plan.materialization.support.sources.size()!=2
                || !plan.inventory.initialization.isEmpty() || frame.entries().size()!=2)
            throw new IOException("Complete dependent pair refutation required");
        Set<Long> ids=new HashSet<>();
        for(Source source:plan.materialization.support.sources)ids.add(source.rule);
        List<ContextProductionInventory.Row> roots=new ArrayList<>();
        for(ContextProductionInventory.Row row:plan.inventory.primary)
            if(ids.contains(row.nativeRule))roots.add(row);
        if(roots.size()!=2 || roots.size()>limits.rows || plan.steps.size()>limits.steps)
            throw new IOException("Dependent pair capsule shape limit");
        return encode(frame,plan,plan.inventory,roots,null,limits,PAIR_VERSION);
    }
    private static Saved encode(NativeDependentMatchFrame frame,ReplayPlan plan,ContextProductionInventory inventory,
            List<ContextProductionInventory.Row> selected,Map<Long,Long> originalToReplay,Limits limits,int version)throws Exception {
        ContextProductionInventory replay=plan.inventory;
        Budget budget=new Budget(limits);BoundedBytes buffer=new BoundedBytes(limits.bytes);DataOutputStream out=new DataOutputStream(buffer);
        out.writeInt(MAGIC);out.writeInt(version);out.writeLong(inventory.context.getMostSignificantBits());out.writeLong(inventory.context.getLeastSignificantBits());
        out.writeLong(inventory.revision);out.writeBoolean(inventory.localX);text(out,"inventory:"+inventory.fingerprint,limits,budget);
        Map<Long,ContextProductionInventory.Row> replayRows=new LinkedHashMap<>();for(ContextProductionInventory.Row row:replay.rows){budget.spend(1);replayRows.put(row.nativeRule,row);}
        Map<Long,Integer> rows=new LinkedHashMap<>();out.writeInt(selected.size());
        if(originalToReplay!=null&&originalToReplay.size()!=selected.size())throw new IOException("Complete Source projection required");
        for(ContextProductionInventory.Row row:selected) {
            budget.spend(1);Long id=originalToReplay==null?Long.valueOf(row.nativeRule):originalToReplay.get(row.nativeRule);
            ContextProductionInventory.Row copy=replayRows.get(id);
            if(copy==null||!Objects.equals(row.origin,copy.origin)||rows.put(id,rows.size())!=null)throw new IOException("Non-bijective whole original projection");
            out.writeLong(row.nativeRule);text(out,row.origin,limits,budget);
        }
        out.writeInt(ordinal(rows,plan.receiver));out.writeInt(plan.branch);out.writeInt(plan.steps.size());
        for(ReplayStep step:plan.steps){budget.spend(1);out.writeInt(ordinal(rows,step.producer));out.writeInt(step.pivot);}
        bytes(out,transcript(plan.materialization,rows,limits,budget),budget);out.flush();frame.verify(plan.materialization);return new Saved(buffer.toByteArray());
    }
    /** Caller supplies an independently retained exact pin. Reopening proves only the historical packet. */
    static Reopened reopen(byte[] packet,Pin expected,Limits limits)throws Exception {
        return reopen(packet,expected,limits,VERSION);
    }
    static Reopened reopenPair(byte[] packet,Pin expected,Limits limits)throws Exception {
        return reopen(packet,expected,limits,PAIR_VERSION);
    }
    private static Reopened reopen(byte[] packet,Pin expected,Limits limits,int version)throws Exception {
        if(packet==null||expected==null||packet.length>limits.bytes)throw new IOException("Bounded pinned capsule required");
        byte[] copy=packet.clone();if(!MessageDigest.isEqual(hash(copy),expected.digest))throw new IOException("Different dependent capsule pin");
        Budget budget=new Budget(limits);DataInputStream in=new DataInputStream(new ByteArrayInputStream(copy));
        if(in.readInt()!=MAGIC||in.readInt()!=version)throw new IOException("Unsupported dependent capsule version");
        UUID context=new UUID(in.readLong(),in.readLong());long revision=in.readLong();int flag=in.readUnsignedByte();if(flag>1)throw new IOException("Noncanonical dependent capsule flag");boolean localX=flag==1;
        String view=text(in,limits,budget);if(!view.matches("inventory:[0-9a-f]{64}"))throw new IOException("Invalid saved inventory view");
        int size=count(in,limits.rows);if(size==0||version==PAIR_VERSION&&size!=2)throw new IOException("Invalid dependent capsule inventory size");
        List<Source> saved=new ArrayList<>();List<String> origins=new ArrayList<>();long previous=-1;
        for(int i=0;i<size;i++) {
            budget.spend(1);long id=in.readLong();if(id<=previous)throw new IOException("Nonunique or unordered saved source");previous=id;
            saved.add(new Source(context,revision,id,localX,view));String origin=text(in,limits,budget);
            if(!origin.trim().startsWith("!"))throw new IOException("Original assertion required");origins.add(origin);
        }
        int receiver=in.readInt(),branch=in.readInt(),steps=count(in,limits.steps);
        if(receiver<0||receiver>=size||branch<0||steps==0||version==PAIR_VERSION&&steps!=2)throw new IOException("Invalid dependent receiver/steps");
        int[] producers=new int[steps],pivots=new int[steps];
        for(int i=0;i<steps;i++){budget.spend(1);producers[i]=in.readInt();pivots[i]=in.readInt();if(producers[i]<0||producers[i]>=size||pivots[i]<0)throw new IOException("Invalid dependent replay step");}
        byte[] claimed=bytes(in,limits,budget);if(in.available()!=0)throw new IOException("Trailing dependent capsule data");
        User user=new User();Mind mind=new Mind(user);user.setCurrentMind(mind);List<Rule> rules=new ArrayList<>();
        for(String origin:origins){budget.spend(1);Rule rule=(Rule)mind.compileLine(origin,false,new LinkedList<ITerm>());if(rule==null)throw new IOException("Missing rebuilt original");rules.add(rule);}
        NativeDependentMatchFrame frame=NativeDependentMatchFrame.open(mind,context,revision,localX,Collections.emptyList(),limits.rows,limits.steps,limits.work,limits.nodes);
        try {
            Map<Source,Source> sources=new LinkedHashMap<>();Map<Long,Integer> rows=new LinkedHashMap<>();
            ContextProductionInventory rebuilt=frame.inventory();
            if(rebuilt.rows.size()!=size||rebuilt.primary.size()!=size||frame.entries().size()!=size)
                throw new IOException("Different rebuilt whole inventory, including auxiliary rows");
            for(int i=0;i<size;i++){budget.spend(1);Entry entry=frame.entry(rules.get(i));if(!entry.origin.equals(origins.get(i)))throw new IOException("Different rebuilt whole origin");sources.put(saved.get(i),entry.source);rows.put(entry.source.rule,i);}
            Instance instance=frame.instantiate(frame.entry(rules.get(receiver)),branch);Match last=null;
            for(int i=0;i<steps;i++) {
                budget.spend(1);last=frame.match(frame.entry(rules.get(producers[i])),instance,pivots[i]);if(last==null)throw new IOException("Reopened dependent step does not unify");instance=last.after;
            }
            Materialization materialization=frame.materialize(last);byte[] actual=transcript(materialization,rows,limits,budget);
            if(!Arrays.equals(claimed,actual))throw new IOException("Different independently rebuilt dependent transcript");
            if(version==PAIR_VERSION && (!materialization.remaining.isEmpty() || materialization.support.sources.size()!=2))
                throw new IOException("Incomplete dependent pair replay");
            frame.verify(materialization);return new Reopened(frame,materialization,sources);
        }catch(Exception failure){frame.close();throw failure;}
    }
}
