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

import org.json.JSONObject;
import org.kanger.interfaces.IReactor;

import javax.net.ssl.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HttpServer {

    private volatile boolean active = true;
    private ServerSocket serverSocket = null;

    public void start(IReactor<JSONObject> reactor) throws Exception {
        startMultiThreaded(reactor);
    }

    private ServerSocket getServerSocket(int port, boolean ssl) throws Exception {
        ServerSocket serverSocket = null;
        if (ssl) {
            // Backlog is the maximum number of pending connections on the socket,
            // 0 means that an implementation-specific default is used
            int backlog = 0;

            Path keyStorePath = Paths.get(Settings.getProperty("server.keystore", "./keystore.jks"));
            char[] keyStorePassword = Settings.getProperty("server.keystore.password", "password").toCharArray();

            // Bind the socket to the given port and address
            serverSocket = getSslContext(keyStorePath, keyStorePassword)
                    .getServerSocketFactory()
                    .createServerSocket(port);
//                    .createServerSocket(address.getPort(), backlog, address.getAddress());

            // We don't need the password anymore → Overwrite it
            Arrays.fill(keyStorePassword, '0');

        } else {
            serverSocket = new ServerSocket(port);
//            serverSocket.bind(address);
        }
        return serverSocket;
    }

    private SSLContext getSslContext(Path keyStorePath, char[] keyStorePass) throws Exception {

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(new FileInputStream(keyStorePath.toFile()), keyStorePass);

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509"); //KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyStorePass);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        TrustManager[] trustManagers = tmf.getTrustManagers();

        // Null means using default implementations for TrustManager and SecureRandom
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagers, null);
        return sslContext;
    }

    private String createResponse(Charset encoding, String origin, JSONObject json) throws UnsupportedEncodingException {

        String body = json == null ? "" : json.toString();
        int contentLength = body.getBytes(encoding).length;
        return "HTTP/1.1 200 OK\r\n" +
                String.format("Content-Length: %d\r\n", contentLength) +
                String.format("Content-Type: application/json; charset=%s\r\n", encoding.displayName()) +

                "Cache-Control: no-cache\r\n" +
                "Access-Control-Allow-Origin: " + origin + "\r\n" +
                "Access-Control-Allow-Methods: GET. POST\r\n" +
                "Access-Control-Allow-Headers: Content-Type, *\r\n" +
                "Access-Control-Allow-Credentials: true\r\n" +
                "P3P: CP='IDC DSP COR ADM DEVi TAIi PSA PSD IVAi IVDi CONi HIS OUR IND CNT'\r\n" +

                // An empty line marks the end of the response's header
                "\r\n" +
                body;
    }

    private List<String> getHeaderLines(BufferedReader reader) throws IOException {
        List<String> lines = new ArrayList<>();
        String line = reader.readLine();
        // An empty line marks the end of the request's header
        while (line != null && !line.isEmpty()) {
            lines.add(line);
            line = reader.readLine();
        }
        return lines;
    }

    private JSONObject getPacket(Charset encoding, BufferedReader reader, List<String> headers) throws IOException {
        JSONObject packet = new JSONObject();
        packet.put("headers", headers);


        JSONObject query = new JSONObject();
        JSONObject parameters = new JSONObject();
        String get = "";
        if (!headers.isEmpty() && headers.get(0).split(" ").length > 1) {
            get = headers.get(0).split(" ")[1]; //headers.get(0).split(" ")[1]; URLDecoder.decode(headers.get(0).split(" ")[1], encoding.displayName());
        }
        packet.put("query", query);
        query.put("parameters", parameters);
        String context = URLDecoder.decode(get.split("\\?")[0], encoding.displayName());
        if (!context.isEmpty() && context.startsWith("/")) {
            context = context.substring(1);
        }
        query.put("context", context);
        if (get.split("\\?").length > 1) {
            for (String p : get.split("\\?")[1].split("\\&")) {
                if (!p.isEmpty()) {
                    String val = "";
                    if (p.split("\\=").length > 1) {
                        val = URLDecoder.decode(p.split("\\=")[1].trim(), encoding.displayName());
                    }
                    parameters.put(URLDecoder.decode(p.split("\\=")[0].trim(), encoding.displayName()).toLowerCase(), val);
                }
            }
        }

        int length = 0;
        for (String line : headers) {
            if (line.trim().toLowerCase().startsWith("content-length")) {
                length = Integer.parseInt(line.split(":")[1].trim());
                break;
            }
        }
        int c;
        StringBuffer b = new StringBuffer();
        while (length-- > 0) {
            if ((c = reader.read()) <= 0) {
                break;
            }
            b.append((char) c);
        }
        String page = b.toString();
        packet.put("body", page.isEmpty() ? new JSONObject() : new JSONObject(page));
        return packet;
    }

    private void startMultiThreaded(IReactor<JSONObject> reactor) throws Exception {

        int port = Integer.parseInt(Settings.getProperty("server.port", 1964 + ""));
        boolean ssl = Boolean.parseBoolean(Settings.getProperty("server.ssl", false + ""));
        int maxThreads = Integer.parseInt(Settings.getProperty("server.maxthreads", 100 + ""));
        ExecutorService threadPool = newCachedThreadPool(maxThreads);
        try (ServerSocket serverSocket = getServerSocket(port, ssl)) {

            this.serverSocket = serverSocket;

            Watchdog.log("Started multi-threaded HTTP server at port " + port + (ssl ? " with SSL" : ""));

            // A cached thread pool with a limited number of threads

            Charset encoding = StandardCharsets.UTF_8;

            // This infinite loop is not CPU-intensive since method "accept" blocks
            // until a client has made a connection to the socket
            while (active) {
                try {
                    Socket socket = serverSocket.accept();
                    // Create a response to the request on a separate thread to
                    // handle multiple requests simultaneously
                    threadPool.submit(() -> {

                        try ( // Use the socket to read the client's request
                              BufferedReader reader = new BufferedReader(new InputStreamReader(
                                      socket.getInputStream(), encoding.name()));
                              // Writing to the output stream and then closing it
                              // sends data to the client
                              BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                                      socket.getOutputStream(), encoding.name()))
                        ) {
                            List<String> headers = getHeaderLines(reader);
                            JSONObject json = getPacket(encoding, reader, headers);
                            JSONObject response = (JSONObject) reactor.run(json);

                            String origin = "*";
                            for (String line : headers) {
                                if (line.trim().toLowerCase().startsWith("origin:")) {
                                    origin = line.substring(7).trim();
                                    break;
//                                } else if (line.trim().toLowerCase().startsWith("referer:")) {
//                                    origin = line.substring(8).trim();
//                                    break;
                                }
                            }

                            writer.write(createResponse(encoding, origin, response));
                            writer.flush();
                            // We're done with the connection → Close the socket
                            socket.close();

                        } catch (SSLHandshakeException e) {
                            Watchdog.err("Exception while creating response");
                            Watchdog.err(e.toString());
                        } catch (Exception e) {
                            Watchdog.err("Exception while creating response");
                            e.printStackTrace(System.err);
                        }
                    });
                } catch (IOException e) {
                    if (active) {
                        Watchdog.err("Exception while handling connection");
                        e.printStackTrace(System.err);
                    } else {
                        Watchdog.log("Server socket closed");
                    }
                }
            }
        } catch (Exception e) {
            Watchdog.err("Could not create socket at port " + port);
            e.printStackTrace(System.err);
        } finally {
            threadPool.shutdown();
        }
    }

    private ExecutorService newCachedThreadPool(int maximumNumberOfThreads) {
        return new ThreadPoolExecutor(0, maximumNumberOfThreads,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>());
    }

    public void stop() {
        active = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }
    }

}
