/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.kanger.Mind;
import org.kanger.User;

/** Reopen a saved trust group and verify inference across its two pinned members. */
public final class ContextTrustRecoveryWitness {
    private static void require(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
    private static void close(Mind mind) throws Exception {
        ((User) mind.getUser()).setCurrentMind(mind.closeStorage());
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("A fresh data directory is required");
        Path data = Files.createDirectory(Paths.get(args[0]).toAbsolutePath());
        Mind rules = ContextPrivatePublicationWitness.open(new DB(), data, "Rules");
        require(rules.compile("!@x p(x) -> q(x);", null, false), "Rules original");
        close(rules);
        Mind facts = ContextPrivatePublicationWitness.open(new DB(), data, "Facts");
        require(facts.compile("!p(John);", null, false), "Facts original");
        close(facts);
        DB source = new DB();
        Mind mind = ContextPrivatePublicationWitness.open(source, data, "Source");
        require(mind.compile("!anchor(1);", null, false), "Source original");
        source.connectContext("Rules", "own");
        source.connectContext("Facts", "own");
        require(source.saveConnections(mind) == 2, "Trust vector publication");
        close(mind);
        verify(data);
        System.out.println("TRUST_REOPEN_WITNESS_OK");
    }
    static void verify(Path data) throws Exception {
        for (String name : new String[] {"Rules", "Facts"}) {
            Mind member = ContextPrivatePublicationWitness.open(new DB(), data, name + "@1");
            try {
                require(member.query("?q(John);", null, false) == null,
                    "Member alone derived the joint answer: " + name);
            } finally {
                close(member);
            }
        }
        DB reopened = new DB();
        Mind view = ContextPrivatePublicationWitness.open(reopened, data, "Source@2");
        require(reopened.federationSnapshot().getConnections().size() == 2, "Two pinned members required");
        require(reopened.federationSnapshot().getConnections().stream()
            .allMatch(pin -> "own".equals(pin.getTrustGroup()) && pin.getPinnedRevision() == 1), "Group name or exact revision changed");
        require(Boolean.TRUE.equals(view.query("?q(John);", null, false)), "Joint inference missing");
        close(view);
    }
}
