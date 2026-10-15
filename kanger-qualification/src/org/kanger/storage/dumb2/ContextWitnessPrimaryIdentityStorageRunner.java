/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.units.*;

/** Durable primary witness identity and rejected-write rollback after reopening DUMB2. */
public final class ContextWitnessPrimaryIdentityStorageRunner {
    private static int checks;
    private static void require(boolean value,String label) {
        ++checks; if (!value) throw new AssertionError(label);
    }
    private static Set<Long> roots(Mind mind,String predicate) throws Exception {
        Set<Long> ids = new HashSet<>();
        for (Object value : mind.getRules()) {
            Rule rule = (Rule)value;
            if (!rule.isStored() || rule.isDeleted(mind)) continue;
            Domain d = rule.getDomain();
            if (d.isAntc() && predicate.equals(d.getPredicate(mind).getName(mind)))
                ids.add(d.get(0).getValue(mind).getId());
        }
        return ids;
    }
    public static void main(String[] args) {
        try {
            Path home = Files.createTempDirectory("witness-primary-storage-");
            System.setProperty("user.home",home.toString());
            User user = new User();
            user.setUserDir(Files.createDirectories(home.resolve("user")).toString()+File.separator);
            user.setDatabaseDir(Files.createDirectories(home.resolve("database")).toString()+File.separator);
            DB db = new DB(); db.init(user); Mind mind = new Mind(user); user.setCurrentMind(mind);
            mind = (Mind)mind.useStorage("Witness"); user.setCurrentMind(mind);
            require(mind.compile("!$x p(x,1); !@x p(x,1)->q(x,1); !@x p(x,1)->r(x,1); !@x q(x,1)&&r(x,1)->out(x,1);",null,false),
                    "persistent fork accepted");
            Set<Long> origin = roots(mind,"p");
            require(origin.size() == 1 && origin.equals(roots(mind,"out")),"fork result keeps primary witness before close");
            user.setCurrentMind(mind.closeStorage());
            mind = (Mind)user.getCurrentMind().useStorage("Witness"); user.setCurrentMind(mind);
            require(origin.equals(roots(mind,"p")) && origin.equals(roots(mind,"out")),"reopen preserves primary and generated witness IDs");
            long revision = db.getRevision(); int count = mind.getRules().size();
            require(Boolean.TRUE.equals(mind.query("?",null,false)),"reopened closure is consistent");
            require(!mind.compile("!@x ~out(x,1);",null,false),"reopened fork rejects universal denial");
            require(db.getRevision() == revision && count == mind.getRules().size(),"rejected write preserves revision and closure");
            require(origin.equals(roots(mind,"out")),"rollback preserves the durable result witness");
            user.setCurrentMind(mind.closeStorage());
            mind = (Mind)user.getCurrentMind().useStorage("Witness"); user.setCurrentMind(mind);
            require(origin.equals(roots(mind,"out")) && Boolean.TRUE.equals(mind.query("?",null,false)),
                    "second reopen preserves the accepted consistent revision");
            user.setCurrentMind(mind.closeStorage());
            System.out.println("CONTEXT_WITNESS_PRIMARY_IDENTITY_STORAGE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
