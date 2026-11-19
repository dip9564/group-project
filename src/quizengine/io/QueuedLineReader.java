package quizengine.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Multithreaded console line reader that isolates blocking {@link BufferedReader#readLine()}
 * calls onto a single daemon {@link Thread} feeding a {@link BlockingQueue}.
 *
 * <p>Enables non-bypassable timed reads ({@link #readLineWithTimeout(long)}) so
 * an expired quiz timer terminates input waiting immediately without freezing
 * on standard input.</p>
 */
public class QueuedLineReader {

    public enum ReadStatus {
        OK,
        TIMEOUT,
        EOF
    }

    public static final class ReadResult {
        private final ReadStatus status;
        private final String line;

        private ReadResult(ReadStatus status, String line) {
            this.status = status;
            this.line = line;
        }

        public static ReadResult ok(String line) {
            return new ReadResult(ReadStatus.OK, line);
        }

        public static ReadResult timeout() {
            return new ReadResult(ReadStatus.TIMEOUT, null);
        }

        public static ReadResult eof() {
            return new ReadResult(ReadStatus.EOF, null);
        }

        public ReadStatus getStatus() {
            return status;
        }

        public String getLine() {
            return line;
        }
    }

    private static final String EOF_SENTINEL = new String("__QUIZ_ENGINE_EOF__");

    private final BlockingQueue<String> lineQueue = new LinkedBlockingQueue<>();

    public QueuedLineReader(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        Thread readerThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineQueue.put(line);
                }
            } catch (IOException | InterruptedException ignored) {
                // Stream closed or reader interrupted during shutdown
            } finally {
                lineQueue.offer(EOF_SENTINEL);
            }
        }, "quiz-console-input-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public ReadResult readLine() {
        try {
            String item = lineQueue.take();
            if (item == EOF_SENTINEL) {
                lineQueue.offer(EOF_SENTINEL);
                return ReadResult.eof();
            }
            return ReadResult.ok(item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ReadResult.eof();
        }
    }

    public ReadResult readLineWithTimeout(long timeoutMillis) {
        if (timeoutMillis <= 0L) {
            return ReadResult.timeout();
        }
        try {
            String item = lineQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
            if (item == null) {
                return ReadResult.timeout();
            }
            if (item == EOF_SENTINEL) {
                lineQueue.offer(EOF_SENTINEL);
                return ReadResult.eof();
            }
            return ReadResult.ok(item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ReadResult.timeout();
        }
    }
}
