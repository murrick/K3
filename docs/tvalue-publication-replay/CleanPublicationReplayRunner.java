package org.kanger;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
/** Replays captured monitor order using uninstrumented native Mind classes. */
public final class CleanPublicationReplayRunner {
    public static void main(String[] args)throws Exception{
        List<String> rows=Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8);PublicationFixture.require(rows.size()==18,"complete captured batch");
        for(String row:rows){String[] f=row.split("\t",-1);PublicationFixture.require(f.length==5,"complete immutable capture row");PublicationFixture fixture=new PublicationFixture();
            PublicationFixture.require(fixture.inputs.equals(PublicationFixture.decode(f[3]))&&PublicationFixture.digest(fixture.inputs).equals(f[2]),"identical native prepared input");
            int[] order=PublicationFixture.parseOrder(f[1]);boolean[] accepted=new boolean[3];for(int actor:order)accepted[actor-1]=fixture.parent.commit(fixture.children[actor-1]);
            String actual=fixture.result(accepted);PublicationFixture.require(actual.equals(PublicationFixture.decode(f[4])),"complete native replay outcome "+f[0]+" order="+f[1]+" actual="+actual+" expected="+PublicationFixture.decode(f[4]));
            System.out.println("REPLAY_CASE label="+f[0]+" order="+f[1]+" result="+PublicationFixture.digest(actual));
        }
        PublicationFixture.require(CommitOrderJournal.activeRegistrations()==0,"clean replay has no recorder scopes");
        System.out.println("CLEAN_PUBLICATION_REPLAY_OK cases=18 full_inputs=true full_results=true reservations=0");
    }
}
