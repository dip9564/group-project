package quizengine.exception;

/**
 * Checked exception indicating malformed, empty, or logically invalid
 * quiz content such as invalid question records, CLI flags, or corrupted score entries.
 */
public class InvalidQuizDataException extends QuizException {

    private static final long serialVersionUID = 1L;

    public InvalidQuizDataException(String message) {
        super("INVALID_DATA", message);
    }

    public InvalidQuizDataException(String message, Throwable cause) {
        super("INVALID_DATA", message, cause);
    }
}
