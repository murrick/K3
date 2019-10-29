package org.kanger.storage;

import org.cojen.tupl.Database;
import org.cojen.tupl.DatabaseConfig;
import org.cojen.tupl.DurabilityMode;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IData;
import org.kanger.interfaces.IUser;

import java.io.File;
import java.io.IOException;

public class DB implements IData {

    DatabaseConfig config = null;
    private String storageName = "";
    private Database db = null;

    @Override
    public void init() {
        config = new DatabaseConfig()
                .minCacheSize(100_000_000)
                .durabilityMode(DurabilityMode.NO_FLUSH);
    }

    @Override
    public void use(String name) throws IOException {
        if (!isClosed()) {
            close();
        }

        String dbPath = System.getProperty("database.dir");
        if (dbPath == null || dbPath.isEmpty()) {
            dbPath = System.getProperty("user.dir");
        }
        if (!dbPath.endsWith("/") && !dbPath.endsWith("\\")) {
            dbPath += File.separatorChar;
        }
        dbPath += name;

        config.baseFilePath(dbPath);
        db = Database.open(config);

        storageName = name;
    }

    @Override
    public void close() throws IOException {
        if (db != null) {
            db.shutdown();
            db = null;
        }
    }

    @Override
    public void flush() throws IOException {
        if (!isClosed()) {
            db.checkpoint();
        }
    }

    @Override
    public boolean isClosed() {
        return db == null;
    }


    @Override
    public String getStorageName() {
        return storageName;
    }

    @Override
    public IBase counstructBase(IUser user, String context) throws IOException {
        return new Base(db, user, context);
    }

}
