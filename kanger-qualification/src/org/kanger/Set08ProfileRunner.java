package org.kanger;
import java.nio.file.Files;
import org.kanger.test.KangerTest;
import org.kanger.udf.UDF;
/** Repeated original four-thread test; main-thread allocation is intentionally not measured. */
public final class Set08ProfileRunner {
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("set08-profile-").toString());
        int samples=Integer.getInteger("bench.samples",8);
        for(int i=0;i<samples;i++) {
            User user=new User(); new UDF().init(user);
            if(!KangerTest.test(new Mind(user),"set_08_02")) throw new AssertionError("sample "+i);
            System.out.println("SET08_SAMPLE_OK "+i);
        }
        System.out.println("SET08_PROFILE_OK samples="+samples);
    }
}
