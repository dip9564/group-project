package quizengine.io;

import java.util.Locale;
import java.util.regex.Pattern;
import quizengine.exception.InvalidQuizDataException;
import quizengine.model.Question;

/**
 * Centralized input validation and normalization utility for console inputs.
 * Uses {@link Integer} wrapper parsing and regular expressions to reject empty,
 * non-numeric, out-of-range, or punctuation-abused inputs cleanly.
 */
public class InputValidator {

    private static final int MIN_NAME_LENGTH = 2;
    private static final int MAX_NAME_LENGTH = 60;
    private static final Pattern VALID_NAME_PATTERN =
            Pattern.compile("^[\\p{L}][\\p{L} .'-]*[\\p{L}.]$");
    private static final Pattern CONSECUTIVE_PUNCTUATION_PATTERN =
            Pattern.compile("[.'-]{2,}|\\s[.'-]|[.'-]\\s+(?=[.'-])");

    public Integer validateMenuChoice(String rawInput, int min, int max) throws InvalidQuizDataException {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            throw new InvalidQuizDataException(
                    "Input cannot be empty. Please enter a number between " + min + " and " + max + ".");
        }
        String trimmed = rawInput.trim();
        Integer choice;
        try {
            choice = Integer.valueOf(trimmed);
        } catch (NumberFormatException e) {
            throw new InvalidQuizDataException(
                    "Invalid input '" + trimmed + "'. Please enter a valid integer between " + min + " and " + max + ".",
                    e);
        }

        if (choice < min || choice > max) {
            throw new InvalidQuizDataException(
                    "Choice " + choice + " is out of range. Please choose a number between " + min + " and " + max + ".");
        }
        return choice;
    }

    public String validateParticipantName(String rawName) throws InvalidQuizDataException {
        if (rawName == null || rawName.trim().isEmpty()) {
            throw new InvalidQuizDataException("Participant name cannot be empty.");
        }

        String normalized = rawName.trim().replaceAll("\\s+", " ");
        if (normalized.length() < MIN_NAME_LENGTH) {
            throw new InvalidQuizDataException(
                    "Participant name must be at least " + MIN_NAME_LENGTH + " characters long.");
        }
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new InvalidQuizDataException(
                    "Participant name must not exceed " + MAX_NAME_LENGTH + " characters.");
        }
        if (!VALID_NAME_PATTERN.matcher(normalized).matches()
                || CONSECUTIVE_PUNCTUATION_PATTERN.matcher(normalized).find()
                || countLetters(normalized) < MIN_NAME_LENGTH) {
            throw new InvalidQuizDataException(
                    "Invalid name '" + normalized
                            + "'. Use at least 2 letters with optional spaces, periods (.), hyphens (-), or apostrophes (').");
        }
        return normalized;
    }

    public char validateAnswerChoice(String rawAnswer) throws InvalidQuizDataException {
        if (rawAnswer == null || rawAnswer.trim().isEmpty()) {
            throw new InvalidQuizDataException("Answer cannot be blank. Please enter A, B, C, or D.");
        }
        String normalized = rawAnswer.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 1 || !Question.isValidOptionLetter(normalized.charAt(0))) {
            throw new InvalidQuizDataException(
                    "Invalid choice '" + rawAnswer.trim() + "'. Please enter A, B, C, or D.");
        }
        return normalized.charAt(0);
    }

    public Integer validateOptionalIntInRange(String rawInput, int min, int max, int defaultValue)
            throws InvalidQuizDataException {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return Integer.valueOf(defaultValue);
        }
        return validateMenuChoice(rawInput, min, max);
    }

    private static int countLetters(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }
}
