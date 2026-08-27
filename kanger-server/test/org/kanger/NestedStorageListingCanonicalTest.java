/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.command.CommandParser;
import org.kanger.enums.Enums;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for canonical nested DUMB storage listing. */
class NestedStorageListingCanonicalTest {

    @Test
    void nestedStorageListingUsesOneLogicalSeparatorPerDirectoryLevel()
            throws Exception {
        String identity = "nested-storage-listing-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);

        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            String logical = "nested.storage." + UUID.randomUUID();
            String physical = logical.replace(".", Enums.FILE_SEPARATOR);

            CanonicalCommandProcessor.Result opened = processor.execute(
                    parser.parse("storage use " + logical), user);

            assertTrue(opened.isSuccess());
            assertEquals(physical, opened.getStorageStatus().getCurrent());
            assertTrue(opened.getStorageStatus().getNames().contains(logical),
                    opened.getStorageStatus().getNames().toString());

            CanonicalCommandProcessor.Result status = processor.execute(
                    parser.parse("storage"), user);
            assertEquals(physical, status.getStorageStatus().getCurrent());
            assertTrue(status.getStorageStatus().getNames().contains(logical),
                    status.getStorageStatus().getNames().toString());
        } finally {
            UserFactory.dropUser(user);
        }
    }
}
