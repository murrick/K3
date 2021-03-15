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

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HttpServer {

    private volatile boolean active = true;
    private ServerSocket serverSocket = null;

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

    public void start(IReactor<JSONObject> reactor) throws Exception {

        int port = Integer.parseInt(Settings.getProperty("server.port", 1964 + ""));
        int maxThreads = Integer.parseInt(Settings.getProperty("server.maxthreads", 100 + ""));
        ExecutorService threadPool = newCachedThreadPool(maxThreads);
        try (ServerSocket serverSocket = new ServerSocket(port)) {

            this.serverSocket = serverSocket;

            Watchdog.log("Started multi-threaded HTTP server at port " + port);

            // A cached thread pool with a limited number of threads

            Charset encoding = StandardCharsets.UTF_8;

            // This infinite loop is not CPU-intensive since method "accept" blocks
            // until a client has made a connection to the socket
            while (active) {
                try {
                    Socket socket = serverSocket.accept();
                    InetAddress addr = socket.getInetAddress();

//                    Watchdog.log("Connection from " + addr.getHostAddress());
                    boolean allowed = false;
                    List<String> list = Settings.getByPrefix("server.remote.allowed");
                    if (list.isEmpty()) {
                        Settings.setProperty("server.remote.allowed.0", "localhost");
                        Settings.setProperty("server.remote.allowed.1", "127.0.0.1");
                        Settings.setProperty("server.remote.allowed.2", "0:0:0:0:0:0:0:1");
                    }
                    for (String str : Settings.getByPrefix("server.remote.allowed")) {
                        if (compareAddresses(addr.getHostAddress(), str)) {
                            allowed = true;
                            break;
                        }
                    }

                    if (allowed) {
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

                            } catch (Exception e) {
                                Watchdog.err("Exception while creating response");
                                e.printStackTrace(System.err);
                            }
                        });
                    } else {
                        Watchdog.log("Block inbound connection from " + addr.getHostAddress());
                        socket.close();
                    }
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

    private boolean compareAddresses(String hostAddress, String str) {
        String a = hostAddress;
        String[] ss = str.split("\\*");
        if (str.startsWith("*")) {
            if (ss.length > 0 && !ss[0].isEmpty()) {
                while (!a.isEmpty() && !a.startsWith(ss[0])) {
                    a = a.substring(1);
                }
            } else {
                return true;
            }
        }
        for (int i = 0; !a.isEmpty() && i < ss.length; ++i) {
            if (!a.startsWith(ss[i])) {
                return false;
            }
            a = a.substring(ss[i].length());
            while (!a.isEmpty() && !a.startsWith(ss[0])) {
                a = a.substring(1);
            }
            if (a.isEmpty()) {
                return true;
            }
        }
        return true;
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
