package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.bootstrap.RuntimeBootstrap;
import org.kanger.bootstrap.RuntimeBootstrapResult;
import org.kanger.bootstrap.RuntimeCapability;
import org.kanger.bootstrap.RuntimeModule;
import org.kanger.exception.RuntimeErrorException;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Qualifies classpath absence as a supported runtime state. RuntimeBootstrap
 * must attach capabilities that are present and leave missing ones unattached
 * without forcing Core, Console, or Server to name concrete provider classes.
 */
public class RuntimeBootstrapOptionalCapabilityTest {

    private static final String SERVICE_DESCRIPTOR =
            "META-INF/services/" + RuntimeModule.class.getName();
    private static final String UDF_PROVIDER =
            "org.kanger.udf.UdfRuntimeModule";
    private static final String STORAGE_PROVIDER =
            "org.kanger.storage.DumbRuntimeModule";

    @Test
    public void supportsRuntimeWithoutAnyProviders() throws Exception {
        User user = new User();

        RuntimeBootstrapResult result = RuntimeBootstrap.ensure(
                user, loaderWithProviders());

        assertFalse(result.loaded(RuntimeCapability.STORAGE));
        assertFalse(result.loaded(RuntimeCapability.UDF));
        assertThrows(RuntimeErrorException.class, user::getData);
        assertThrows(RuntimeErrorException.class, user::getUdf);
    }

    @Test
    public void attachesOnlyUdfWhenStorageProviderIsAbsent() throws Exception {
        User user = new User();

        RuntimeBootstrapResult result = RuntimeBootstrap.ensure(
                user, loaderWithProviders(UDF_PROVIDER));

        assertFalse(result.loaded(RuntimeCapability.STORAGE));
        assertTrue(result.loaded(RuntimeCapability.UDF));
        assertThrows(RuntimeErrorException.class, user::getData);
        assertNotNull(user.getUdf());
    }

    @Test
    public void attachesOnlyStorageWhenUdfProviderIsAbsent() throws Exception {
        User user = new User();

        RuntimeBootstrapResult result = RuntimeBootstrap.ensure(
                user, loaderWithProviders(STORAGE_PROVIDER));

        assertTrue(result.loaded(RuntimeCapability.STORAGE));
        assertFalse(result.loaded(RuntimeCapability.UDF));
        assertNotNull(user.getData());
        assertThrows(RuntimeErrorException.class, user::getUdf);
    }

    private static ClassLoader loaderWithProviders(String... providers)
            throws IOException {
        Path root = Files.createTempDirectory("kanger-runtime-modules-");
        Path services = Files.createDirectories(root.resolve("META-INF/services"));
        Path descriptor = services.resolve(RuntimeModule.class.getName());
        Files.write(descriptor, Arrays.asList(providers), StandardCharsets.UTF_8);

        root.toFile().deleteOnExit();
        services.toFile().deleteOnExit();
        descriptor.toFile().deleteOnExit();

        final URL descriptorUrl = descriptor.toUri().toURL();
        final ClassLoader parent = RuntimeBootstrapOptionalCapabilityTest.class
                .getClassLoader();

        return new ClassLoader(parent) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (SERVICE_DESCRIPTOR.equals(name)) {
                    return Collections.enumeration(
                            Collections.singletonList(descriptorUrl));
                }
                return super.getResources(name);
            }
        };
    }
}
