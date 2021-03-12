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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
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

    private int port = 440;
    private boolean ssl = true;

    public void start(int port, boolean ssl, IReactor<JSONObject> reactor) {
        this.port = port;
        this.ssl = ssl;

        startMultiThreaded(port, ssl, reactor);
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

    private String createResponse(Charset encoding, JSONObject json) {

        String body = json == null ? "" : json.toString();
        int contentLength = body.getBytes(encoding).length;
        return "HTTP/1.1 200 OK\r\n" +
                String.format("Content-Length: %d\r\n", contentLength) +
                String.format("Content-Type: application/json; charset=%s\r\n", encoding.displayName()) +

                "Cache-Control: no-cache\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
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
        while (length-- > 0 && (c = reader.read()) != -1) {
            b.append((char) c);
        }
        String page = b.toString();
        packet.put("body", page.isEmpty() ? new JSONObject() : new JSONObject(page));
        return packet;
    }

    private void startMultiThreaded(int port, boolean ssl, IReactor<JSONObject> reactor) {

        try (ServerSocket serverSocket = getServerSocket(port, ssl)) {

            System.out.println("Started multi-threaded server at port " + port + (ssl ? " with SSL" : ""));

            // A cached thread pool with a limited number of threads
            ExecutorService threadPool = newCachedThreadPool(8);

            Charset encoding = StandardCharsets.UTF_8;

            // This infinite loop is not CPU-intensive since method "accept" blocks
            // until a client has made a connection to the socket
            while (true) {
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

                            writer.write(createResponse(encoding, response));
                            writer.flush();
                            // We're done with the connection → Close the socket
                            socket.close();

                        } catch (Exception e) {
                            System.err.println("Exception while creating response");
                            e.printStackTrace();
                        }
                    });
                } catch (IOException e) {
                    System.err.println("Exception while handling connection");
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("Could not create socket at port " + port);
            e.printStackTrace();
        }
    }

    private ExecutorService newCachedThreadPool(int maximumNumberOfThreads) {
        return new ThreadPoolExecutor(0, maximumNumberOfThreads,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>());
    }

}
