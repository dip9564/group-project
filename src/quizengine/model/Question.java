package quizengine.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Concrete multiple-choice question (MCQ) extending {@link AssessmentItem}.
 *
 * <p>Demonstrates core Java <strong>Arrays</strong> by storing the four options
 * in a fixed-length {@code String[4]} array alongside the {@code char[]} label array
 * ({@code {'A', 'B', 'C', 'D'}}), returning defensive copies to preserve encapsulation.</p>
 */
public class Question extends AssessmentItem {

    public static final int EXPECTED_OPTION_COUNT = 4;
    public static final char[] OPTION_LABELS = {'A', 'B', 'C', 'D'};

    private final String[] options;
    private final char correctOption;

    /**
     * Creates an immutable multiple-choice question backed by a 4-element String array.
     *
     * @param prompt        non-blank question text
     * @param options       array containing exactly 4 non-blank, distinct option texts (A-D)
     * @param correctOption answer key letter ('A', 'B', 'C', or 'D', case-insensitive)
     * @throws IllegalArgumentException if any invariant is violated
     */
    public Question(String prompt, String[] options, char correctOption) {
        super(prompt);
        if (options == null || options.length != EXPECTED_OPTION_COUNT) {
            throw new IllegalArgumentException(
                    "A multiple-choice question must have exactly " + EXPECTED_OPTION_COUNT + " options.");
        }

        String[] cleanedOptions = new String[EXPECTED_OPTION_COUNT];
        Set<String> seenOptions = new HashSet<>(EXPECTED_OPTION_COUNT);
        for (int i = 0; i < EXPECTED_OPTION_COUNT; i++) {
            String opt = options[i];
            if (opt == null || opt.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Option " + OPTION_LABELS[i] + " must not be null or empty.");
            }
            String trimmedOpt = opt.trim();
            String normalizedOpt = trimmedOpt.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            if (!seenOptions.add(normalizedOpt)) {
                throw new IllegalArgumentException(
                        "Duplicate option text detected at option " + OPTION_LABELS[i] + ": '" + trimmedOpt + "'.");
            }
            cleanedOptions[i] = trimmedOpt;
        }

        char normalizedAnswer = Character.toUpperCase(correctOption);
        if (!isValidOptionLetter(normalizedAnswer)) {
            throw new IllegalArgumentException(
                    "Correct answer must be one of A, B, C, or D (received: '" + correctOption + "').");
        }

        this.options = cleanedOptions;
        this.correctOption = normalizedAnswer;
    }

    /**
     * Overloaded constructor accepting the correct answer as a String.
     *
     * @param prompt        non-blank question text
     * @param options       4-element String array of options
     * @param correctAnswer single-letter answer string ("A"-"D", case-insensitive)
     */
    public Question(String prompt, String[] options, String correctAnswer) {
        this(prompt, options, parseAnswerLetter(correctAnswer));
    }

    public static boolean isValidOptionLetter(char letter) {
        char upper = Character.toUpperCase(letter);
        return upper == 'A' || upper == 'B' || upper == 'C' || upper == 'D';
    }

    public static char parseAnswerLetter(String rawAnswer) {
        if (rawAnswer == null) {
            throw new IllegalArgumentException("Answer must not be null.");
        }
        String trimmed = rawAnswer.trim().toUpperCase(Locale.ROOT);
        if (trimmed.length() != 1 || !isValidOptionLetter(trimmed.charAt(0))) {
            throw new IllegalArgumentException(
                    "Answer must be a single letter A, B, C, or D (received: '" + rawAnswer + "').");
        }
        return trimmed.charAt(0);
    }

    /**
     * Returns a defensive copy of the internal 4-element options array so callers
     * cannot mutate the question's encapsulated state.
     *
     * @return new {@code String[4]} containing options A through D
     */
    public String[] getOptions() {
        return Arrays.copyOf(options, options.length);
    }

    public int getOptionCount() {
        return options.length;
    }

    public char getCorrectOption() {
        return correctOption;
    }

    public String getOptionText(char optionLetter) {
        char normalized = Character.toUpperCase(optionLetter);
        int index = normalized - 'A';
        if (index < 0 || index >= options.length) {
            throw new IllegalArgumentException("Invalid option letter: " + optionLetter);
        }
        return options[index];
    }

    @Override
    public boolean evaluateAnswer(String participantResponse) {
        if (participantResponse == null) {
            return false;
        }
        String normalized = participantResponse.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 1) {
            return false;
        }
        return normalized.charAt(0) == this.correctOption;
    }

    @Override
    public String getCorrectAnswerDisplay() {
        return correctOption + ") " + getOptionText(correctOption);
    }

    @Override
    public String formatForDisplay(int questionNumber, int totalQuestions) {
        StringBuilder sb = new StringBuilder();
        sb.append("Question ").append(questionNumber).append(" of ").append(totalQuestions)
          .append(System.lineSeparator())
          .append("----------------------------------------------")
          .append(System.lineSeparator())
          .append(getPrompt())
          .append(System.lineSeparator())
          .append(System.lineSeparator());
        for (int i = 0; i < options.length; i++) {
            sb.append("  ").append(OPTION_LABELS[i]).append(") ").append(options[i])
              .append(System.lineSeparator());
        }
        return sb.toString();
    }

    public String getNormalizedPromptKey() {
        return getPrompt().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Question)) {
            return false;
        }
        Question other = (Question) o;
        return correctOption == other.correctOption
                && getNormalizedPromptKey().equals(other.getNormalizedPromptKey())
                && Arrays.equals(options, other.options);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(getNormalizedPromptKey(), correctOption);
        result = 31 * result + Arrays.hashCode(options);
        return result;
    }

    @Override
    public String toString() {
        return "Question{prompt='" + getPrompt() + "', correctOption=" + correctOption + "}";
    }
}
