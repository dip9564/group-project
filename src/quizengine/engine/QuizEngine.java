package quizengine.engine;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import quizengine.exception.InvalidQuizDataException;
import quizengine.exception.QuizException;
import quizengine.io.ConsoleUI;
import quizengine.io.InputValidator;
import quizengine.io.QueuedLineReader;
import quizengine.model.AssessmentItem;
import quizengine.model.LoadResult;
import quizengine.model.Participant;
import quizengine.model.Question;
import quizengine.model.Score;
import quizengine.storage.QuizDataStorage;

/**
 * Core quiz execution engine responsible for selecting non-repeating questions,
 * rendering progress cards, enforcing the multithreaded countdown timer,
 * validating and evaluating answers, calculating scores, and archiving results.
 */
public class QuizEngine {

    public static final int DEFAULT_QUESTION_COUNT = 10;
    public static final long DEFAULT_TIME_LIMIT_SECONDS = QuizTimer.DEFAULT_TIME_LIMIT_SECONDS;

    private final QuizDataStorage storage;
    private final InputValidator validator;
    private final ConsoleUI ui;
    private final Random random;

    public QuizEngine(QuizDataStorage storage, InputValidator validator, ConsoleUI ui) {
        this(storage, validator, ui, new Random());
    }

    public QuizEngine(QuizDataStorage storage, InputValidator validator, ConsoleUI ui, Random random) {
        this.storage = Objects.requireNonNull(storage, "Storage must not be null.");
        this.validator = Objects.requireNonNull(validator, "Validator must not be null.");
        this.ui = Objects.requireNonNull(ui, "ConsoleUI must not be null.");
        this.random = Objects.requireNonNull(random, "Random generator must not be null.");
    }

    public List<Question> selectQuestions(List<Question> questionBank, int requestedCount)
            throws InvalidQuizDataException {
        if (questionBank == null || questionBank.isEmpty()) {
            throw new InvalidQuizDataException("No valid questions are available to start the quiz.");
        }
        if (requestedCount <= 0) {
            throw new InvalidQuizDataException("Question count must be at least 1.");
        }

        List<Question> uniqueBank = new ArrayList<>();
        Set<String> seenPrompts = new HashSet<>();
        for (Question q : questionBank) {
            if (q != null && seenPrompts.add(q.getNormalizedPromptKey())) {
                uniqueBank.add(q);
            }
        }
        if (uniqueBank.isEmpty()) {
            throw new InvalidQuizDataException("No valid questions are available to start the quiz.");
        }

        Collections.shuffle(uniqueBank, random);
        int actualCount = Math.min(requestedCount, uniqueBank.size());
        return Collections.unmodifiableList(new ArrayList<>(uniqueBank.subList(0, actualCount)));
    }

    public LoadResult<Question> loadValidatedQuestionBank() throws QuizException {
        LoadResult<Question> loaded = storage.loadQuestions();
        if (loaded.hasDiagnostics()) {
            ui.printDiagnostics("Question bank diagnostics", loaded.getDiagnostics());
            ui.printBlankLine();
        }
        if (loaded.isEmpty()) {
            throw new InvalidQuizDataException(
                    "Cannot start quiz: 0 valid questions were found in the question bank.");
        }
        return loaded;
    }

    public Score runQuizSession(
            Participant participant,
            List<Question> questionBank,
            int questionCount,
            long timeLimitSeconds,
            QueuedLineReader reader) throws QuizException {
        Objects.requireNonNull(participant, "Participant must not be null.");
        Objects.requireNonNull(reader, "Input reader must not be null.");

        List<Question> selectedQuestions = selectQuestions(questionBank, questionCount);
        int totalQuestions = selectedQuestions.size();

        QuizTimer timer = new QuizTimer(timeLimitSeconds);
        ui.printSessionOverview(participant, totalQuestions, timeLimitSeconds);

        int correctCount = 0;
        int incorrectCount = 0;
        int unansweredCount = 0;
        boolean timedOut = false;

        timer.start();

        try {
            for (int i = 0; i < totalQuestions; i++) {
                if (timer.isTimeExpired()) {
                    timedOut = true;
                    unansweredCount += (totalQuestions - i);
                    ui.printBlankLine();
                    ui.printWarning("Time limit reached! Remaining " + (totalQuestions - i)
                            + " question(s) marked as unanswered.");
                    break;
                }

                AssessmentItem currentItem = selectedQuestions.get(i);
                ui.printQuestionCard(
                        currentItem,
                        i + 1,
                        totalQuestions,
                        correctCount,
                        i,
                        timer.getRemainingSeconds(),
                        timer.getElapsedSeconds(),
                        timer.isLowTimeAlert());

                boolean answeredThisQuestion = false;
                boolean inputEnded = false;

                while (!answeredThisQuestion) {
                    long remainingMillis = timer.getRemainingMillis();
                    if (remainingMillis <= 0L) {
                        timedOut = true;
                        break;
                    }

                    ui.printPrompt("Your answer (A/B/C/D): ");
                    QueuedLineReader.ReadResult result = reader.readLineWithTimeout(remainingMillis);

                    if (result.getStatus() == QueuedLineReader.ReadStatus.TIMEOUT || timer.isTimeExpired()) {
                        timedOut = true;
                        ui.printBlankLine();
                        break;
                    }

                    if (result.getStatus() == QueuedLineReader.ReadStatus.EOF) {
                        inputEnded = true;
                        ui.printBlankLine();
                        ui.printWarning("Input stream ended unexpectedly. Ending quiz session.");
                        break;
                    }

                    try {
                        char normalizedChoice = validator.validateAnswerChoice(result.getLine());
                        boolean isCorrect = currentItem.evaluateAnswer(String.valueOf(normalizedChoice));
                        if (isCorrect) {
                            correctCount++;
                            ui.printAnswerFeedback(true, currentItem.getCorrectAnswerDisplay());
                        } else {
                            incorrectCount++;
                            ui.printAnswerFeedback(false, currentItem.getCorrectAnswerDisplay());
                        }
                        answeredThisQuestion = true;
                    } catch (InvalidQuizDataException e) {
                        ui.printError(e.getMessage());
                    }
                }

                if (timedOut) {
                    int remaining = totalQuestions - i;
                    unansweredCount += remaining;
                    ui.printWarning("Time expired! " + remaining
                            + " remaining question(s) marked as unanswered.");
                    break;
                }
                if (inputEnded) {
                    int remaining = totalQuestions - i;
                    unansweredCount += remaining;
                    break;
                }
            }
        } finally {
            timer.stop();
        }

        long recordedDuration = timedOut ? timer.getTimeLimitSeconds() : timer.getRecordedDurationSeconds();

        Score finalScore = new Score(
                participant.getAttemptId(),
                participant.getFullName(),
                Integer.valueOf(totalQuestions),
                Integer.valueOf(correctCount),
                Integer.valueOf(incorrectCount),
                Integer.valueOf(unansweredCount),
                recordedDuration,
                LocalDateTime.now());

        boolean saved = false;
        try {
            storage.appendScore(finalScore);
            saved = true;
        } catch (QuizException e) {
            ui.printError("Could not archive your score: " + e.getMessage());
        }

        ui.printScoreSummary(finalScore, saved, timedOut);
        return finalScore;
    }
}
