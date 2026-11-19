package quizengine.engine;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import quizengine.model.Score;

/**
 * Reliable monotonic quiz timer with a dedicated background countdown monitor {@link Thread}.
 *
 * <p>Demonstrates core Java <strong>Multithreading</strong> ({@link Thread}, {@link Runnable},
 * {@code volatile} visibility, {@link AtomicBoolean}, and clean {@link Thread#interrupt()}
 * lifecycle management) alongside drift-free {@link System#nanoTime()} elapsed-time tracking.</p>
 */
public class QuizTimer {

    public static final long DEFAULT_TIME_LIMIT_SECONDS = 300L; // 5 minutes
    public static final long LOW_TIME_THRESHOLD_SECONDS = 30L;
    public static final long MAX_TIME_LIMIT_SECONDS = Long.MAX_VALUE / 1_000_000_000L;

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long MONITOR_POLL_MILLIS = 50L;

    private final long timeLimitSeconds;
    private final LongSupplier nanoClock;

    private volatile boolean running;
    private volatile boolean started;
    private volatile boolean expired;
    private volatile boolean lowTimeAlert;
    private final AtomicBoolean timeoutCallbackFired = new AtomicBoolean(false);

    private long startNanos;
    private long endNanos;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Thread monitorThread;

    public QuizTimer() {
        this(DEFAULT_TIME_LIMIT_SECONDS, System::nanoTime);
    }

    public QuizTimer(long timeLimitSeconds) {
        this(timeLimitSeconds, System::nanoTime);
    }

    public QuizTimer(long timeLimitSeconds, LongSupplier nanoClock) {
        if (timeLimitSeconds <= 0 || timeLimitSeconds > MAX_TIME_LIMIT_SECONDS) {
            throw new IllegalArgumentException(
                    "Quiz time limit must be between 1 and " + MAX_TIME_LIMIT_SECONDS + " seconds.");
        }
        this.timeLimitSeconds = timeLimitSeconds;
        this.nanoClock = Objects.requireNonNull(nanoClock, "Nano clock supplier must not be null.");
        this.running = false;
        this.started = false;
        this.expired = false;
        this.lowTimeAlert = false;
    }

    /**
     * Starts the timer and launches its background countdown monitor daemon thread.
     */
    public void start() {
        start(null);
    }

    /**
     * Starts the timer and launches its background countdown monitor daemon thread,
     * invoking {@code onTimeoutCallback} once if the deadline expires before {@link #stop()}.
     *
     * @param onTimeoutCallback optional runnable invoked by the background thread upon expiration
     */
    public synchronized void start(Runnable onTimeoutCallback) {
        stop(); // Clean up any prior run
        this.startNanos = nanoClock.getAsLong();
        this.endNanos = this.startNanos;
        this.startedAt = LocalDateTime.now();
        this.endedAt = null;
        this.expired = false;
        this.lowTimeAlert = false;
        this.timeoutCallbackFired.set(false);
        this.started = true;
        this.running = true;

        Runnable monitorTask = () -> runCountdownMonitor(onTimeoutCallback);
        this.monitorThread = new Thread(monitorTask, "quiz-countdown-monitor");
        this.monitorThread.setDaemon(true);
        this.monitorThread.start();
    }

    private void runCountdownMonitor(Runnable onTimeoutCallback) {
        while (running && !Thread.currentThread().isInterrupted()) {
            long remainingMs = getRemainingMillis();
            if (remainingMs <= LOW_TIME_THRESHOLD_SECONDS * 1000L && remainingMs > 0L) {
                this.lowTimeAlert = true;
            }
            if (remainingMs <= 0L) {
                this.expired = true;
                if (onTimeoutCallback != null && timeoutCallbackFired.compareAndSet(false, true)) {
                    onTimeoutCallback.run();
                }
                break;
            }
            try {
                Thread.sleep(Math.min(MONITOR_POLL_MILLIS, remainingMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Stops the timer, freezes elapsed time, and interrupts the background monitor thread.
     */
    public synchronized void stop() {
        if (running) {
            this.endNanos = nanoClock.getAsLong();
            this.endedAt = LocalDateTime.now();
            this.running = false;
        }
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isLowTimeAlert() {
        return started && (lowTimeAlert || (getRemainingSeconds() > 0L
                && getRemainingSeconds() <= LOW_TIME_THRESHOLD_SECONDS));
    }

    public boolean isMonitorThreadAlive() {
        return monitorThread != null && monitorThread.isAlive();
    }

    public long getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public long getElapsedNanos() {
        if (!started) {
            return 0L;
        }
        long reference = running ? nanoClock.getAsLong() : endNanos;
        long elapsed = reference - startNanos;
        return Math.max(0L, elapsed);
    }

    public long getElapsedMillis() {
        return getElapsedNanos() / NANOS_PER_MILLI;
    }

    public long getElapsedSeconds() {
        return getElapsedNanos() / NANOS_PER_SECOND;
    }

    public long getRemainingMillis() {
        long limitMillis = timeLimitSeconds * 1000L;
        long remaining = limitMillis - getElapsedMillis();
        return Math.max(0L, remaining);
    }

    public long getRemainingSeconds() {
        long remainingMillis = getRemainingMillis();
        if (remainingMillis == 0L) {
            return 0L;
        }
        return (remainingMillis + 999L) / 1000L;
    }

    public boolean isTimeExpired() {
        return started && (expired || getRemainingMillis() <= 0L);
    }

    public long getRecordedDurationSeconds() {
        long elapsed = getElapsedSeconds();
        return Math.min(elapsed, timeLimitSeconds);
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public String getFormattedElapsedTime() {
        return formatDuration(getElapsedSeconds());
    }

    public String getFormattedRemainingTime() {
        return formatDuration(getRemainingSeconds());
    }

    public static String formatDuration(long totalSeconds) {
        return Score.formatSeconds(totalSeconds);
    }
}
