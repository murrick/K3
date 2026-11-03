/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.*;
import org.kanger.ContextDependentReplayInspection.Policy;
import org.kanger.ContextDependentProductionInspection.*;

/** Original-only Horn replay packet. Historical verification grants no live/provider/write authority. */
public final class ContextDependentProductionReplay {
    private ContextDependentProductionReplay(){}
    private static final int MAGIC=0x4b334850,VERSION=1;
    public static final class Saved {
        public final String pin;
        public final Result evidence;
        private final byte[] packet;
        private Saved(byte[] packet,Result evidence)throws Exception{this.packet=packet.clone();pin=hex(hash(packet));this.evidence=evidence;}
        public byte[] bytes(){return packet.clone();}
    }
    private static final class Budget {
        int remaining;Budget(Policy p){remaining=p.work;}
        void take(int n){if(n<0||n>remaining)throw new IllegalStateException("Production replay work limit");remaining-=n;}
    }
    private static final class Buffer extends ByteArrayOutputStream {
        final int maximum;Buffer(int maximum){super(Math.min(maximum,1024));this.maximum=maximum;}
        void room(int n){if(n<0||n>maximum-count)throw new IllegalStateException("Production replay byte limit");}
        @Override public synchronized void write(int value){room(1);super.write(value);}
        @Override public synchronized void write(byte[] bytes,int offset,int length){room(length);super.write(bytes,offset,length);}
    }
    private static byte[] hash(byte[] packet)throws Exception{return MessageDigest.getInstance("SHA-256").digest(packet);}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private static byte[] unhex(String text){if(text==null||!text.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Exact separately retained SHA256 pin required");byte[] out=new byte[32];for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(text.substring(2*i,2*i+2),16);return out;}
    private static void text(DataOutputStream out,String text,Policy p,Budget budget)throws Exception {
        if(text==null||text.length()>p.bytes)throw new IOException("Invalid original-only text length");ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));byte[] bytes=new byte[encoded.remaining()];encoded.get(bytes);budget.take(bytes.length);out.writeInt(bytes.length);out.write(bytes);
    }
    private static String text(DataInputStream in,Policy p,Budget budget)throws Exception {
        int length=in.readInt();if(length<0||length>p.bytes||length>in.available())throw new IOException("Invalid original-only text length");budget.take(length);byte[] bytes=new byte[length];in.readFully(bytes);return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static int count(DataInputStream in,int maximum)throws Exception {int count=in.readInt();if(count<0||count>maximum)throw new IOException("Original-only count limit");return count;}
    public static Saved capture(Mind actual,ContextProductionInventory expected,Request request)throws Exception {
        if(actual==null||request==null)throw new IllegalArgumentException("Actual original inventory and complete production recipe required");if(request.signed)throw new IllegalArgumentException("K3HP/v1 has no signed residual recipe encoding");Policy policy=request.policy;ContextDependentReplayInspection.current(actual,expected,policy);
        try {
            Result evidence=ContextDependentProductionInspection.inspect(actual,expected,request);ContextDependentProductionInspection.Originals originals=ContextDependentProductionInspection.Originals.of(expected);
            Budget budget=new Budget(policy);Buffer buffer=new Buffer(policy.bytes);DataOutputStream out=new DataOutputStream(buffer);out.writeInt(MAGIC);out.writeInt(VERSION);out.writeLong(originals.context.getMostSignificantBits());out.writeLong(originals.context.getLeastSignificantBits());out.writeLong(originals.revision);text(out,originals.fingerprint,policy,budget);
            out.writeInt(originals.rows.size());for(Map.Entry<Long,String> row:originals.rows.entrySet()){budget.take(1);out.writeLong(row.getKey());text(out,row.getValue(),policy,budget);}
            out.writeInt(request.capacity);out.writeInt(request.steps.size());
            for(Step step:request.steps){budget.take(1);out.writeLong(step.rule);out.writeInt(step.donors.size());for(Donor donor:step.donors){budget.take(1);out.writeByte(donor.original?0:1);out.writeLong(donor.reference);}}
            text(out,evidence.auditDigest,policy,budget);out.flush();Saved saved=new Saved(buffer.toByteArray(),evidence);
            // Return no file artifact unless the exact encoded bytes independently reconstruct the result.
            reopen(saved.packet,saved.pin,policy,request.capacity);return saved;
        }finally{ContextDependentReplayInspection.current(actual,expected,policy);}
    }
    /** Reconstructs products from originals; no generated graph, term ID or fact is decoded as knowledge. */
    public static Result reopen(byte[] packet,String pin,Policy policy,int maximumCapacity)throws Exception {
        if(policy==null||packet==null||packet.length>policy.bytes||maximumCapacity<1||maximumCapacity>100000)throw new IllegalArgumentException("Bounded pinned production packet required");
        byte[] expectedPin=unhex(pin),copy=packet.clone();if(!MessageDigest.isEqual(expectedPin,hash(copy)))throw new IOException("Different original-only packet pin");Budget budget=new Budget(policy);DataInputStream in=new DataInputStream(new ByteArrayInputStream(copy));
        if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("Unsupported production replay format");UUID context=new UUID(in.readLong(),in.readLong());long revision=in.readLong();String view=text(in,policy,budget);
        int rows=count(in,policy.rows);Map<Long,String> originals=new LinkedHashMap<>();long previous=-1;
        for(int i=0;i<rows;i++){budget.take(1);long id=in.readLong();if(id<=previous)throw new IOException("Original occurrences must be distinct and ordered");previous=id;originals.put(id,text(in,policy,budget));}
        int capacity=count(in,maximumCapacity);if(capacity==0)throw new IOException("Positive replay capacity required");int steps=count(in,policy.steps);List<Step> recipe=new ArrayList<>();
        for(int i=0;i<steps;i++) {
            budget.take(1);long rule=in.readLong();if(!originals.containsKey(rule))throw new IOException("Missing whole Horn occurrence");int count=count(in,policy.steps);List<Donor> donors=new ArrayList<>();
            for(int j=0;j<count;j++){budget.take(1);int tag=in.readUnsignedByte();long reference=in.readLong();if(tag==0){if(!originals.containsKey(reference))throw new IOException("Missing original donor occurrence");donors.add(Donor.original(reference));}else if(tag==1){if(reference<0||reference>=i)throw new IOException("Only earlier generated steps may be referenced");donors.add(Donor.product((int)reference));}else throw new IOException("Unknown production donor tag");}
            recipe.add(new Step(rule,donors));
        }
        String claimed=text(in,policy,budget);if(!claimed.matches("[0-9a-f]{64}")||in.available()!=0)throw new IOException("Invalid final graph digest or trailing production data");
        Result result=ContextDependentProductionInspection.reconstruct(new ContextDependentProductionInspection.Originals(context,revision,view,originals),new Request(policy,capacity,recipe));
        if(!MessageDigest.isEqual(unhex(claimed),unhex(result.auditDigest)))throw new IOException("Different graph reconstructed from original knowledge and recipe");return result;
    }
}
