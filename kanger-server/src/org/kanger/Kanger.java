/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger;

import org.kanger.admin.AdminServer;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicBoolean;

public class Kanger {

    private static Process serviceDescriptor = null;
    private static boolean serviceTerminate = false;
    private static HttpServer httpServer = null;
    private static AdminServer adminServer = null;
    private static final AtomicBoolean serverShutdown = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        boolean wrapper = false;


        for (String a : args) {
            if ("--wrapper".equals(a) || "-W".equals(a)) {
                wrapper = true;
            }
        }

        if (wrapper) {
            registerShutdownHook();
            Kanger.start();
            System.exit(0);
        } else {
            serverShutdown.set(false);
            registerServerShutdownHook();
            try {
                Settings.setActive(true);
                Timer timer = new Timer(true);
                timer.scheduleAtFixedRate(new Watchdog(), 0, Long.parseLong(Settings.getProperty("server.watchdog.period", 1000 + "")));

                if (AdminServer.isEnabled()) {
                    Watchdog.log("Admin Server starting...");
                    adminServer = AdminServer.fromSettings();
                    adminServer.start();
                }

                Watchdog.log("HTTP Server starting...");
                httpServer = new HttpServer();
                httpServer.start(new SessionSerializingReactor(
                        new MailBoundaryReactor(
                                new AuthenticatedRuntimeBootstrapReactor(
                                        new CanonicalCommandIngressReactor(
                                                new WorkspaceStateReactor(
                                                        new CanonicalCommandRuntimeReactor(
                                                                new ExplicitStorageLifecycleReactor(
                                                                        new DestructiveStopLossReactor(
                                                                                new MindLifecycleReactor(
                                                                                        new QueryProcessor()))))))))));
            } finally {
                shutdownServer();
                System.out.println("FORCE REBOOT Server");
            }
        }
    }

    public static void start() {
        String cd = getModuleWorkingDir();
        System.setProperty("user.dir", cd);
        System.out.println("BUILT-IN-WRAPPER: Used working dir " + cd);
        serviceTerminate = false;

        try {
            do {
                String options = "-jar " + String.join(" ", Settings.getByPrefix("server.wrapper.option."));
                String cmd[] = new String[]{"java",
                        options.trim(),
                        cd + "/kanger-server.jar"};

                System.out.println("BUILT-IN-WRAPPER: Executing: " + String.join(" ", cmd));
                boolean reboot = launch(cmd);

                if (!reboot) {
                    System.out.println("BUILT-IN-WRAPPER: Stop server");
                    serviceTerminate = true;
                }
                if (!serviceTerminate) {
                    System.out.println("BUILT-IN-WRAPPER: Restart server");
                } else {
                    System.out.println("BUILT-IN-WRAPPER: Shutdown server");
                }
            } while (!serviceTerminate);
        } catch (InterruptedException e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
        } catch (IOException e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
        }
    }

    public static void stop() {
        try {

            Settings.setActive(false);

            String cd = getModuleWorkingDir();
            System.setProperty("user.dir", cd);
            System.out.println("BUILT-IN-WRAPPER: Used working dir " + cd);
            System.out.println("BUILT-IN-WRAPPER: Stop server");
            serviceTerminate = true;

            if (serviceDescriptor != null) {
                System.out.println("BUILT-IN-WRAPPER: Waiting for process shutdown...");
                serviceDescriptor.waitFor();
                System.out.println("BUILT-IN-WRAPPER: Process done");
            }

        } catch (InterruptedException e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
        }
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
                new Thread() {
                    @Override
                    public void run() {
                        Kanger.stop();
                    }
                }
        );
    }

    private static void registerServerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
                new Thread("kanger-server-shutdown") {
                    @Override
                    public void run() {
                        shutdownServer();
                    }
                }
        );
    }

    private static void shutdownServer() {
        if (!serverShutdown.compareAndSet(false, true)) {
            return;
        }

        try {
            HttpServer current = httpServer;
            httpServer = null;
            if (current != null) {
                current.stop();
            }
        } catch (Exception error) {
            System.err.println(new Date());
            error.printStackTrace(System.err);
        }

        try {
            AdminServer current = adminServer;
            adminServer = null;
            if (current != null) {
                current.stop();
            }
        } catch (Exception error) {
            System.err.println(new Date());
            error.printStackTrace(System.err);
        }

        try {
            MailTransport.shutdownRuntime();
        } catch (Exception error) {
            System.err.println(new Date());
            error.printStackTrace(System.err);
        }

        try {
            UserFactory.shutdown();
        } catch (Exception error) {
            System.err.println(new Date());
            error.printStackTrace(System.err);
        }

        try {
            Settings.setActive(false);
        } catch (Exception error) {
            System.err.println(new Date());
            error.printStackTrace(System.err);
        }
    }

    public static boolean launch(String[] cmdarray) throws IOException, InterruptedException, IOException {
        final boolean[] reboot = {false};
        byte[] buffer = new byte[256];
        ProcessBuilder processBuilder = new ProcessBuilder(cmdarray);
        serviceDescriptor = processBuilder.start();
        InputStream in = serviceDescriptor.getInputStream();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    StringBuffer history = new StringBuffer(4096);
                    while (true) {
                        int r = 0;
                        r = in.read(buffer);
                        if (r <= 0) {
                            break;
                        }
                        history.append(new String(buffer));
                        reboot[0] = history.indexOf("FORCE REBOOT Server") != -1;
                        if (history.length() > 4096) {
                            history.delete(0, history.length() - 4096);
                        }
                        System.out.write(buffer, 0, r);
                    }
                } catch (IOException e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                } finally {
                    try {
                        in.close();
                    } catch (IOException e) {
                        System.err.println(new Date());
                        e.printStackTrace(System.err);
                    }
                }
            }
        }).start();
        InputStream er = serviceDescriptor.getErrorStream();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    StringBuffer history = new StringBuffer(4096);
                    while (true) {
                        int r = 0;
                        r = er.read(buffer);
                        if (r <= 0) {
                            break;
                        }
                        history.append(new String(buffer));
                        if (history.length() > 4096) {
                            history.delete(0, history.length() - 4096);
                        }
                        System.err.write(buffer, 0, r);
                    }
                } catch (IOException e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                } finally {
                    try {
                        er.close();
                    } catch (IOException e) {
                        System.err.println(new Date());
                        e.printStackTrace(System.err);
                    }
                }
            }
        }).start();
        serviceDescriptor.waitFor();
        return reboot[0];
    }

    public static String getModuleWorkingDir() {
        URL location = Kanger.class.getProtectionDomain().getCodeSource().getLocation();
        try {
            String sub = location.getFile().substring(2, 3).equals(":") && location.getFile().substring(0, 1).equals("/") ? location.getFile().substring(1) : location.getFile();
            String classLocation = URLDecoder.decode(sub.replace('/', File.separatorChar), "utf-8" /*Charset.defaultCharset()*/);
            int pos = classLocation.indexOf(".jar");
            if (pos != -1) {
                return classLocation.substring(0, classLocation.lastIndexOf(File.separatorChar));
            }
        } catch (UnsupportedEncodingException e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
        }
        return new File("").getAbsolutePath();
    }

    /**
     * Historical compatibility facade for outbound HTTP calls.
     * HTTPS uses the JVM platform trust store and default hostname verifier.
     */
    public static String httpRequest(String url, String post, String enc, int timeout, Map<String, String> headers) throws IOException, KeyManagementException, NoSuchAlgorithmException {
        return OutboundHttpClient.request(url, post, enc, timeout, headers);
    }

    public static HttpServer getHttpServer() {
        return httpServer;
    }
}
