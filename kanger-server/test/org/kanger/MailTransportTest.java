package org.kanger;

import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailTransportTest {

    @Test
    void startTlsAndSmtpsAreMutuallyExclusive() {
        MailTransport.Config startTls = config(MailTransport.Mode.STARTTLS, 587, 1, 8);
        Properties startTlsProperties = startTls.properties();
        assertEquals("smtp", startTlsProperties.getProperty("mail.transport.protocol"));
        assertEquals("true", startTlsProperties.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", startTlsProperties.getProperty("mail.smtp.starttls.required"));
        assertEquals("false", startTlsProperties.getProperty("mail.smtp.ssl.enable"));
        assertFalse(startTlsProperties.containsKey("mail.smtps.ssl.enable"));

        MailTransport.Config smtps = config(MailTransport.Mode.SMTPS, 465, 1, 8);
        Properties smtpsProperties = smtps.properties();
        assertEquals("smtps", smtpsProperties.getProperty("mail.transport.protocol"));
        assertEquals("true", smtpsProperties.getProperty("mail.smtps.ssl.enable"));
        assertEquals("false", smtpsProperties.getProperty("mail.smtps.starttls.enable"));
        assertFalse(smtpsProperties.containsKey("mail.smtp.starttls.enable"));
    }

    @Test
    void debugIsOffAndAllTimeoutsAreFinite() {
        MailTransport.Config config = config(MailTransport.Mode.STARTTLS, 587, 1, 8);
        Properties properties = config.properties();

        assertEquals("false", properties.getProperty("mail.debug"));
        assertEquals("1000", properties.getProperty("mail.smtp.connectiontimeout"));
        assertEquals("2000", properties.getProperty("mail.smtp.timeout"));
        assertEquals("3000", properties.getProperty("mail.smtp.writetimeout"));
        assertFalse(properties.containsKey("mail.smtp.ssl.trust"));
        assertFalse(properties.containsKey("mail.smtps.ssl.trust"));
    }

    @Test
    void disabledTransportRejectsRecipientsWithoutCreatingExecutor() {
        MailTransport transport = new MailTransport(
                config(MailTransport.Mode.DISABLED, 587, 1, 8),
                new RecordingSender());
        assertFalse(transport.isEnabled());
        assertThrows(IllegalStateException.class,
                () -> transport.validateRecipient("rick@example.org"));
        assertEquals(0, transport.queuedMessages());
    }

    @Test
    void boundedQueueRejectsThirdMessageWhenWorkerAndQueueAreOccupied() throws Exception {
        BlockingSender sender = new BlockingSender();
        MailTransport transport = new MailTransport(
                config(MailTransport.Mode.STARTTLS, 587, 1, 1), sender);
        try {
            transport.submit(envelope("one@example.org"));
            assertTrue(sender.entered.await(2, TimeUnit.SECONDS));
            transport.submit(envelope("two@example.org"));
            assertEquals(1, transport.queuedMessages());

            assertThrows(IllegalStateException.class,
                    () -> transport.submit(envelope("three@example.org")));
        } finally {
            sender.release.countDown();
            transport.shutdown();
        }
        assertEquals(2, sender.sent.get());
    }

    @Test
    void validatesRecipientBeforeQueueing() throws Exception {
        MailTransport transport = new MailTransport(
                config(MailTransport.Mode.STARTTLS, 587, 1, 8),
                new RecordingSender());
        try {
            transport.validateRecipient("valid@example.org");
            assertThrows(Exception.class,
                    () -> transport.validateRecipient("not-an-address"));
        } finally {
            transport.shutdown();
        }
    }

    private static MailTransport.Config config(MailTransport.Mode mode,
                                                int port,
                                                int workers,
                                                int capacity) {
        return new MailTransport.Config(
                mode,
                mode == MailTransport.Mode.DISABLED ? "" : "smtp.example.org",
                port,
                "mailer",
                "secret",
                mode == MailTransport.Mode.DISABLED ? "" : "noreply@example.org",
                true,
                false,
                1000,
                2000,
                3000,
                workers,
                capacity);
    }

    private static MailTransport.Envelope envelope(String recipient) {
        return new MailTransport.Envelope(
                recipient, "subject", "body", "UTF-8");
    }

    private static final class RecordingSender implements MailTransport.Sender {
        @Override
        public void send(MailTransport.Config config,
                         MailTransport.Envelope envelope) {
            // no network
        }
    }

    private static final class BlockingSender implements MailTransport.Sender {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger sent = new AtomicInteger();

        @Override
        public void send(MailTransport.Config config,
                         MailTransport.Envelope envelope) throws Exception {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            sent.incrementAndGet();
        }
    }
}
