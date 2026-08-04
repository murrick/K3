/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.kanger.interfaces.IUser;

import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded asynchronous SMTP boundary owned by the server runtime.
 *
 * <p>The transport is disabled by default. STARTTLS and implicit TLS (SMTPS)
 * are mutually exclusive explicit modes and both retain the Java platform
 * certificate and hostname validation defaults.</p>
 */
final class MailTransport implements MailBoundaryReactor.MailGateway {

    enum Mode {
        DISABLED,
        STARTTLS,
        SMTPS;

        static Mode parse(String value) {
            if (value == null || value.trim().isEmpty()) {
                return DISABLED;
            }
            String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
            if ("SSL".equals(normalized)) {
                return SMTPS;
            }
            return Mode.valueOf(normalized);
        }
    }

    interface Sender {
        void send(Config config, Envelope envelope) throws Exception;
    }

    static final class Config {
        final Mode mode;
        final String host;
        final int port;
        final String login;
        final String password;
        final String from;
        final boolean authentication;
        final boolean debug;
        final int connectionTimeoutMillis;
        final int readTimeoutMillis;
        final int writeTimeoutMillis;
        final int workerCount;
        final int queueCapacity;

        Config(Mode mode,
               String host,
               int port,
               String login,
               String password,
               String from,
               boolean authentication,
               boolean debug,
               int connectionTimeoutMillis,
               int readTimeoutMillis,
               int writeTimeoutMillis,
               int workerCount,
               int queueCapacity) {
            this.mode = mode;
            this.host = host;
            this.port = port;
            this.login = login;
            this.password = password;
            this.from = from;
            this.authentication = authentication;
            this.debug = debug;
            this.connectionTimeoutMillis = connectionTimeoutMillis;
            this.readTimeoutMillis = readTimeoutMillis;
            this.writeTimeoutMillis = writeTimeoutMillis;
            this.workerCount = workerCount;
            this.queueCapacity = queueCapacity;
            validate();
        }

        private void validate() {
            if (mode == null) {
                throw new IllegalArgumentException("mail mode must not be null");
            }
            if (mode != Mode.DISABLED) {
                if (host == null || host.trim().isEmpty()) {
                    throw new IllegalArgumentException("server.email.host must be configured");
                }
                if (port <= 0 || port > 65535) {
                    throw new IllegalArgumentException("server.email.port must be between 1 and 65535");
                }
                if (from == null || from.trim().isEmpty()) {
                    throw new IllegalArgumentException("server.email.from must be configured");
                }
                if (authentication && (login == null || login.isEmpty())) {
                    throw new IllegalArgumentException("server.email.login must be configured when authentication is enabled");
                }
            }
            if (connectionTimeoutMillis <= 0 || readTimeoutMillis <= 0 || writeTimeoutMillis <= 0) {
                throw new IllegalArgumentException("SMTP timeouts must be greater than zero");
            }
            if (workerCount <= 0 || queueCapacity <= 0) {
                throw new IllegalArgumentException("SMTP worker and queue limits must be greater than zero");
            }
        }

        String protocol() {
            return mode == Mode.SMTPS ? "smtps" : "smtp";
        }

        Properties properties() {
            Properties properties = new Properties();
            String protocol = protocol();
            String prefix = "mail." + protocol + ".";

            properties.setProperty("mail.transport.protocol", protocol);
            properties.setProperty(prefix + "host", host == null ? "" : host);
            properties.setProperty(prefix + "port", Integer.toString(port));
            properties.setProperty(prefix + "auth", Boolean.toString(authentication));
            properties.setProperty(prefix + "connectiontimeout", Integer.toString(connectionTimeoutMillis));
            properties.setProperty(prefix + "timeout", Integer.toString(readTimeoutMillis));
            properties.setProperty(prefix + "writetimeout", Integer.toString(writeTimeoutMillis));
            properties.setProperty(prefix + "ssl.checkserveridentity", "true");
            properties.setProperty("mail.debug", Boolean.toString(debug));

            if (mode == Mode.STARTTLS) {
                properties.setProperty("mail.smtp.starttls.enable", "true");
                properties.setProperty("mail.smtp.starttls.required", "true");
                properties.setProperty("mail.smtp.ssl.enable", "false");
            } else if (mode == Mode.SMTPS) {
                properties.setProperty("mail.smtps.ssl.enable", "true");
                properties.setProperty("mail.smtps.starttls.enable", "false");
            }
            return properties;
        }
    }

    static final class Envelope {
        final String recipient;
        final String subject;
        final String body;
        final String charset;

        Envelope(String recipient, String subject, String body, String charset) {
            this.recipient = recipient;
            this.subject = subject;
            this.body = body;
            this.charset = charset;
        }
    }

    private static final Object RUNTIME_MONITOR = new Object();
    private static volatile MailTransport runtime;

    private final Config config;
    private final Sender sender;
    private final ThreadPoolExecutor executor;

    MailTransport(Config config, Sender sender) {
        if (config == null || sender == null) {
            throw new IllegalArgumentException("mail config and sender must not be null");
        }
        this.config = config;
        this.sender = sender;
        this.executor = config.mode == Mode.DISABLED ? null : new ThreadPoolExecutor(
                config.workerCount,
                config.workerCount,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(config.queueCapacity),
                new MailThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        if (executor != null) {
            executor.allowCoreThreadTimeOut(true);
        }
    }

    static MailTransport runtime() throws Exception {
        MailTransport current = runtime;
        if (current != null) {
            return current;
        }
        synchronized (RUNTIME_MONITOR) {
            if (runtime == null) {
                runtime = new MailTransport(fromSettings(), new JavaMailSender());
            }
            return runtime;
        }
    }

    static void shutdownRuntime() {
        MailTransport current;
        synchronized (RUNTIME_MONITOR) {
            current = runtime;
            runtime = null;
        }
        if (current != null) {
            current.shutdown();
        }
    }

    static void resetRuntimeForTests() {
        shutdownRuntime();
    }

    @Override
    public boolean isEnabled() {
        return config.mode != Mode.DISABLED;
    }

    @Override
    public void validateRecipient(String address) throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException("E-mail transport is disabled");
        }
        InternetAddress parsed = new InternetAddress(address, true);
        parsed.validate();
    }

    @Override
    public void queueConfirmation(IUser user, String confirmationToken) throws Exception {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        queueConfirmation(
                user.getProperty("reg.login", ""),
                user.getProperty("reg.email", ""),
                confirmationToken);
    }

    /**
     * Queues confirmation directly from pending registration data. No IUser or
     * account artifacts are required.
     */
    void queueConfirmation(String login,
                           String recipient,
                           String confirmationToken) throws Exception {
        validateRecipient(recipient);
        String publicUrl = Settings.getProperty("server.url", "https://kanger.org");
        Envelope envelope = new Envelope(
                recipient,
                "KANGER: Registration confirmation",
                "You are just registered on site kanger.org with login " + login
                        + ". Please confirm your e-mail by following link: "
                        + publicUrl + "/?confirm=" + confirmationToken,
                "UTF-8");
        submit(envelope);
    }

    void submit(final Envelope envelope) {
        if (!isEnabled() || executor == null) {
            throw new IllegalStateException("E-mail transport is disabled");
        }
        if (envelope == null) {
            throw new IllegalArgumentException("mail envelope must not be null");
        }
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        sender.send(config, envelope);
                    } catch (Exception error) {
                        System.err.println(new Date());
                        error.printStackTrace(System.err);
                    }
                }
            });
        } catch (RejectedExecutionException error) {
            throw new IllegalStateException("E-mail queue is full or shutting down", error);
        }
    }

    int queuedMessages() {
        return executor == null ? 0 : executor.getQueue().size();
    }

    void shutdown() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException error) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    static Config fromSettings() throws Exception {
        Mode mode = Mode.parse(Settings.getProperty("server.email.mode", "disabled"));
        int defaultPort = mode == Mode.SMTPS ? 465 : 587;
        return new Config(
                mode,
                Settings.getProperty("server.email.host", ""),
                positiveInt("server.email.port", defaultPort),
                Settings.getProperty("server.email.login", ""),
                Settings.getProperty("server.email.password", ""),
                Settings.getProperty("server.email.from", ""),
                Boolean.parseBoolean(Settings.getProperty("server.email.auth", "true")),
                Boolean.parseBoolean(Settings.getProperty("server.email.debug", "false")),
                positiveInt("server.email.connection.timeout.millis", 10000),
                positiveInt("server.email.read.timeout.millis", 10000),
                positiveInt("server.email.write.timeout.millis", 10000),
                positiveInt("server.email.workers", 1),
                positiveInt("server.email.queue.capacity", 64));
    }

    private static int positiveInt(String key, int defaultValue) throws Exception {
        int value = Integer.parseInt(Settings.getProperty(key, Integer.toString(defaultValue)));
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be greater than zero");
        }
        return value;
    }

    private static final class JavaMailSender implements Sender {
        @Override
        public void send(final Config config, Envelope envelope) throws Exception {
            Properties properties = config.properties();
            Authenticator authenticator = config.authentication ? new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.login, config.password);
                }
            } : null;

            Session session = Session.getInstance(properties, authenticator);
            session.setDebug(config.debug);

            MimeMessage message = new MimeMessage(session);
            InternetAddress fromAddress = new InternetAddress(config.from, true);
            InternetAddress recipient = new InternetAddress(envelope.recipient, true);
            recipient.validate();

            message.setFrom(fromAddress);
            message.setRecipient(Message.RecipientType.TO, recipient);
            message.setReplyTo(new Address[]{fromAddress});
            message.setSentDate(new Date());
            message.setSubject(envelope.subject, Charset.forName(envelope.charset).name());
            message.setText(envelope.body, Charset.forName(envelope.charset).name());

            Transport transport = session.getTransport(config.protocol());
            try {
                if (config.authentication) {
                    transport.connect(config.host, config.port, config.login, config.password);
                } else {
                    transport.connect(config.host, config.port, null, null);
                }
                transport.sendMessage(message, message.getAllRecipients());
            } catch (MessagingException error) {
                throw error;
            } finally {
                try {
                    transport.close();
                } catch (MessagingException ignored) {
                    // original send/connect failure remains authoritative
                }
            }
        }
    }

    private static final class MailThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable,
                    "kanger-mail-" + sequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        }
    }
}
