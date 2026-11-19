package quizengine.exception;

/**
 * Base checked exception for all domain-specific errors in the
 * Interactive Quiz Competition Engine (PCC-CS593).
 */
public class QuizException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public QuizException(String message) {
        this("QUIZ_ERROR", message, null);
    }

    public QuizException(String errorCode, String message) {
        this(errorCode, message, null);
    }

    public QuizException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = (errorCode == null || errorCode.isBlank()) ? "QUIZ_ERROR" : errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
