package org.kanger;

import org.kanger.enums.Enums;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.factory.*;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IData;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class User implements IUser {

    private long id = -1L;
    private final Object locker = new Object();
    Properties userSettings = new Properties();
    private IData data = null;
    private Class udf = null;
    private Map<String, IBase> storage = new HashMap<>();
    private Map<String, Long> counters = new HashMap<>();
    private long lastId = 0L;

    public User(String login, String password, String rootDir) throws Exception {
//        if (mind == null) {
//            this.mind = new Mind(this);
//        } else {
//            this.mind = mind;
//        }

        String token = token(login, password);
        userSettings.put("user.home", getHome());

        String confName = getDir("users.conf");
        if (!new File(confName).exists()) {
            confName = getDir(rootDir) + Enums.FILE_SEPARATOR + "users.conf";
        }
        if (new File(confName).exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(confName))) {
                String sCurrentLine;
                while ((sCurrentLine = br.readLine()) != null) {
                    if (sCurrentLine.split("\\=").length == 2 && token.toLowerCase().equals(sCurrentLine.split("\\=")[0].toLowerCase())) {
                        id = Long.parseLong(sCurrentLine.split("\\=")[1]);
                        break;
                    }
                }
            }
        }

        if (id == -1L) {
            throw new AuthenticationErrorException(login);
        }

        userSettings.put("user.dir", getDir(rootDir + Enums.FILE_SEPARATOR + id + Enums.FILE_SEPARATOR));
        Files.createDirectories(Paths.get(userSettings.getProperty("user.dir")));

        confName = userSettings.getProperty("user.dir") + "kanger.conf";
        if (new File(confName).exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(confName))) {
                userSettings.load(br);
//                String sCurrentLine;
//                while ((sCurrentLine = br.readLine()) != null) {
//                    if (sCurrentLine.split("\\=").length == 2) {
//                        userSettings.setProperty(sCurrentLine.split("\\=")[0], sCurrentLine.split("\\=")[1]);
//                    }
//                }
            }
        }

        if (!userSettings.containsKey("sources.dir")) {
            String sourcesDir = userSettings.getProperty("user.dir") + "SRC" + Enums.FILE_SEPARATOR;
            userSettings.put("sources.dir", sourcesDir);
            Files.createDirectories(Paths.get(userSettings.getProperty("sources.dir")));
        }

        if (!userSettings.containsKey("database.dir")) {
            String sourcesDir = userSettings.getProperty("user.dir") + "DB" + Enums.FILE_SEPARATOR;
            userSettings.put("database.dir", sourcesDir);
            Files.createDirectories(Paths.get(userSettings.getProperty("database.dir")));
        }
    }

    public static String getHome() {
        String home = System.getProperty("user.home");
        if (home.isEmpty()) {
            home = new File("").getAbsolutePath();
            if (home.isEmpty() || home.equals(Enums.FILE_SEPARATOR)) {
                String tmp = "/storage/emulated/0";
                if (Files.exists(Paths.get(tmp))) {
                    return tmp;
                } else {
                    return home;
                }
            }
        }
        return home;
    }

    public static String getDir(String subDir) {
        String home = getHome();
        if (!home.isEmpty()) {
            home += Enums.FILE_SEPARATOR;
        }
        return home + subDir;
    }

    public static IUser createUser(String login, String password, String rootDir) throws Exception {
        String token = token(login, password);
        String confName = getDir("users.conf");
        if (!new File(confName).exists()) {
            Files.createDirectories(Paths.get(getDir(rootDir)));
            confName = getDir(rootDir) + Enums.FILE_SEPARATOR + "users.conf";
        }
        long id = 0;
        if (new File(confName).exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(confName))) {
                String sCurrentLine;
                while ((sCurrentLine = br.readLine()) != null) {
                    if (sCurrentLine.split("\\=").length == 2 && token.toLowerCase().equals(sCurrentLine.split("\\=")[0].toLowerCase())) {
                        throw new AuthenticationErrorException("User already exists");
                    }
                    long idx = Long.parseLong(sCurrentLine.split("\\=")[1]);
                    if (idx > id) {
                        id = idx;
                    }
                }
            }
        } else {
            new File(confName).createNewFile();
        }
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(confName, true))) {
            bw.write(token + "=" + (++id));
            bw.newLine();
        }

        return new User(login, password, rootDir);
    }

    public IBase getStorage(String schema) {
        return storage.get(schema);
    }

    public IBase connect(String schema) throws Exception {
        if (data != null && !data.isClosed()) {
            return data.connect(schema);
        } else {
            return null;
        }
    }

    public void clear(Mind mind) throws Exception {
        if (data != null && !data.isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                e.getValue().clear();
            }
            data.flush();
        }

        for (Mind m = mind; m != null; m = m.getNext()) {
            m.clear();
            mind = m;
        }
    }

    public void remove() throws Exception {
        if (data != null && !data.isClosed()) {
            data.remove();
//            for (Map.Entry<String, IBase> e : storage.entrySet()) {
//                //TODO: Удаление БД
//            }
        }
    }

    public void reindex(IReactor IReactor) throws IOException, RuntimeErrorException {
        if (data != null && !data.isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                try {
                    IReactor.run(e.getKey());
                } catch (Exception ex) {
                }
                //TODO: Переиндексация БД
            }
//            use(data.getStorageName());
        }
    }

    @Override
    public long getUsedCacheSize() {
        long sz = 0;
        for (Map.Entry<String, IBase> e : storage.entrySet()) {
            sz += e.getValue().getUsedCacheSize();
        }
        return sz;
    }

    @Override
    public long getMaxCacheSize() {
        long sz = 0;
        for (Map.Entry<String, IBase> e : storage.entrySet()) {
            sz += e.getValue().getMaxCacheSize();
        }
        return sz;
    }

    @Override
    public void clearCache() {
        for (Map.Entry<String, IBase> e : storage.entrySet()) {
            e.getValue().clearCache();
        }
    }

    @Override
    public long lastId(String schema) {
        if (isClosed()) {
            synchronized (this) {
                if (!counters.containsKey(schema)) {
                    counters.put(schema, 0L);
                }
                return counters.get(schema);
            }
        } else {
            return storage.get(schema).lastId();
        }
    }

    @Override
    public long nextId(String schema) {
        if (isClosed()) {
            synchronized (this) {
                if (!counters.containsKey(schema)) {
                    counters.put(schema, 0L);
                }
                long id = counters.get(schema);
                counters.put(schema, id + 1);
                return id;
            }
        } else {
            return storage.get(schema).nextId();
        }
    }

    @Override
    public void clearCounters(String schema) {
        counters.put(schema, 0L);
    }

    @Override
    public long lastId() {
        return lastId;
    }

    @Override
    public long nextId() {
        return lastId++;
    }

    @Override
    public void close() throws Exception {
        if (data != null && !data.isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                e.getValue().clearCache();
            }
            data.close();
        }
    }


    public boolean isClosed() {
        return data == null || data.isClosed();
    }

    public String getStorageName() {
        return data == null ? "" : data.getStorageName();
    }

    @Override
    public Collection<String> getStoragesList() {
        if (data != null) {
            return data.list();
        } else {
            return new ArrayList<>();
        }
    }

    @Override
    public void flush() throws Exception {
        if (data != null) {
            data.flush();
        }
    }

    @Override
    public Object getLocker() {
        return locker;
    }

    public Mind use(Mind mind, String name) throws Exception {

        if (data != null) {

            if (mind == null) {
                mind = new Mind(this);
            }
            data.use(name);

            storage.put(DictionaryFactory.SCHEMA, data.getBase(DictionaryFactory.SCHEMA));
            storage.put(DomainFactory.SCHEMA, data.getBase(DomainFactory.SCHEMA));
            storage.put(FunctionFactory.SCHEMA, data.getBase(FunctionFactory.SCHEMA));
            storage.put(PredicateFactory.SCHEMA, data.getBase(PredicateFactory.SCHEMA));
            storage.put(RuleFactory.SCHEMA, data.getBase(RuleFactory.SCHEMA));
            storage.put(TVariableFactory.SCHEMA, data.getBase(TVariableFactory.SCHEMA));
            storage.put(LibraryFactory.SCHEMA, data.getBase(LibraryFactory.SCHEMA));

            storage.put(TValueFactory.SCHEMA, data.getBase(TValueFactory.SCHEMA));
            storage.put(FValueFactory.SCHEMA, data.getBase(FValueFactory.SCHEMA));

            storage.put(CommentFactory.SCHEMA, data.getBase(CommentFactory.SCHEMA));

            while (mind.getNext() != null) {
                mind = mind.getNext();
            }

            mind.getTerms().transaction(null);
            mind.getDomains().transaction(null);
            mind.getFunctions().transaction(null);
            mind.getFValues().transaction(null);
            mind.getPredicates().transaction(null);
            mind.getRules().transaction(null);
            mind.getComments().transaction(null);
            mind.getTValues().transaction(null);
            mind.getTVars().transaction(null);
            mind.getLibrary().transaction(null);

//            Mind m = new Mind(mind);
//            m.link(null, true);
//            Boolean ar = m.analise(null, true);
//
//            if (ar) {
//                m.getLog().add(LogMode.ANALIZER, "ERROR: Collisions in Program");
//                mind.release(m);
//                close();
//                throw new RuntimeErrorException("Collisions in Program");
//            } else {
//                m.getLog().add(LogMode.ANALIZER, "SUCCESS: No Collisions in Program");
//                mind.commit(m);
//            }

            return mind;

        } else {
            throw new RuntimeErrorException("DB module doesn't loaded");
        }
    }

    @Override
    public boolean containsKey(String s) {
        return userSettings.containsKey(s);
    }

    @Override
    public IData getData() throws RuntimeErrorException {
        if (data != null) {
            return data;
        } else {
            throw new RuntimeErrorException("DB module doesn't loaded");
        }
    }

    @Override
    public void setData(IData db) {
        data = db;
    }

    @Override
    public String getProperty(String key, String val) {
        if (userSettings.containsKey(key)) {
            return userSettings.getProperty(key);
        } else {
            userSettings.setProperty(key, val);
            String confName = userSettings.getProperty("user.dir") + "kanger.conf";
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(confName))) {
                userSettings.store(bw, new Date().toString());
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }
        return val;
    }

    @Override
    public String getUserDir() {
        return userSettings.getProperty("user.dir");
    }

    @Override
    public String getDatabaseDir() {
        return userSettings.getProperty("database.dir");
    }

    private static String token(String login, String password) {
        return String.format("%04x%04x", login.hashCode(), password.hashCode());
    }

    @Override
    public String getSourceDir() {
        return userSettings.getProperty("sources.dir");
    }
}
