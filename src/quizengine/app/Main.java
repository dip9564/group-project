package quizengine.app;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import quizengine.engine.Leaderboard;
import quizengine.engine.QuizEngine;
import quizengine.exception.InvalidQuizDataException;
import quizengine.exception.QuizException;
import quizengine.io.ConsoleUI;
import quizengine.io.InputValidator;
import quizengine.io.QueuedLineReader;
import quizengine.model.LoadResult;
import quizengine.model.Participant;
import quizengine.model.Question;
import quizengine.model.Score;
import quizengine.storage.FileManager;
import quizengine.storage.QuizDataStorage;

/**
 * Entry point and interactive menu controller for the
 * Interactive Quiz Competition Engine with Score Archiving (PCC-CS593).
 *
 * <p>Accepts command-line arguments via {@link QuizConfig#fromArgs(String[])} and
 * delegates quiz execution, validation, persistence, and ranking to modular classes
 * organized across dedicated Java packages.</p>
 */
public class Main {

    private static final int MENU_START_QUIZ = 1;
    private static final int MENU_VIEW_LEADERBOARD = 2;
    private static final int MENU_VIEW_PREVIOUS_SCORES = 3;
    private static final int MENU_VIEW_INSTRUCTIONS = 4;
    private static final int MENU_EXIT = 5;

    private final QuizConfig config;
    private final QuizDataStorage storage;
    private final InputValidator validator;
    private final ConsoleUI ui;
    private final QuizEngine quizEngine;
    private final Leaderboard leaderboard;
    private final QueuedLineReader reader;
    private final Set<String> sessionIssuedAttemptIds = new HashSet<>();

    public Main() {
        this(QuizConfig.defaults(), System.in, System.out);
    }

    public Main(QuizConfig config, InputStream inputStream, PrintStream outputStream) {
        this(
                config,
                new FileManager(config.getQuestionsPath(), config.getScoresPath()),
                inputStream,
                outputStream,
                config.isColorEnabled());
    }

    public Main(QuizDataStorage storage, InputStream inputStream, PrintStream outputStream, boolean colorEnabled) {
        this(QuizConfig.defaults(), storage, inputStream, outputStream, colorEnabled);
    }

    public Main(
            QuizConfig config,
            QuizDataStorage storage,
            InputStream inputStream,
            PrintStream outputStream,
            boolean colorEnabled) {
        this.config = config;
        this.storage = storage;
        this.validator = new InputValidator();
        this.ui = new ConsoleUI(outputStream, colorEnabled);
        this.quizEngine = new QuizEngine(storage, validator, ui);
        this.leaderboard = new Leaderboard(storage);
        this.reader = new QueuedLineReader(inputStream);
    }

    public static void main(String[] args) {
        QuizConfig config;
        try {
            config = QuizConfig.fromArgs(args);
        } catch (InvalidQuizDataException e) {
            System.err.println("[ERROR] " + e.getMessage());
            System.err.println();
            System.err.println(QuizConfig.getUsageText());
            System.exit(1);
            return;
        }

        if (config.isHelpRequested()) {
            System.out.println(QuizConfig.getUsageText());
            return;
        }

        Main app = new Main(config, System.in, System.out);
        if (config.hasCustomArgs()) {
            app.printActiveCliConfig();
        }
        app.run();
    }

    private void printActiveCliConfig() {
        ui.printInfo("CLI configuration applied: questions=" + config.getDefaultQuestionCount()
                + ", time=" + Score.formatSeconds(config.getDefaultTimeLimitSeconds())
                + ", bank=" + config.getQuestionsPath()
                + ", scores=" + config.getScoresPath());
    }

    public void run() {
        boolean running = true;

        while (running) {
            try {
                ui.printMainMenu();
                Integer choice = promptMenuChoice();
                if (choice == null) {
                    ui.printInfo("Input closed. Exiting application.");
                    break;
                }

                switch (choice.intValue()) {
                    case MENU_START_QUIZ:
                        handleStartNewQuiz();
                        break;
                    case MENU_VIEW_LEADERBOARD:
                        handleViewLeaderboard();
                        break;
                    case MENU_VIEW_PREVIOUS_SCORES:
                        handleViewPreviousScores();
                        break;
                    case MENU_VIEW_INSTRUCTIONS:
                        ui.printInstructions(
                                config.getDefaultQuestionCount(),
                                config.getDefaultTimeLimitSeconds());
                        break;
                    case MENU_EXIT:
                        ui.printBanner("THANK YOU FOR PARTICIPATING",
                                "All scores remain archived in " + config.getScoresPath());
                        ui.printLine("  Goodbye! See you at the next competition.");
                        ui.printBlankLine();
                        running = false;
                        break;
                    default:
                        ui.printError("Unexpected menu selection. Please choose 1 to 5.");
                        break;
                }
            } catch (Exception e) {
                ui.printError("An unexpected error occurred: " + e.getMessage());
                ui.printInfo("Returning safely to the main menu.");
            }
        }
    }

    private Integer promptMenuChoice() {
        while (true) {
            ui.printPrompt("Enter your choice: ");
            QueuedLineReader.ReadResult result = reader.readLine();
            if (result.getStatus() == QueuedLineReader.ReadStatus.EOF) {
                return null;
            }
            try {
                return validator.validateMenuChoice(result.getLine(), MENU_START_QUIZ, MENU_EXIT);
            } catch (InvalidQuizDataException e) {
                ui.printError(e.getMessage());
            }
        }
    }

    private void handleStartNewQuiz() {
        ui.printBanner("NEW QUIZ REGISTRATION", "Register Participant & Configure Session");
        ui.printBlankLine();

        LoadResult<Question> questionBankResult;
        try {
            questionBankResult = quizEngine.loadValidatedQuestionBank();
        } catch (QuizException e) {
            ui.printError(e.getMessage());
            ui.printInfo("Please check " + config.getQuestionsPath() + " and try again.");
            return;
        }

        List<Question> validQuestions = questionBankResult.getRecords();
        ui.printInfo("Loaded " + validQuestions.size() + " valid question(s) from the question bank.");
        ui.printBlankLine();

        String fullName = promptParticipantName();
        if (fullName == null) {
            return;
        }

        String attemptId = allocateUniqueSessionAttemptId();
        Participant participant = new Participant(attemptId, fullName);
        ui.printSuccess("Registered " + participant.getFullName()
                + " (Attempt ID: " + participant.getAttemptId() + ")");
        ui.printBlankLine();

        int defaultCount = Math.min(config.getDefaultQuestionCount(), validQuestions.size());
        Integer questionCount = promptOptionalInt(
                "Number of questions (1-" + validQuestions.size() + ") [default " + defaultCount + "]: ",
                1,
                validQuestions.size(),
                defaultCount);
        if (questionCount == null) {
            return;
        }

        int defaultTimeLimit = (int) config.getDefaultTimeLimitSeconds();
        Integer timeLimit = promptOptionalInt(
                "Quiz time limit in seconds (" + QuizConfig.MIN_TIME_LIMIT_SECONDS + "-"
                        + QuizConfig.MAX_TIME_LIMIT_SECONDS + ") [default " + defaultTimeLimit + "]: ",
                QuizConfig.MIN_TIME_LIMIT_SECONDS,
                QuizConfig.MAX_TIME_LIMIT_SECONDS,
                defaultTimeLimit);
        if (timeLimit == null) {
            return;
        }

        try {
            quizEngine.runQuizSession(
                    participant,
                    validQuestions,
                    questionCount.intValue(),
                    timeLimit.longValue(),
                    reader);
        } catch (QuizException e) {
            ui.printError("Quiz could not be completed: " + e.getMessage());
        }
    }

    private String allocateUniqueSessionAttemptId() {
        String candidate;
        try {
            candidate = storage.generateNextAttemptId();
        } catch (QuizException e) {
            ui.printWarning("Could not inspect existing score archive (" + e.getMessage() + ").");
            candidate = String.format(Locale.US, "A%03d", sessionIssuedAttemptIds.size() + 1);
        }

        int nextNumber = parseAttemptNumberOrDefault(candidate, sessionIssuedAttemptIds.size() + 1);
        while (!sessionIssuedAttemptIds.add(candidate.toUpperCase(Locale.ROOT))) {
            nextNumber++;
            candidate = String.format(Locale.US, "A%03d", nextNumber);
        }
        return candidate;
    }

    private static int parseAttemptNumberOrDefault(String attemptId, int fallback) {
        if (attemptId != null && attemptId.length() >= 2
                && (attemptId.charAt(0) == 'A' || attemptId.charAt(0) == 'a')) {
            try {
                return Integer.parseInt(attemptId.substring(1));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String promptParticipantName() {
        while (true) {
            ui.printPrompt("Enter participant full name: ");
            QueuedLineReader.ReadResult result = reader.readLine();
            if (result.getStatus() == QueuedLineReader.ReadStatus.EOF) {
                return null;
            }
            try {
                return validator.validateParticipantName(result.getLine());
            } catch (InvalidQuizDataException e) {
                ui.printError(e.getMessage());
            }
        }
    }

    private Integer promptOptionalInt(String prompt, int min, int max, int defaultValue) {
        while (true) {
            ui.printPrompt(prompt);
            QueuedLineReader.ReadResult result = reader.readLine();
            if (result.getStatus() == QueuedLineReader.ReadStatus.EOF) {
                return null;
            }
            try {
                return validator.validateOptionalIntInRange(result.getLine(), min, max, defaultValue);
            } catch (InvalidQuizDataException e) {
                ui.printError(e.getMessage());
            }
        }
    }

    private void handleViewLeaderboard() {
        try {
            leaderboard.displayTopLeaderboard(ui);
        } catch (QuizException e) {
            ui.printError("Unable to load leaderboard: " + e.getMessage());
        }
    }

    private void handleViewPreviousScores() {
        try {
            LoadResult<Score> result = storage.loadScores();
            ui.printPreviousScores(result.getRecords());
            if (result.hasDiagnostics()) {
                ui.printBlankLine();
                ui.printDiagnostics("Score archive warnings", result.getDiagnostics());
            }
        } catch (QuizException e) {
            ui.printError("Unable to load previous scores: " + e.getMessage());
        }
    }
}
