import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Java 21 source-file load probe. Uses virtual threads and validates every ALELS ACK. */
public final class GatewayCapacityProbe {
    private record Settings(String host, int port, int connections, int durationSeconds,
                            int sendIntervalSeconds, int rampPerSecond, int connectTimeoutMs,
                            String imeiPrefix, Path output) {
    }

    public static void main(String[] args) throws Exception {
        Settings settings = parse(args);
        AtomicLong connected = new AtomicLong();
        AtomicLong connectFailed = new AtomicLong();
        AtomicLong sent = new AtomicLong();
        AtomicLong acknowledged = new AtomicLong();
        AtomicLong ioFailed = new AtomicLong();
        CountDownLatch finished = new CountDownLatch(settings.connections());
        long startedNanos = System.nanoTime();
        long deadlineNanos = startedNanos + TimeUnit.SECONDS.toNanos(settings.durationSeconds());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < settings.connections(); index++) {
                final int deviceIndex = index;
                executor.submit(() -> runDevice(settings, deviceIndex, deadlineNanos, connected,
                        connectFailed, sent, acknowledged, ioFailed, finished));
                if ((index + 1) % settings.rampPerSecond() == 0) Thread.sleep(1_000L);
            }
            finished.await(settings.durationSeconds() + 120L, TimeUnit.SECONDS);
        }

        double elapsedSeconds = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
        String evidence = """
                {
                  "schemaVersion": 1,
                  "finishedAt": "%s",
                  "host": "%s",
                  "port": %d,
                  "requestedConnections": %d,
                  "connected": %d,
                  "connectFailed": %d,
                  "sent": %d,
                  "acknowledged": %d,
                  "ioFailed": %d,
                  "elapsedSeconds": %.3f,
                  "messagesPerSecond": %.3f,
                  "ackReconciliationDelta": %d
                }
                """.formatted(Instant.now(), escape(settings.host()), settings.port(),
                settings.connections(), connected.get(), connectFailed.get(), sent.get(),
                acknowledged.get(), ioFailed.get(), elapsedSeconds,
                sent.get() / Math.max(elapsedSeconds, 0.001), sent.get() - acknowledged.get());
        System.out.print(evidence);
        if (settings.output() != null) {
            Path parent = settings.output().toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(settings.output(), evidence, StandardCharsets.UTF_8);
        }
        if (connected.get() != settings.connections() || sent.get() != acknowledged.get()
                || connectFailed.get() != 0L || ioFailed.get() != 0L) System.exit(2);
    }

    private static void runDevice(Settings settings, int index, long deadlineNanos,
                                  AtomicLong connected, AtomicLong connectFailed, AtomicLong sent,
                                  AtomicLong acknowledged, AtomicLong ioFailed, CountDownLatch finished) {
        String imei = settings.imeiPrefix() + String.format(Locale.ROOT, "%010d", index);
        try (Socket socket = new Socket()) {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.connect(new InetSocketAddress(settings.host(), settings.port()), settings.connectTimeoutMs());
            socket.setSoTimeout(Math.max(settings.sendIntervalSeconds() * 2_000, 5_000));
            connected.incrementAndGet();
            try (var output = new BufferedOutputStream(socket.getOutputStream());
                 var input = new BufferedInputStream(socket.getInputStream())) {
                long sequence = 0L;
                while (System.nanoTime() < deadlineNanos) {
                    String payload = "{\"T\":\"data\",\"A\":\"" + imei + "\",\"S\":"
                            + sequence++ + ",\"lat\":-6.2,\"lon\":106.8,\"speed\":10}\n";
                    output.write(payload.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    sent.incrementAndGet();
                    if (input.read() == '0' && input.read() == '1' && input.read() == '\n') {
                        acknowledged.incrementAndGet();
                    } else {
                        ioFailed.incrementAndGet();
                        return;
                    }
                    Thread.sleep(TimeUnit.SECONDS.toMillis(settings.sendIntervalSeconds()));
                }
            }
        } catch (java.net.ConnectException error) {
            connectFailed.incrementAndGet();
        } catch (Exception error) {
            ioFailed.incrementAndGet();
        } finally {
            finished.countDown();
        }
    }

    private static Settings parse(String[] args) {
        String host = value(args, "--host", "127.0.0.1");
        int port = positive(value(args, "--port", "5050"), "port");
        int connections = positive(value(args, "--connections", "10000"), "connections");
        int duration = positive(value(args, "--duration-seconds", "300"), "duration-seconds");
        int interval = positive(value(args, "--send-interval-seconds", "30"), "send-interval-seconds");
        int ramp = positive(value(args, "--ramp-per-second", "1000"), "ramp-per-second");
        int timeout = positive(value(args, "--connect-timeout-ms", "10000"), "connect-timeout-ms");
        String prefix = value(args, "--imei-prefix", "99000");
        Path output = has(args, "--output") ? Path.of(value(args, "--output", "")) : null;
        return new Settings(host, port, connections, duration, interval, ramp, timeout, prefix, output);
    }

    private static int positive(String raw, String name) {
        int value = Integer.parseInt(raw);
        if (value <= 0) throw new IllegalArgumentException(name + " must be greater than zero");
        return value;
    }

    private static boolean has(String[] args, String name) {
        for (String arg : args) if (name.equals(arg)) return true;
        return false;
    }

    private static String value(String[] args, String name, String fallback) {
        for (int i = 0; i < args.length - 1; i++) if (name.equals(args[i])) return args[i + 1];
        return fallback;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
