package org.kanger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public final class ThreadedPublicationCaptureRunner {
    public static void main(String[] args)throws Exception{
        List<String> cases=new ArrayList<>(),events=new ArrayList<>();
        String[] forced={"123","132","213","231","312","321"};
        for(int n=0;n<18;n++){
            PublicationFixture fixture=new PublicationFixture();String label=(n<6?"forced-":"race-")+n;
            int[] requested=n<6?PublicationFixture.parseOrder(forced[n]):null;
            CommitOrderJournal.Session session=CommitOrderJournal.register(fixture.parent,fixture.children);
            CountDownLatch ready=new CountDownLatch(3),start=new CountDownLatch(1),done=new CountDownLatch(3);
            Semaphore[] turns={new Semaphore(0),new Semaphore(0),new Semaphore(0)};
            if(requested!=null)turns[requested[0]-1].release();
            boolean[] accepted=new boolean[3];AtomicReference<Throwable> error=new AtomicReference<>();
            List<Thread> threads=new ArrayList<>();
            for(int actor=0;actor<3;actor++){final int id=actor;Thread t=new Thread(()->{
                try{ready.countDown();if(!start.await(30,TimeUnit.SECONDS))throw new AssertionError("start timeout");if(requested!=null&&!turns[id].tryAcquire(30,TimeUnit.SECONDS))throw new AssertionError("turn timeout");accepted[id]=fixture.parent.commit(fixture.children[id]);}
                catch(Throwable failure){error.compareAndSet(null,failure);}
                finally{if(requested!=null)for(int i=0;i<2;i++)if(requested[i]==id+1)turns[requested[i+1]-1].release();done.countDown();}
            },"native-publication-"+(actor+1));t.setDaemon(true);threads.add(t);}
            for(int i=0;i<3;i++)threads.get((i+n)%3).start();
            PublicationFixture.require(ready.await(30,TimeUnit.SECONDS),"all workers ready");start.countDown();
            PublicationFixture.require(done.await(30,TimeUnit.SECONDS),"all native commits completed");
            if(error.get()!=null)throw new AssertionError("worker failure",error.get());
            events.addAll(CommitOrderJournal.finish(session,label));int[] actual=CommitOrderJournal.order(session);
            PublicationFixture.require(actual.length==3&&Arrays.equals(accepted,CommitOrderJournal.outcomes(session)),"monitor outcomes agree with caller results");
            if(requested!=null)PublicationFixture.require(Arrays.equals(actual,requested),"forced monitor order preserved");
            String result=fixture.result(accepted);String row=label+"\t"+PublicationFixture.order(actual)+"\t"+PublicationFixture.digest(fixture.inputs)+"\t"+PublicationFixture.encode(fixture.inputs)+"\t"+PublicationFixture.encode(result);
            cases.add(row);System.out.println("CAPTURE_CASE label="+label+" order="+PublicationFixture.order(actual)+" result="+PublicationFixture.digest(result));
        }
        PublicationFixture.require(CommitOrderJournal.activeRegistrations()==0,"recorder scopes closed");
        Files.write(Paths.get(args[0]),cases,StandardCharsets.UTF_8);Files.write(Paths.get(args[1]),events,StandardCharsets.UTF_8);
        System.out.println("THREADED_PUBLICATION_CAPTURE_OK cases=18 workers=54 entries=54 exits=54 registrations=0");
    }
}
