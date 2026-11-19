package quizengine.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a registered quiz participant for a single competition attempt.
 * Encapsulates the participant's validated full name, unique attempt identifier,
 * and registration timestamp.
 */
public class Participant {

    private final String attemptId;
    private final String fullName;
    private final LocalDateTime registeredAt;

    public Participant(String attemptId, String fullName) {
        this(attemptId, fullName, LocalDateTime.now());
    }

    public Participant(String attemptId, String fullName, LocalDateTime registeredAt) {
        if (attemptId == null || attemptId.trim().isEmpty()) {
            throw new IllegalArgumentException("Attempt ID must not be null or empty.");
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Participant name must not be null or empty.");
        }
        this.attemptId = attemptId.trim();
        this.fullName = fullName.trim();
        this.registeredAt = Objects.requireNonNull(registeredAt, "Registration timestamp must not be null.");
    }

    public String getAttemptId() {
        return attemptId;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    @Override
    public String toString() {
        return "Participant{attemptId='" + attemptId + "', fullName='" + fullName + "'}";
    }
}
