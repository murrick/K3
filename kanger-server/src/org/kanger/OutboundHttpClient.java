/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Server-owned outbound HTTP boundary.
 *
 * <p>HTTPS uses the JVM platform trust store and the default hostname verifier.
 * This class never replaces the process-wide SSLContext or hostname verifier.</p>
 */
final class OutboundHttpClient {

    static final int DEFAULT_TIMEOUT_MILLIS = 10_000;
    static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private OutboundHttpClient() {
    }

    static String request(String urlText,
                          String post,
                          String encoding,
                          int timeoutMillis,
                          Map<String, String> headers) throws IOException {
        URL url = new URL(urlText);
        validateProtocol(url);

        URLConnection rawConnection = url.openConnection();
        if (!(rawConnection instanceof HttpURLConnection)) {
            throw new ProtocolException("Unsupported URL protocol: " + url.getProtocol());
        }

        HttpURLConnection connection = (HttpURLConnection) rawConnection;
        Charset charset = resolveCharset(encoding);
        int timeout = timeoutMillis > 0 ? timeoutMillis : DEFAULT_TIMEOUT_MILLIS;

        try {
            connection.setUseCaches(false);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(post == null ? "GET" : "POST");
            connection.setDoInput(true);

            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    if (header.getKey() != null && header.getValue() != null) {
                        connection.setRequestProperty(header.getKey(), header.getValue());
                    }
                }
            }

            if (post != null) {
                byte[] requestBody = post.getBytes(charset);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(requestBody.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(requestBody);
                }
            }

            int status = connection.getResponseCode();
            InputStream response = status >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            String responseText = response == null
                    ? ""
                    : readBounded(response, charset, MAX_RESPONSE_BYTES);

            if (status >= 400) {
                throw new IOException("HTTP " + status + " from " + url +
                        (responseText.isEmpty() ? "" : ": " + responseText));
            }
            return responseText;
        } finally {
            connection.disconnect();
        }
    }

    private static void validateProtocol(URL url) throws ProtocolException {
        String protocol = url.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            throw new ProtocolException("Unsupported URL protocol: " + protocol);
        }
    }

    private static Charset resolveCharset(String encoding) {
        if (encoding == null || encoding.trim().isEmpty()) {
            return StandardCharsets.UTF_8;
        }
        return Charset.forName(encoding);
    }

    private static String readBounded(InputStream input,
                                      Charset charset,
                                      int maximumBytes) throws IOException {
        try (InputStream source = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = source.read(buffer)) != -1) {
                total += read;
                if (total > maximumBytes) {
                    throw new IOException("Outbound HTTP response exceeds " + maximumBytes + " bytes");
                }
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), charset);
        }
    }
}
