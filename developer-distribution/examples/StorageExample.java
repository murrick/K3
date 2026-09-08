import org.kanger.Mind;
import org.kanger.User;
import org.kanger.bootstrap.RuntimeBootstrap;
import org.kanger.bootstrap.RuntimeCapability;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** Canonical KANGER 3.7.0 IUser storage lifecycle example. */
public final class StorageExample {

    private StorageExample() {
    }

    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("kanger-sdk-storage-");
        IUser user = new User();
        IMind mind = null;
        Throwable failure = null;
        try {
            Path sources = home.resolve("SRC");
            Path databases = home.resolve("DB");
            Files.createDirectories(sources);
            Files.createDirectories(databases);

            user.setUserDir(directory(home));
            user.setSourceDir(directory(sources));
            user.setDatabaseDir(directory(databases));

            RuntimeBootstrap.ensureCapabilities(user, RuntimeCapability.STORAGE);
            mind = new Mind(user);

            mind = user.use(mind, "example");
            require(mind.compile("!color(apple, Red);"),
                    "Unable to compile persistent fact");
            mind = user.checkpoint(mind);
            mind = user.close(mind);

            mind = user.use(mind, "example");
            Boolean result = mind.query("?color(apple, Red);");
            require(Boolean.TRUE.equals(result),
                    "Persistent fact did not survive reopen: " + result);
            mind = user.close(mind);

            System.out.println("STORAGE_PASS");
        } catch (Throwable error) {
            failure = error;
            throw error;
        } finally {
            if (mind != null) {
                try {
                    user.close(mind);
                } catch (Throwable closeError) {
                    if (failure != null && closeError != failure) {
                        failure.addSuppressed(closeError);
                    } else if (failure == null) {
                        rethrow(closeError);
                    }
                }
            }
            deleteTree(home.toFile());
        }
    }

    private static String directory(Path path) {
        String value = path.toAbsolutePath().toString();
        return value.endsWith(File.separator) ? value : value + File.separator;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteTree(child);
                }
            }
        }
        if (!file.delete() && file.exists()) {
            throw new IllegalStateException("Unable to delete temporary path " + file);
        }
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new RuntimeException(failure);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
