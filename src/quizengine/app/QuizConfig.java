package quizengine.app;

import java.nio.file.Path;
import java.nio.file.Paths;
import quizengine.engine.QuizEngine;
import quizengine.exception.InvalidQuizDataException;
import quizengine.io.ConsoleUI;
import quizengine.storage.FileManager;

/**
 * Immutable configuration parsed from command-line arguments ({@code String[] args}).
 *
 * <p>Demonstrates core Java <strong>Command-Line Arguments</strong> and
 * {@link Integer} wrapper parsing for configuring quiz defaults at startup:</p>
 * <ul>
 *   <li>{@code --questions <N>} / {@code -q <N>} : default question count per attempt</li>
 *   <li>{@code --time <seconds>} / {@code -t <seconds>} : default quiz time limit (30-3600s)</li>
 *   <li>{@code --bank <path>} / {@code -b <path>} : question bank text file path</li>
 *   <li>{@code --scores <path>} / {@code -s <path>} : CSV score archive file path</li>
 *   <li>{@code --no-color} : force plain ASCII output without ANSI colors</li>
 *   <li>{@code --help} / {@code -h} : print CLI usage summary and exit</li>
 * </ul>
 */
public class QuizConfig {

    public static final int MIN_TIME_LIMIT_SECONDS = 30;
    public static final int MAX_TIME_LIMIT_SECONDS = 3600;

    private final int defaultQuestionCount;
    private final long defaultTimeLimitSeconds;
    private final Path questionsPath;
    private final Path scoresPath;
    private final boolean colorEnabled;
    private final boolean helpRequested;
    private final boolean customArgsProvided;

    private QuizConfig(
            int defaultQuestionCount,
            long defaultTimeLimitSeconds,
            Path questionsPath,
            Path scoresPath,
            boolean colorEnabled,
            boolean helpRequested,
            boolean customArgsProvided) {
        this.defaultQuestionCount = defaultQuestionCount;
        this.defaultTimeLimitSeconds = defaultTimeLimitSeconds;
        this.questionsPath = questionsPath;
        this.scoresPath = scoresPath;
        this.colorEnabled = colorEnabled;
        this.helpRequested = helpRequested;
        this.customArgsProvided = customArgsProvided;
    }

    /**
     * Returns the default configuration with no command-line overrides.
     */
    public static QuizConfig defaults() {
        return new QuizConfig(
                QuizEngine.DEFAULT_QUESTION_COUNT,
                QuizEngine.DEFAULT_TIME_LIMIT_SECONDS,
                FileManager.DEFAULT_QUESTIONS_PATH,
                FileManager.DEFAULT_SCORES_PATH,
                ConsoleUI.detectAnsiSupport(),
                false,
                false);
    }

    /**
     * Parses command-line arguments from {@code main(String[] args)}.
     *
     * @param args command-line arguments array
     * @return validated {@link QuizConfig}
     * @throws InvalidQuizDataException if an unknown flag, missing value, or invalid number is passed
     */
    public static QuizConfig fromArgs(String[] args) throws InvalidQuizDataException {
        int questions = QuizEngine.DEFAULT_QUESTION_COUNT;
        long timeLimit = QuizEngine.DEFAULT_TIME_LIMIT_SECONDS;
        Path bankPath = FileManager.DEFAULT_QUESTIONS_PATH;
        Path scoresPath = FileManager.DEFAULT_SCORES_PATH;
        boolean color = ConsoleUI.detectAnsiSupport();
        boolean help = false;
        boolean custom = args != null && args.length > 0;

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--help":
                    case "-h":
                        help = true;
                        break;
                    case "--no-color":
                        color = false;
                        break;
                    case "--questions":
                    case "-q":
                        String qVal = requireNextArg(args, i, arg);
                        i++;
                        questions = parsePositiveInt(qVal, arg, 1, 500);
                        break;
                    case "--time":
                    case "-t":
                        String tVal = requireNextArg(args, i, arg);
                        i++;
                        timeLimit = parsePositiveInt(tVal, arg, MIN_TIME_LIMIT_SECONDS, MAX_TIME_LIMIT_SECONDS);
                        break;
                    case "--bank":
                    case "-b":
                        String bVal = requireNextArg(args, i, arg);
                        i++;
                        bankPath = Paths.get(bVal);
                        break;
                    case "--scores":
                    case "-s":
                        String sVal = requireNextArg(args, i, arg);
                        i++;
                        scoresPath = Paths.get(sVal);
                        break;
                    default:
                        throw new InvalidQuizDataException(
                                "Unknown command-line argument: '" + arg + "'. Run with --help for usage.");
                }
            }
        }

        return new QuizConfig(questions, timeLimit, bankPath, scoresPath, color, help, custom);
    }

    private static String requireNextArg(String[] args, int index, String flag) throws InvalidQuizDataException {
        if (index + 1 >= args.length || args[index + 1].trim().isEmpty()) {
            throw new InvalidQuizDataException("Missing value after option '" + flag + "'.");
        }
        return args[index + 1].trim();
    }

    private static int parsePositiveInt(String raw, String flag, int min, int max) throws InvalidQuizDataException {
        Integer value;
        try {
            value = Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            throw new InvalidQuizDataException(
                    "Invalid numeric value '" + raw + "' for option '" + flag + "'.", e);
        }
        if (value < min || value > max) {
            throw new InvalidQuizDataException(
                    "Value for '" + flag + "' must be between " + min + " and " + max + " (got " + value + ").");
        }
        return value.intValue();
    }

    public static String getUsageText() {
        return String.join(System.lineSeparator(),
                "Usage: java -cp out quizengine.app.Main [OPTIONS]",
                "",
                "Options:",
                "  -q, --questions <N>     Default questions per quiz (1-500, default: 10)",
                "  -t, --time <seconds>    Default quiz time limit in seconds (30-3600, default: 300)",
                "  -b, --bank <path>       Path to question bank file (default: data/questions.txt)",
                "  -s, --scores <path>     Path to CSV score archive (default: data/scores.txt)",
                "      --no-color          Disable ANSI colors and use plain 7-bit ASCII",
                "  -h, --help              Display this usage message and exit");
    }

    public int getDefaultQuestionCount() {
        return defaultQuestionCount;
    }

    public long getDefaultTimeLimitSeconds() {
        return defaultTimeLimitSeconds;
    }

    public Path getQuestionsPath() {
        return questionsPath;
    }

    public Path getScoresPath() {
        return scoresPath;
    }

    public boolean isColorEnabled() {
        return colorEnabled;
    }

    public boolean isHelpRequested() {
        return helpRequested;
    }

    public boolean hasCustomArgs() {
        return customArgsProvided;
    }
}
