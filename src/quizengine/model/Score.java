package quizengine.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Encapsulates the calculated outcome of a completed quiz attempt.
 *
 * <p>Demonstrates {@link Integer} and {@link Double} wrapper classes for score
 * metrics, division-by-zero &amp; upper-bound safeguards, and polymorphic
 * leaderboard ordering via {@link Comparable}.</p>
 */
public class Score implements Comparable<Score> {

    public static final DateTimeFormatter ARCHIVE_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String attemptId;
    private final String participantName;
    private final Integer totalQuestions;
    private final Integer correctAnswers;
    private final Integer incorrectAnswers;
    private final Integer unansweredQuestions;
    private final Double percentage;
    private final long durationSeconds;
    private final LocalDateTime completedAt;

    public Score(
            String attemptId,
            String participantName,
            Integer totalQuestions,
            Integer correctAnswers,
            Integer incorrectAnswers,
            Integer unansweredQuestions,
            long durationSeconds,
            LocalDateTime completedAt) {
        if (attemptId == null || attemptId.trim().isEmpty()) {
            throw new IllegalArgumentException("Attempt ID must not be null or empty.");
        }
        if (participantName == null || participantName.trim().isEmpty()) {
            throw new IllegalArgumentException("Participant name must not be null or empty.");
        }
        Objects.requireNonNull(totalQuestions, "Total questions must not be null.");
        Objects.requireNonNull(correctAnswers, "Correct answers must not be null.");
        Objects.requireNonNull(incorrectAnswers, "Incorrect answers must not be null.");
        Objects.requireNonNull(unansweredQuestions, "Unanswered questions must not be null.");
        Objects.requireNonNull(completedAt, "Completion timestamp must not be null.");

        if (totalQuestions < 0 || correctAnswers < 0 || incorrectAnswers < 0 || unansweredQuestions < 0) {
            throw new IllegalArgumentException("Question counts cannot be negative.");
        }
        if (correctAnswers + incorrectAnswers + unansweredQuestions != totalQuestions) {
            throw new IllegalArgumentException(
                    "Sum of correct, incorrect, and unanswered questions must equal total questions.");
        }
        if (durationSeconds < 0L) {
            throw new IllegalArgumentException("Duration cannot be negative.");
        }

        this.attemptId = attemptId.trim();
        this.participantName = participantName.trim();
        this.totalQuestions = totalQuestions;
        this.correctAnswers = correctAnswers;
        this.incorrectAnswers = incorrectAnswers;
        this.unansweredQuestions = unansweredQuestions;
        this.percentage = calculatePercentage(correctAnswers, totalQuestions);
        this.durationSeconds = durationSeconds;
        this.completedAt = completedAt.withNano(0);
    }

    public static Score fromArchivedRecord(
            String attemptId,
            String participantName,
            Integer correctAnswers,
            Integer totalQuestions,
            long durationSeconds,
            LocalDateTime completedAt) {
        Objects.requireNonNull(correctAnswers, "Archived score must not be null.");
        Objects.requireNonNull(totalQuestions, "Archived total must not be null.");
        if (totalQuestions <= 0) {
            throw new IllegalArgumentException("Archived total questions must be greater than zero.");
        }
        if (correctAnswers < 0 || correctAnswers > totalQuestions) {
            throw new IllegalArgumentException("Archived score must be between 0 and total questions.");
        }
        Integer incorrect = Integer.valueOf(totalQuestions - correctAnswers);
        return new Score(
                attemptId,
                participantName,
                totalQuestions,
                correctAnswers,
                incorrect,
                Integer.valueOf(0),
                durationSeconds,
                completedAt);
    }

    /**
     * Calculates score percentage safely using {@link Integer} and {@link Double} wrappers,
     * preventing division by zero and rejecting impossible values where {@code correct > total}.
     *
     * @param correct number of correct answers
     * @param total   total number of questions
     * @return percentage wrapped in {@link Double} within [0.0, 100.0]
     */
    public static Double calculatePercentage(Integer correct, Integer total) {
        if (total == null || total.intValue() <= 0 || correct == null || correct.intValue() <= 0) {
            return Double.valueOf(0.0);
        }
        if (correct.intValue() > total.intValue()) {
            throw new IllegalArgumentException(
                    "Correct answers (" + correct + ") cannot exceed total questions (" + total + ").");
        }
        double raw = (correct.doubleValue() / total.doubleValue()) * 100.0;
        if (Double.isNaN(raw) || Double.isInfinite(raw)) {
            return Double.valueOf(0.0);
        }
        double rounded = Math.round(raw * 100.0) / 100.0;
        return Double.valueOf(rounded);
    }

    public static String formatSeconds(long totalSeconds) {
        long safe = Math.max(0L, totalSeconds);
        long minutes = safe / 60L;
        long seconds = safe % 60L;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    public String getAttemptId() {
        return attemptId;
    }

    public String getParticipantName() {
        return participantName;
    }

    public Integer getTotalQuestions() {
        return totalQuestions;
    }

    public Integer getCorrectAnswers() {
        return correctAnswers;
    }

    public Integer getIncorrectAnswers() {
        return incorrectAnswers;
    }

    public Integer getUnansweredQuestions() {
        return unansweredQuestions;
    }

    public Integer getFinalScore() {
        return correctAnswers;
    }

    public Double getPercentage() {
        return percentage;
    }

    public String getFormattedPercentage() {
        return String.format(Locale.US, "%.2f", percentage);
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public String getFormattedDuration() {
        return formatSeconds(durationSeconds);
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getFormattedCompletedAt() {
        return completedAt.format(DISPLAY_DATE_FORMAT);
    }

    public String getIsoCompletedAt() {
        return completedAt.format(ARCHIVE_DATE_FORMAT);
    }

    public boolean isTiedWith(Score other) {
        if (other == null) {
            return false;
        }
        return Double.compare(this.percentage, other.percentage) == 0
                && Integer.compare(this.correctAnswers, other.correctAnswers) == 0
                && Long.compare(this.durationSeconds, other.durationSeconds) == 0;
    }

    @Override
    public int compareTo(Score other) {
        Objects.requireNonNull(other, "Cannot compare Score to null.");
        int cmp = Double.compare(other.percentage, this.percentage);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(other.correctAnswers, this.correctAnswers);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Long.compare(this.durationSeconds, other.durationSeconds);
        if (cmp != 0) {
            return cmp;
        }
        cmp = this.completedAt.compareTo(other.completedAt);
        if (cmp != 0) {
            return cmp;
        }
        return this.attemptId.compareTo(other.attemptId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Score)) {
            return false;
        }
        Score other = (Score) o;
        return durationSeconds == other.durationSeconds
                && attemptId.equals(other.attemptId)
                && participantName.equals(other.participantName)
                && totalQuestions.equals(other.totalQuestions)
                && correctAnswers.equals(other.correctAnswers)
                && incorrectAnswers.equals(other.incorrectAnswers)
                && unansweredQuestions.equals(other.unansweredQuestions)
                && percentage.equals(other.percentage)
                && completedAt.equals(other.completedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                attemptId,
                participantName,
                totalQuestions,
                correctAnswers,
                incorrectAnswers,
                unansweredQuestions,
                percentage,
                durationSeconds,
                completedAt);
    }

    @Override
    public String toString() {
        return "Score{attemptId='" + attemptId
                + "', participant='" + participantName
                + "', score=" + correctAnswers + "/" + totalQuestions
                + " (" + getFormattedPercentage() + "%), duration=" + durationSeconds + "s}";
    }
}
