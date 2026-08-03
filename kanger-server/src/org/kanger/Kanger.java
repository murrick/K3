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

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Map;
import java.util.Timer;

public class Kanger {

    private static Process serviceDescriptor = null;
    private static boolean serviceTerminate = false;
    private static HttpServer httpServer = null;

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
            try {
                Settings.setActive(true);
                Timer timer = new Timer(true);
                timer.scheduleAtFixedRate(new Watchdog(), 0, Long.parseLong(Settings.getProperty("server.watchdog.period", 1000 + "")));

                Watchdog.log("HTTP Server starting...");
                httpServer = new HttpServer();
                httpServer.start(new SessionSerializingReactor(new QueryProcessor()));
            } finally {
                try {
                    if (httpServer != null) {
                        httpServer.stop();
                    }
                } catch (Exception ex) {
                }
                UserFactory.shutdown();
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

    public static String httpRequest(String url, String post, String enc, int timeout, Map<String, String> headers) throws IOException, KeyManagementException, NoSuchAlgorithmException {
        BufferedReader reader = null;
        URLConnection cn = null;
        try {

            if (url.toLowerCase().substring(0, 8).equals("https://")) {
                SSLContext ctx = SSLContext.getInstance("SSL");
                ctx.init(new KeyManager[0], new TrustManager[]{new X509TrustManager() {

                    @Override
                    public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                }}, new SecureRandom());
                SSLContext.setDefault(ctx);

                HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
                conn.setHostnameVerifier(new HostnameVerifier() {

                    @Override
                    public boolean verify(String string, SSLSession ssls) {
                        return true;
                    }
                });
                conn.setDoOutput(true);
                conn.setRequestMethod(post == null ? "GET" : "POST");
                cn = conn;
            } else {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod(post == null ? "GET" : "POST");
                cn = conn;
            }
            cn.setUseCaches(false);
            cn.setReadTimeout(timeout);
            cn.setConnectTimeout(timeout);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    cn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            cn.connect();

            if (post != null) {
                OutputStreamWriter wr = new OutputStreamWriter(cn.getOutputStream());
                wr.write(post);
                wr.flush();
            }

            reader = new BufferedReader(new InputStreamReader(cn.getInputStream(), enc));
            StringBuffer b = new StringBuffer();
            int c;
            while ((c = reader.read()) != -1) {
                b.append((char) c);
            }
            String page = b.toString();
            reader.close();
            reader = null;
            return page;

        } catch (NoSuchFieldError e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return "";
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    //
                }
            }
        }
    }

    public static HttpServer getHttpServer() {
        return httpServer;
    }
}
