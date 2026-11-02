/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.NativeDependentMatchFrame.*;
import org.kanger.NativeDependentReplayCapsule.*;
import org.kanger.NativeDependentWitnessTerms.*;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

/** Fresh-JVM whole-source replay and semantic transcript corruption tests. No DB activation. */
public final class NativeDependentReplayCapsuleRunner {
    private static int checks;
    private static final UUID CONTEXT=new UUID(0,507);
    private static final Limits LIMITS=new Limits(2000000,100,100,10000000,50000);
    private static void require(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static Mind memory(boolean offset)throws Exception {
        User user=new User();Mind m=new Mind(user);user.setCurrentMind(m);
        if(offset)for(int i=0;i<17;i++){user.nextId("rules");m.getTerms().nextVarIndex();m.getTerms().add("unused-allocation-"+i);}
        return m;
    }
    private static Rule compile(Mind m,String text)throws Exception{return (Rule)m.compileLine(text,false,new LinkedList<ITerm>());}
    private static NativeDependentMatchFrame frame(Mind m,List<String> initialization)throws Exception{return NativeDependentMatchFrame.open(m,CONTEXT,11,true,initialization,100,100,10000000,50000);}
    private static Pin repin(byte[] data)throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(data);StringBuilder s=new StringBuilder();for(byte b:hash)s.append(String.format(Locale.ROOT,"%02x",b&255));return Pin.fromHex(s.toString());
    }
    private static void refuses(byte[] data,Pin pin,Limits limits,String why)throws Exception {
        boolean no=false;try(Reopened result=NativeDependentReplayCapsule.reopen(data,pin,limits)){}catch(Exception expected){no=true;}require(no,why);
    }
    private static void validate(Reopened result,boolean refined)throws Exception {
        result.verify();Materialization m=result.materialization;
        require(m.producers.size()==2&&m.remaining.isEmpty()&&m.support.depth==2,"complete two-step historical proof independently rebuilt");
        require(result.sources.size()==(refined?4:3),"whole inventory includes unrelated original, not only proof roots");
        require(m.support.sources.size()==(refined?3:2),"no unrelated Source borrowed for proof");
        for(Source old:result.sources.keySet())require(old.context.equals(CONTEXT)&&old.revision==11&&old.localX,"saved Source coordinate retained");
        require(result.sources.keySet().stream().allMatch(s->s.rule>=17),"saved source occurrences keep original allocation IDs");
        require(result.sources.entrySet().stream().allMatch(e->e.getKey().rule!=e.getValue().rule),"fresh compiler IDs differ: explicit source bijection required");
        Node witness=m.producers.get(0).arguments.get(1);
        require(witness.kind==Kind.APPLICATION&&witness.arguments.size()==1,"binder dependency tuple reconstructed");
        if(refined){Node fixed=m.producers.get(1).arguments.get(0);require(fixed.kind==Kind.VALUE&&witness.arguments.get(0)==fixed,"late typed refinement survives cold reopen");}
        else{require(witness==m.producers.get(1).arguments.get(1),"same dependency application shared across later universal values");require(witness.arguments.get(0).kind==Kind.VARIABLE,"unmentioned parameter remains symbolic after reopen");}
        try{result.sources.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
    }
    private static void cold(Saved saved,boolean refined)throws Exception {cold(saved,Boolean.toString(refined));}
    private static void cold(Saved saved,String scenario)throws Exception {
        Path path=Files.createTempFile("dependent-capsule-",".bin");Files.write(path,saved.bytes());
        Process process=new ProcessBuilder(Paths.get(System.getProperty("java.home"),"bin","java").toString(),"-XX:-UsePerfData","-cp",System.getProperty("java.class.path"),NativeDependentReplayCapsuleRunner.class.getName(),"--child",path.toString(),saved.pin.hex(),scenario).redirectErrorStream(true).start();
        if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("Cold dependent replay timeout");}
        ByteArrayOutputStream log=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=process.getInputStream().read(b))!=-1)log.write(b,0,n);
        String text=new String(log.toByteArray(),StandardCharsets.UTF_8);require(process.exitValue()==0&&text.contains("DEPENDENT_CAPSULE_CHILD_OK"),"fresh JVM reconstructs without writer heap: "+text);Files.delete(path);
    }
    private static Saved example(String value,boolean reverse,boolean refined)throws Exception {
        Mind m=memory(true);Rule p,n,q=null;
        String negative=refined?"!@a @u ~(rel(a,u,1)&&q(a));":"!@a @u ~(rel(a,u,1)&&rel(a,u,2));";
        if(reverse){n=compile(m,negative);if(refined)q=compile(m,"!q("+value+");");p=compile(m,"!@a $u @b rel(a,u,b);");}
        else{p=compile(m,"!@a $u @b rel(a,u,b);");if(refined)q=compile(m,"!q("+value+");");n=compile(m,negative);}
        compile(m,"!unrelated(99);");String before=ContextProductionInventory.inspect(m,CONTEXT,11,true,Collections.emptyList(),100).fingerprint;
        Saved saved;
        try(NativeDependentMatchFrame f=frame(m,Collections.emptyList())) {
            Match a=f.match(f.entry(p),f.instantiate(f.entry(n),0),0),b=f.match(f.entry(refined?q:p),a.after,0);
            saved=NativeDependentReplayCapsule.capture(f,b,LIMITS);
            require(Arrays.equals(saved.bytes(),NativeDependentReplayCapsule.capture(f,b,LIMITS).bytes()),"repeated capture has deterministic bytes despite audit allocations");
            byte[] copy=saved.bytes();copy[0]^=1;require(copy[0]!=saved.bytes()[0],"saved packet is detached immutable data");
            require(before.equals(ContextProductionInventory.inspect(m,CONTEXT,11,true,Collections.emptyList(),100).fingerprint),"capture never writes native state");
        }
        try(Reopened result=NativeDependentReplayCapsule.reopen(saved.bytes(),Pin.fromHex(saved.pin.hex()),LIMITS)){
            validate(result,refined);
            if(refined)require(result.materialization.producers.get(1).arguments.get(0).value.semanticallyEquals(SemanticTermSnapshot.capture(q.getDomain().get(0).getValue(m))),"exact typed value equals original independently of operational IDs");
        }
        cold(saved,refined);return saved;
    }
    private static final class Layout {
        int rowCount,stringLength,receiver,steps,transcript;
        final List<Integer> ids=new ArrayList<>(),origins=new ArrayList<>();
        Layout(byte[] data)throws Exception {
            java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(data);b.position(8+16+8+1);int length=b.getInt();b.position(b.position()+length);
            rowCount=b.position();int size=b.getInt();
            for(int i=0;i<size;i++){ids.add(b.position());b.getLong();stringLength=b.position();length=b.getInt();origins.add(b.position());b.position(b.position()+length);}
            receiver=b.position();b.getInt();b.getInt();steps=b.position();int count=b.getInt();b.position(b.position()+count*8);length=b.getInt();transcript=b.position();require(length>0&&b.position()+length==data.length,"well formed complete transcript layout");
        }
    }
    private static void validateWide(Reopened result,int width)throws Exception {
        result.verify();Materialization m=result.materialization;
        require(m.remaining.isEmpty()&&m.producers.size()==2&&m.support.sources.size()==3,"wide proof and all original supports reconstructed");
        Node witness=m.producers.get(0).arguments.get(width);
        require(witness.kind==Kind.APPLICATION&&witness.arguments.size()==width,"arbitrary dependency count stored without fixed mask");
        for(int i=0;i<width;i++)require(witness.arguments.get(i)==m.producers.get(1).arguments.get(i),"dependency coordinate independently restored: "+i);
        require(m.receiver.get(0).arguments.equals(m.producers.get(0).arguments),"wide original receiver and final producer agree");
    }
    private static void wide()throws Exception {
        for(int width:new int[]{7,17,65}) {
            Mind m=memory(true);StringBuilder prefix=new StringBuilder(),args=new StringBuilder(),fixed=new StringBuilder();
            for(int i=0;i<width;i++){prefix.append("@x").append(i).append(" ");if(i>0){args.append(',');fixed.append(',');}args.append("x").append(i);fixed.append(i);}
            Rule p=compile(m,"!"+prefix+"$u rel("+args+",u);"),q=compile(m,"!q("+fixed+");"),n=compile(m,"!"+prefix+"@u ~(rel("+args+",u)&&q("+args+"));");Saved saved;
            try(NativeDependentMatchFrame f=frame(m,Collections.emptyList())) {
                Match a=f.match(f.entry(p),f.instantiate(f.entry(n),0),0),b=f.match(f.entry(q),a.after,0);saved=NativeDependentReplayCapsule.capture(f,b,LIMITS);
            }
            try(Reopened result=NativeDependentReplayCapsule.reopen(saved.bytes(),saved.pin,LIMITS)){validateWide(result,width);}
            cold(saved,"wide:"+width);
        }
    }
    private static void alternativesAndClauses()throws Exception {
        Mind m=memory(true);Rule u=compile(m,"!@x p(x);"),c=compile(m,"!p(1);"),n=compile(m,"!@a @b ~(p(a)&&p(b));");
        Saved small,large;
        try(NativeDependentMatchFrame f=frame(m,Collections.emptyList())) {
            Match a=f.match(f.entry(u),f.instantiate(f.entry(n),0),1);
            small=NativeDependentReplayCapsule.capture(f,f.match(f.entry(u),a.after,0),LIMITS);
            large=NativeDependentReplayCapsule.capture(f,f.match(f.entry(c),a.after,0),LIMITS);
        }
        try(Reopened a=NativeDependentReplayCapsule.reopen(small.bytes(),small.pin,LIMITS);Reopened b=NativeDependentReplayCapsule.reopen(large.bytes(),large.pin,LIMITS)) {
            require(a.materialization.support.sources.size()==2&&b.materialization.support.sources.size()==3,"Source superset alternative survives independent replay");
            require(!small.pin.hex().equals(large.pin.hex()),"different proof alternatives have distinct pinned packets");
            require(a.materialization.receiver.get(1).originalLiteral==1,"non-first pivot preserves original receiver positions");
        }
        m=memory(true);Rule p=compile(m,"!p(1);"),q=compile(m,"!q(2);"),receiver=compile(m,"!~p(1)&&~q(2);");
        try(NativeDependentMatchFrame f=frame(m,Collections.emptyList())) {
            Match second=f.match(f.entry(q),f.instantiate(f.entry(receiver),1),0);boolean no=false;
            try{NativeDependentReplayCapsule.capture(f,second,LIMITS);}catch(IOException expected){no=true;}
            require(no,"compiler auxiliary/deleted rows prevent partial whole-view export even for a valid second clause");
        }
        m=memory(true);p=compile(m,"!plain();");receiver=compile(m,"!~plain();");
        try(NativeDependentMatchFrame f=frame(m,Collections.emptyList())) {
            Saved empty=NativeDependentReplayCapsule.capture(f,f.match(f.entry(p),f.instantiate(f.entry(receiver),0),0),LIMITS);
            try(Reopened result=NativeDependentReplayCapsule.reopen(empty.bytes(),empty.pin,LIMITS)){require(result.materialization.producers.get(0).arguments.isEmpty()&&result.materialization.remaining.isEmpty(),"nullary proof persists without invented parameter");}
        }
    }
    private static void prefixScopes()throws Exception {
        String[] prefixes={"$u @a @b","@a $u @b","@a @b $u"};
        for(int i=0;i<prefixes.length;i++) {
            Mind m=memory(true);Rule p=compile(m,"!"+prefixes[i]+" rel(a,u,b);"),n=compile(m,"!@u ~(rel(1,u,1)&&rel(2,u,1));");Saved saved;
            try(NativeDependentMatchFrame f=frame(m,Collections.emptyList())) {
                Match a=f.match(f.entry(p),f.instantiate(f.entry(n),0),0),b=f.match(f.entry(p),a.after,0);
                require((b!=null)==(i==0),"cross-parameter constraint closes only for global witness");saved=NativeDependentReplayCapsule.capture(f,b==null?a:b,LIMITS);
            }
            try(Reopened result=NativeDependentReplayCapsule.reopen(saved.bytes(),saved.pin,LIMITS)) {
                Node witness=result.materialization.producers.get(0).arguments.get(1);
                require(witness.kind==Kind.APPLICATION&&witness.arguments.size()==i,"global/earlier/later parameter scope persists");
                require(result.materialization.remaining.isEmpty()==(i==0),"partial branch never becomes refutation on reopen");
            }
        }
        Mind m=memory(true);Rule p=compile(m,"!@a $u @b $v rel(v,a,u,b,u,v);"),n=compile(m,"!@a @u @v @w ~(rel(v,a,u,1,u,v)&&rel(w,a,u,2,u,w));");Saved saved;
        try(NativeDependentMatchFrame f=frame(m,Collections.emptyList())) {
            Match a=f.match(f.entry(p),f.instantiate(f.entry(n),0),0),b=f.match(f.entry(p),a.after,0);saved=NativeDependentReplayCapsule.capture(f,b,LIMITS);
        }
        try(Reopened result=NativeDependentReplayCapsule.reopen(saved.bytes(),saved.pin,LIMITS)) {
            Atom a=result.materialization.producers.get(0),b=result.materialization.producers.get(1);
            require(a.arguments.get(0)==a.binders.get(3)&&a.arguments.get(2)==a.binders.get(1),"body-first v retains declaration binder positions");
            require(a.arguments.get(2)==b.arguments.get(2),"u(a) is shared across different later universal values");
            require(a.arguments.get(0)!=b.arguments.get(0)&&a.arguments.get(0).symbol==b.arguments.get(0).symbol,"different v(a,b) applications retain same source-bound symbol without asserting value inequality");
        }
    }
    private static byte[] replaceOrigin(byte[] data,Layout layout,int row,String text)throws Exception {
        int start=layout.origins.get(row),old=java.nio.ByteBuffer.wrap(data).getInt(start-4);byte[] replacement=text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(buffer);
        out.write(data,0,start-4);out.writeInt(replacement.length);out.write(replacement);out.write(data,start+old,data.length-start-old);return buffer.toByteArray();
    }
    private static void corruption(Saved saved)throws Exception {
        byte[] bytes=saved.bytes(),changed=bytes.clone();Layout layout=new Layout(bytes);
        changed[changed.length-1]^=1;refuses(changed,saved.pin,LIMITS,"independent retained pin rejects altered packet");
        refuses(changed,repin(changed),LIMITS,"even repinned false claimed transcript fails semantic reconstruction");
        java.nio.ByteBuffer graph=java.nio.ByteBuffer.wrap(bytes);graph.position(layout.transcript);graph.getInt();graph.getInt();graph.getInt();int nameBytes=graph.getInt();graph.position(graph.position()+nameBytes);graph.getInt();
        require(graph.get()==0&&graph.get()==2,"first producer transcript exposes universal then dependent application");graph.getInt();int binderOffset=graph.position();
        changed=bytes.clone();java.nio.ByteBuffer.wrap(changed).putInt(binderOffset,0);refuses(changed,repin(changed),LIMITS,"wrong Source binder position rejected by independent graph reconstruction");
        changed=Arrays.copyOf(bytes,bytes.length-1);refuses(changed,repin(changed),LIMITS,"truncated packet refuses before source compilation");
        changed=Arrays.copyOf(bytes,bytes.length+1);refuses(changed,repin(changed),LIMITS,"trailing data cannot hide outside transcript");
        changed=bytes.clone();java.nio.ByteBuffer.wrap(changed).putInt(4,2);refuses(changed,repin(changed),LIMITS,"unknown format version refuses");
        changed=bytes.clone();java.nio.ByteBuffer.wrap(changed).putInt(layout.rowCount,Integer.MAX_VALUE);refuses(changed,repin(changed),LIMITS,"huge row count is bounded before allocation");
        changed=bytes.clone();java.nio.ByteBuffer.wrap(changed).putInt(layout.stringLength,Integer.MAX_VALUE);refuses(changed,repin(changed),LIMITS,"huge text length is bounded before allocation");
        changed=bytes.clone();java.nio.ByteBuffer.wrap(changed).putLong(layout.ids.get(1),java.nio.ByteBuffer.wrap(bytes).getLong(layout.ids.get(0)));refuses(changed,repin(changed),LIMITS,"duplicate source occurrence cannot be merged on reopen");
        changed=bytes.clone();changed[layout.origins.get(0)]=(byte)255;refuses(changed,repin(changed),LIMITS,"malformed UTF8 is rejected, never replaced by another source");
        changed=bytes.clone();java.nio.ByteBuffer.wrap(changed).putInt(layout.receiver,100);refuses(changed,repin(changed),LIMITS,"receiver points to exact whole original");
        changed=bytes.clone();java.nio.ByteBuffer.wrap(changed).putInt(layout.steps+8,100);refuses(changed,repin(changed),LIMITS,"invalid pivot cannot fabricate a branch");
        // Alter dependency scope in a valid, same-length source while keeping the old claimed result.
        changed=bytes.clone();String from="!@a $u @b rel",to="!@a @b $u rel";
        String packet=new String(bytes,StandardCharsets.ISO_8859_1);int start=packet.indexOf(from);require(start>=0,"dependency source located");
        System.arraycopy(to.getBytes(StandardCharsets.UTF_8),0,changed,start,to.length());refuses(changed,repin(changed),LIMITS,"fresh replay rejects changed quantifier dependency even with new pin");
        int unrelated=-1;for(int i=0;i<layout.origins.size();i++)if(new String(bytes,layout.origins.get(i),java.nio.ByteBuffer.wrap(bytes).getInt(layout.origins.get(i)-4),StandardCharsets.UTF_8).equals("!unrelated(99);"))unrelated=i;
        require(unrelated>=0,"unused whole original located");changed=replaceOrigin(bytes,layout,unrelated,"!~newa(1)&&~newb(2);");
        refuses(changed,repin(changed),LIMITS,"new compiler auxiliary rows cannot extend restored whole view behind unchanged proof");
        refuses(bytes,saved.pin,new Limits(bytes.length-1,100,100,10000000,50000),"packet byte limit precedes clone and decoding");
        refuses(bytes,saved.pin,new Limits(2000000,1,100,10000000,50000),"whole view row limit cannot yield successful prefix");
        refuses(bytes,saved.pin,new Limits(2000000,100,1,10000000,50000),"step limit cannot yield successful prefix");
        refuses(bytes,saved.pin,new Limits(2000000,100,100,1,50000),"work exhaustion yields no reopened result");
        refuses(bytes,saved.pin,new Limits(2000000,100,100,10000000,1),"node exhaustion yields no reopened result");
    }
    private static void exportBounds()throws Exception {
        for(boolean generated:new boolean[]{false,true}) {
            Mind m=memory(false);Rule p=compile(m,"!@a $u @b rel(a,u,b);"),n=compile(m,"!@a @u ~(rel(a,u,1)&&rel(a,u,2));"),cache=compile(m,"!cache(1);");
            if(generated)cache.setGenerated(true);else cache.setDeleted(true,m);
            try(NativeDependentMatchFrame f=frame(m,Collections.emptyList())) {
                Match a=f.match(f.entry(p),f.instantiate(f.entry(n),0),0);boolean no=false;try{NativeDependentReplayCapsule.capture(f,a,LIMITS);}catch(IOException expected){no=true;}require(no,"caches/deleted rows never silently omitted from persistent view");
            }
        }
        Mind m=memory(false);Rule p=compile(m,"!@a $u @b rel(a,u,b);"),n=compile(m,"!@a @u ~(rel(a,u,1)&&rel(a,u,2));");
        try(NativeDependentMatchFrame f=frame(m,Collections.singletonList("opaque config"))) {
            Match a=f.match(f.entry(p),f.instantiate(f.entry(n),0),0);boolean no=false;try{NativeDependentReplayCapsule.capture(f,a,LIMITS);}catch(IOException expected){no=true;}require(no,"opaque initialization is not executed or ignored");
        }
        try(NativeDependentMatchFrame f=frame(m,Collections.emptyList())) {
            Match a=f.match(f.entry(p),f.instantiate(f.entry(n),0),0);Saved partial=NativeDependentReplayCapsule.capture(f,a,LIMITS);
            try(Reopened result=NativeDependentReplayCapsule.reopen(partial.bytes(),partial.pin,LIMITS)){require(result.materialization.remaining.size()==1,"partial branch stays partial after reopen");result.verify();}
            boolean no=false;try{NativeDependentReplayCapsule.capture(f,a,new Limits(1,100,100,10000000,50000));}catch(IllegalStateException expected){no=true;}require(no,"output byte cap refuses entire packet");
        }
    }
    private static String worker(String... args)throws Exception {
        List<String> command=new ArrayList<>(Arrays.asList(Paths.get(System.getProperty("java.home"),"bin","java").toString(),"-XX:-UsePerfData","-cp",System.getProperty("java.class.path"),NativeDependentReplayCapsuleRunner.class.getName()));command.addAll(Arrays.asList(args));
        Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("Dependent capsule worker timeout");}
        ByteArrayOutputStream log=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=process.getInputStream().read(b))!=-1)log.write(b,0,n);
        String text=new String(log.toByteArray(),StandardCharsets.UTF_8);if(process.exitValue()!=0)throw new AssertionError(text);return text;
    }
    private static void writer(Path packet,Path pin)throws Exception {
        Mind m=memory(true);Rule p=compile(m,"!@a $u @b rel(a,u,b);"),q=compile(m,"!q(2);"),n=compile(m,"!@a @u ~(rel(a,u,1)&&q(a));");compile(m,"!unrelated(99);");
        try(NativeDependentMatchFrame f=frame(m,Collections.emptyList())) {
            Match a=f.match(f.entry(p),f.instantiate(f.entry(n),0),0),b=f.match(f.entry(q),a.after,0);Saved saved=NativeDependentReplayCapsule.capture(f,b,LIMITS);
            Files.write(packet,saved.bytes());Files.write(pin,saved.pin.hex().getBytes(StandardCharsets.US_ASCII));
        }
    }
    private static void writerExit()throws Exception {
        Path packet=Files.createTempFile("dependent-writer-exit-",".bin"),pin=Files.createTempFile("dependent-writer-exit-",".sha256");
        require(worker("--writer",packet.toString(),pin.toString()).contains("DEPENDENT_CAPSULE_WRITER_EXIT"),"writer JVM exits after writing bytes and separate pin");
        // No decoding in this process: only the exited writer created the data.
        String expected=new String(Files.readAllBytes(pin),StandardCharsets.US_ASCII);
        require(worker("--child",packet.toString(),expected,"true").contains("DEPENDENT_CAPSULE_CHILD_OK"),"reader JVM starts after writer exit and independently verifies the packet");
        Files.delete(packet);Files.delete(pin);
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("dependent-capsule-home-").toString());
        if(args.length!=0&&args[0].equals("--writer")){writer(Paths.get(args[1]),Paths.get(args[2]));System.out.println("DEPENDENT_CAPSULE_WRITER_EXIT");return;}
        if(args.length!=0) {
            if(!args[0].equals("--child")||Files.size(Paths.get(args[1]))>LIMITS.bytes)throw new AssertionError("Invalid child capsule");
            try(Reopened result=NativeDependentReplayCapsule.reopen(Files.readAllBytes(Paths.get(args[1])),Pin.fromHex(args[2]),LIMITS)){if(args[3].startsWith("wide:"))validateWide(result,Integer.parseInt(args[3].substring(5)));else validate(result,Boolean.parseBoolean(args[3]));}
            System.out.println("DEPENDENT_CAPSULE_CHILD_OK checks="+checks);return;
        }
        Saved raw=null;for(boolean reverse:new boolean[]{false,true}){raw=example("2",reverse,false);for(String value:new String[]{"2","\"2\""})example(value,reverse,true);}
        corruption(raw);exportBounds();wide();alternativesAndClauses();prefixScopes();writerExit();System.out.println("NATIVE_DEPENDENT_REPLAY_CAPSULE_PASS checks="+checks);
    }
}
