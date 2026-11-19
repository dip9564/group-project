package quizengine.model;

/**
 * Abstract superclass representing a general assessable item in a quiz.
 * Demonstrates Abstraction, Encapsulation, Inheritance, and Polymorphism.
 */
public abstract class AssessmentItem {

    private final String prompt;

    protected AssessmentItem(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Question prompt must not be null or empty.");
        }
        this.prompt = prompt.trim();
    }

    public String getPrompt() {
        return prompt;
    }

    public abstract boolean evaluateAnswer(String participantResponse);

    public abstract String getCorrectAnswerDisplay();

    public abstract String formatForDisplay(int questionNumber, int totalQuestions);
}
