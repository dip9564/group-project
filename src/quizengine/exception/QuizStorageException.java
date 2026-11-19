package quizengine.exception;

/**
 * Checked exception representing file system and persistent storage errors,
 * such as missing files, invalid encoding, permission failures, or I/O interruptions.
 */
public class QuizStorageException extends QuizException {

    private static final long serialVersionUID = 1L;

    public QuizStorageException(String message) {
        super("STORAGE_ERROR", message);
    }

    public QuizStorageException(String message, Throwable cause) {
        super("STORAGE_ERROR", message, cause);
    }
}
