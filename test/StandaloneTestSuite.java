package quizengine.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import quizengine.app.Main;
import quizengine.app.QuizConfig;
import quizengine.engine.Leaderboard;
import quizengine.engine.QuizEngine;
import quizengine.engine.QuizTimer;
import quizengine.exception.InvalidQuizDataException;
import quizengine.exception.QuizException;
import quizengine.exception.QuizStorageException;
import quizengine.io.ConsoleUI;
import quizengine.io.InputValidator;
import quizengine.io.QueuedLineReader;
import quizengine.model.AssessmentItem;
import quizengine.model.LoadResult;
import quizengine.model.Participant;
import quizengine.model.Question;
import quizengine.model.Score;
import quizengine.storage.FileManager;
import quizengine.storage.QuizDataStorage;

/**
 * Independent core-Java automated test runner (40 unit, concurrency, CLI-arg,
 * and integration test cases) using standard Java SE APIs only (no Maven/JUnit).
 *
 * <p>Uses isolated temporary directories for all file I/O tests so real workspace
 * archives ({@code data/scores.txt}) are never modified.</p>
 */
public class StandaloneTestSuite {

    @FunctionalInterface
    private interface TestAction {
        void execute(Path tempDir) throws Exception;
    }

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=========================================================================");
        System.out.println("  INTERACTIVE QUIZ COMPETITION ENGINE - CORE JAVA TEST SUITE (40 CASES)");
        System.out.println("=========================================================================");

        runTest("TC01", "Question object creation & invariants", StandaloneTestSuite::tc01QuestionCreation);
        runTest("TC02", "Question parsing via String tokenization", StandaloneTestSuite::tc02QuestionParsing);
        runTest("TC03", "Correct answer evaluation", StandaloneTestSuite::tc03CorrectAnswer);
        runTest("TC04", "Incorrect answer evaluation", StandaloneTestSuite::tc04IncorrectAnswer);
        runTest("TC05", "Lowercase answer normalization", StandaloneTestSuite::tc05LowercaseNormalization);
        runTest("TC06", "Invalid answer rejection", StandaloneTestSuite::tc06InvalidAnswerRejection);
        runTest("TC07", "Score calculation & wrapper classes", StandaloneTestSuite::tc07ScoreCalculation);
        runTest("TC08", "Percentage calculation & 2-decimal formatting", StandaloneTestSuite::tc08PercentageCalculation);
        runTest("TC09", "Zero-question protection (no div-by-zero)", StandaloneTestSuite::tc09ZeroQuestionProtection);
        runTest("TC10", "Timer duration & countdown calculation", StandaloneTestSuite::tc10TimerDuration);
        runTest("TC11", "Dynamic question loading (data/questions.txt)", StandaloneTestSuite::tc11QuestionFileLoading);
        runTest("TC12", "Missing question file handling", StandaloneTestSuite::tc12MissingQuestionFile);
        runTest("TC13", "Malformed, duplicate & empty question handling", StandaloneTestSuite::tc13MalformedQuestions);
        runTest("TC14", "Score file auto-creation with CSV header", StandaloneTestSuite::tc14ScoreFileCreation);
        runTest("TC15", "Score file appending & CSV field escaping", StandaloneTestSuite::tc15ScoreFileAppending);
        runTest("TC16", "Previous score retrieval & corrupt row recovery", StandaloneTestSuite::tc16PreviousScoreRetrieval);
        runTest("TC17", "Leaderboard sorting & Top 5 cutoff", StandaloneTestSuite::tc17LeaderboardSorting);
        runTest("TC18", "Leaderboard tie-breaking & shared ranks", StandaloneTestSuite::tc18LeaderboardTieBreaking);
        runTest("TC19", "Empty leaderboard handling", StandaloneTestSuite::tc19EmptyLeaderboard);
        runTest("TC20", "Invalid menu input rejection", StandaloneTestSuite::tc20InvalidMenuInput);
        runTest("TC21", "Participant name validation", StandaloneTestSuite::tc21ParticipantNameValidation);
        runTest("TC22", "Non-repeating question selection", StandaloneTestSuite::tc22NonRepeatingSelection);
        runTest("TC23", "Strict non-bypassable quiz timeout enforcement", StandaloneTestSuite::tc23StrictTimeout);
        runTest("TC24", "End-to-end integration & restart persistence", StandaloneTestSuite::tc24EndToEndIntegration);
        runTest("TC25", "File permission error handling (read-only)", StandaloneTestSuite::tc25ReadOnlyPermission);
        runTest("TC26", "Append to scores file lacking trailing newline", StandaloneTestSuite::tc26NoTrailingNewlineAppend);
        runTest("TC27", "UTF-8 BOM & leading blank lines in data files", StandaloneTestSuite::tc27Utf8BomAndBlankLines);
        runTest("TC28", "Corrupted CSV quotes & tampered percentages", StandaloneTestSuite::tc28CorruptedCsvQuoting);
        runTest("TC29", "Score percentage rejects correct > total", StandaloneTestSuite::tc29PercentageBoundsCheck);
        runTest("TC30", "Duplicate attempt IDs & malformed ID collision", StandaloneTestSuite::tc30DuplicateAndMalformedAttemptIds);
        runTest("TC31", "Duplicate participants across multi-run & restart", StandaloneTestSuite::tc31DuplicateParticipantsAcrossRestart);
        runTest("TC32", "Participant name punctuation-abuse rejection", StandaloneTestSuite::tc32NamePunctuationValidation);
        runTest("TC33", "Malformed question edge cases & invalid UTF-8", StandaloneTestSuite::tc33MalformedQuestionEdgeCases);
        runTest("TC34", "QuizEngine deduplicates caller question list", StandaloneTestSuite::tc34EngineDeduplicatesBank);
        runTest("TC35", "QuizTimer rejects overflow & invalid limits", StandaloneTestSuite::tc35TimerOverflowGuard);
        runTest("TC36", "Question String[] options array & defensive copy", StandaloneTestSuite::tc36QuestionArrayEncapsulation);
        runTest("TC37", "Command-line arguments parsing (QuizConfig)", StandaloneTestSuite::tc37CommandLineArgsParsing);
        runTest("TC38", "Background countdown monitor Thread lifecycle", StandaloneTestSuite::tc38BackgroundTimerThread);
        runTest("TC39", "End-to-end execution configured via CLI args", StandaloneTestSuite::tc39EndToEndWithCliArgs);
        runTest("TC40", "Cross-package inheritance & polymorphism checks", StandaloneTestSuite::tc40CrossPackagePolymorphism);

        System.out.println("=========================================================================");
        System.out.printf("  SUMMARY: Executed = %d | Passed = %d | Failed = %d%n",
                passed + failed, passed, failed);
        System.out.println("=========================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void runTest(String id, String description, TestAction action) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("quiz-test-" + id + "-");
            action.execute(tempDir);
            passed++;
            System.out.printf("  [PASS] %-5s : %s%n", id, description);
        } catch (Throwable t) {
            failed++;
            System.out.printf("  [FAIL] %-5s : %s -> %s%n", id, description, t.getMessage());
            t.printStackTrace(System.out);
        } finally {
            deleteQuietly(tempDir);
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                path.toFile().setWritable(true);
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError("Assertion failed (expected false): " + message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " (expected: <" + expected + ">, actual: <" + actual + ">)");
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void assertThrows(Class<? extends Throwable> expectedType, ThrowingRunnable runnable, String msg) {
        try {
            runnable.run();
        } catch (Throwable t) {
            if (expectedType.isInstance(t)) {
                return;
            }
            throw new AssertionError(msg + " (expected " + expectedType.getSimpleName()
                    + " but caught " + t.getClass().getSimpleName() + ": " + t.getMessage() + ")");
        }
        throw new AssertionError(msg + " (expected " + expectedType.getSimpleName() + " but nothing was thrown)");
    }

    // ==================== TEST IMPLEMENTATIONS ====================

    private static void tc01QuestionCreation(Path tempDir) {
        String[] opts = {"Programming Language", "Operating System", "Database", "Browser"};
        Question q = new Question("What is Java?", opts, 'A');
        assertEquals("What is Java?", q.getPrompt(), "Prompt mismatch");
        assertEquals(4, q.getOptionCount(), "Option count");
        assertEquals('A', q.getCorrectOption(), "Correct option");
        assertEquals("A) Programming Language", q.getCorrectAnswerDisplay(), "Display format");

        assertThrows(IllegalArgumentException.class, () -> new Question("", opts, 'A'), "Blank prompt");
        assertThrows(IllegalArgumentException.class,
                () -> new Question("Too few?", new String[]{"A", "B"}, 'A'), "Fewer than 4 options");
        assertThrows(IllegalArgumentException.class, () -> new Question("Bad key?", opts, 'E'), "Invalid key");
    }

    private static void tc02QuestionParsing(Path tempDir) throws QuizException {
        FileManager fm = new FileManager(tempDir.resolve("q.txt"), tempDir.resolve("s.txt"));
        List<String> block = Arrays.asList(
                "Which keyword creates an object?",
                "A) class", "B) new", "C) static", "D) void", "B");
        Question parsed = fm.parseQuestionBlock(block);
        assertEquals("Which keyword creates an object?", parsed.getPrompt(), "Parsed prompt");
        assertEquals("new", parsed.getOptionText('B'), "Parsed option B");
        assertEquals('B', parsed.getCorrectOption(), "Parsed answer");

        assertThrows(InvalidQuizDataException.class,
                () -> fm.parseQuestionBlock(Arrays.asList("Short?", "A) One")), "Short block rejected");
    }

    private static void tc03CorrectAnswer(Path tempDir) {
        Question q = new Question("Which keyword creates an object?",
                new String[]{"class", "new", "static", "void"}, 'B');
        assertTrue(q.evaluateAnswer("B"), "Exact uppercase answer should evaluate true");
    }

    private static void tc04IncorrectAnswer(Path tempDir) {
        Question q = new Question("Which keyword creates an object?",
                new String[]{"class", "new", "static", "void"}, 'B');
        assertFalse(q.evaluateAnswer("A"), "Option A should be false");
        assertFalse(q.evaluateAnswer("C"), "Option C should be false");
        assertFalse(q.evaluateAnswer("D"), "Option D should be false");
    }

    private static void tc05LowercaseNormalization(Path tempDir) throws InvalidQuizDataException {
        InputValidator v = new InputValidator();
        assertEquals('A', v.validateAnswerChoice("a"), "Lowercase a");
        assertEquals('B', v.validateAnswerChoice(" b "), "Lowercase b with spaces");
        assertEquals('C', v.validateAnswerChoice("c"), "Lowercase c");
        assertEquals('D', v.validateAnswerChoice("d"), "Lowercase d");

        Question q = new Question("What is Java?",
                new String[]{"Programming Language", "OS", "DB", "Browser"}, 'A');
        assertTrue(q.evaluateAnswer(" a "), "Normalized lowercase answer evaluates true");
    }

    private static void tc06InvalidAnswerRejection(Path tempDir) {
        InputValidator v = new InputValidator();
        assertThrows(InvalidQuizDataException.class, () -> v.validateAnswerChoice("E"), "E rejected");
        assertThrows(InvalidQuizDataException.class, () -> v.validateAnswerChoice("1"), "1 rejected");
        assertThrows(InvalidQuizDataException.class, () -> v.validateAnswerChoice(""), "Blank rejected");
        assertThrows(InvalidQuizDataException.class, () -> v.validateAnswerChoice("AB"), "Multi-char rejected");
    }

    private static void tc07ScoreCalculation(Path tempDir) {
        Score s = new Score("A001", "Dip Mondal",
                Integer.valueOf(10), Integer.valueOf(8), Integer.valueOf(1), Integer.valueOf(1),
                135L, LocalDateTime.of(2026, 11, 18, 10, 30, 0));
        assertEquals(Integer.valueOf(10), s.getTotalQuestions(), "Total");
        assertEquals(Integer.valueOf(8), s.getCorrectAnswers(), "Correct");
        assertEquals(Integer.valueOf(1), s.getIncorrectAnswers(), "Incorrect");
        assertEquals(Integer.valueOf(1), s.getUnansweredQuestions(), "Unanswered");
        assertEquals("02:15", s.getFormattedDuration(), "Formatted duration");
    }

    private static void tc08PercentageCalculation(Path tempDir) {
        assertEquals(Double.valueOf(80.0), Score.calculatePercentage(8, 10), "8/10 percentage");
        assertEquals(Double.valueOf(66.67), Score.calculatePercentage(2, 3), "2/3 percentage");
        Score s = new Score("A002", "Test", 3, 2, 1, 0, 45L, LocalDateTime.now());
        assertEquals("66.67", s.getFormattedPercentage(), "2-decimal formatting");
    }

    private static void tc09ZeroQuestionProtection(Path tempDir) {
        Double pct = Score.calculatePercentage(0, 0);
        assertEquals(Double.valueOf(0.0), pct, "Zero total safely yields 0.0");
        assertFalse(Double.isNaN(pct), "Must not be NaN");
        assertFalse(Double.isInfinite(pct), "Must not be Infinite");
        Score s = new Score("A000", "Zero Attempt", 0, 0, 0, 0, 0L, LocalDateTime.now());
        assertEquals("0.00", s.getFormattedPercentage(), "Formatted zero percentage");
    }

    private static void tc10TimerDuration(Path tempDir) {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        QuizTimer timer = new QuizTimer(120L, clock::get);
        timer.start();
        try {
            clock.addAndGet(75_000_000_000L);
            assertEquals(75L, timer.getElapsedSeconds(), "Elapsed seconds");
            assertEquals("01:15", timer.getFormattedElapsedTime(), "Elapsed MM:SS");
            assertEquals(45L, timer.getRemainingSeconds(), "Remaining seconds");
            assertFalse(timer.isTimeExpired(), "Not expired at 75s");

            clock.addAndGet(50_000_000_000L);
            assertTrue(timer.isTimeExpired(), "Expired at 125s");
            timer.stop();
            clock.addAndGet(30_000_000_000L);
            assertEquals(125L, timer.getElapsedSeconds(), "Frozen after stop");
        } finally {
            timer.stop();
        }
    }

    private static void tc11QuestionFileLoading(Path tempDir) throws QuizException {
        FileManager fm = new FileManager(Paths.get("data", "questions.txt"), tempDir.resolve("s.txt"));
        LoadResult<Question> res = fm.loadQuestions();
        assertTrue(res.getRecords().size() >= 30, "At least 30 valid questions loaded");
        assertFalse(res.hasDiagnostics(), "Zero malformed diagnostics expected in data/questions.txt");
    }

    private static void tc12MissingQuestionFile(Path tempDir) {
        FileManager fm = new FileManager(tempDir.resolve("missing.txt"), tempDir.resolve("s.txt"));
        assertThrows(QuizStorageException.class, fm::loadQuestions, "Missing file raises QuizStorageException");
    }

    private static void tc13MalformedQuestions(Path tempDir) throws IOException, QuizException {
        Path file = tempDir.resolve("mixed.txt");
        Files.writeString(file, String.join("\n",
                "What is Java?",
                "A) Programming Language", "B) OS", "C) DB", "D) Browser", "A",
                "",
                "What is Java?",
                "A) Programming Language", "B) OS", "C) DB", "D) Browser", "A",
                "",
                "Bad answer key?",
                "A) 1", "B) 2", "C) 3", "D) 4", "X",
                "",
                "Missing options?",
                "A) Only one"), StandardCharsets.UTF_8);

        FileManager fm = new FileManager(file, tempDir.resolve("s.txt"));
        LoadResult<Question> res = fm.loadQuestions();
        assertEquals(1, res.getRecords().size(), "1 valid question retained");
        assertEquals(3, res.getDiagnostics().size(), "3 diagnostics reported");

        Path emptyFile = tempDir.resolve("empty.txt");
        Files.writeString(emptyFile, "   \n\n", StandardCharsets.UTF_8);
        FileManager emptyFm = new FileManager(emptyFile, tempDir.resolve("s.txt"));
        assertThrows(InvalidQuizDataException.class, emptyFm::loadQuestions, "Empty file raises InvalidQuizDataException");
    }

    private static void tc14ScoreFileCreation(Path tempDir) throws QuizException, IOException {
        Path scoresFile = tempDir.resolve("sub/scores.txt");
        FileManager fm = new FileManager(tempDir.resolve("q.txt"), scoresFile);
        fm.appendScore(new Score("A001", "Dip Mondal", 10, 8, 2, 0, 120L,
                LocalDateTime.of(2026, 11, 18, 10, 30, 0)));

        assertTrue(Files.exists(scoresFile), "Scores file created");
        List<String> lines = Files.readAllLines(scoresFile, StandardCharsets.UTF_8);
        assertEquals(2, lines.size(), "Header + 1 row");
        assertEquals(FileManager.SCORES_CSV_HEADER, lines.get(0), "CSV header matches");
    }

    private static void tc15ScoreFileAppending(Path tempDir) throws QuizException {
        Path scoresFile = tempDir.resolve("append.txt");
        FileManager fm = new FileManager(tempDir.resolve("q.txt"), scoresFile);
        fm.appendScore(new Score("A001", "Alice Roy", 10, 9, 1, 0, 110L,
                LocalDateTime.of(2026, 11, 18, 10, 0, 0)));
        fm.appendScore(new Score("A002", "Bob, \"The Dev\" Sen", 10, 7, 3, 0, 140L,
                LocalDateTime.of(2026, 11, 18, 10, 15, 0)));

        LoadResult<Score> loaded = fm.loadScores();
        assertEquals(2, loaded.getRecords().size(), "Both records retained");
        assertEquals("Alice Roy", loaded.getRecords().get(0).getParticipantName(), "First record");
        assertEquals("Bob, \"The Dev\" Sen", loaded.getRecords().get(1).getParticipantName(), "Escaped CSV round-trip");
        assertEquals("A003", fm.generateNextAttemptId(), "Next sequential ID");
    }

    private static void tc16PreviousScoreRetrieval(Path tempDir) throws IOException, QuizException {
        Path scoresFile = tempDir.resolve("corrupt.txt");
        Files.writeString(scoresFile, String.join("\n",
                FileManager.SCORES_CSV_HEADER,
                "A001,Dip Mondal,8,10,80.00,120,2026-11-18T10:30:00",
                "MALFORMED_LINE",
                "A002,Impossible,15,10,150.00,90,2026-11-18T11:00:00",
                "A003,Neha Paul,10,10,100.00,95,2026-11-18T11:15:00"), StandardCharsets.UTF_8);

        FileManager fm = new FileManager(tempDir.resolve("q.txt"), scoresFile);
        LoadResult<Score> loaded = fm.loadScores();
        assertEquals(2, loaded.getRecords().size(), "Valid rows retrieved");
        assertEquals(2, loaded.getDiagnostics().size(), "Corrupt rows reported");
    }

    private static void tc17LeaderboardSorting(Path tempDir) throws QuizException {
        Path scoresFile = tempDir.resolve("lb.txt");
        FileManager fm = new FileManager(tempDir.resolve("q.txt"), scoresFile);
        LocalDateTime t = LocalDateTime.of(2026, 11, 18, 9, 0, 0);

        fm.appendScore(new Score("A001", "P1", 10, 5, 5, 0, 100L, t));
        fm.appendScore(new Score("A002", "P2", 5, 5, 0, 0, 60L, t.plusMinutes(1)));
        fm.appendScore(new Score("A003", "P3", 10, 10, 0, 0, 120L, t.plusMinutes(2)));
        fm.appendScore(new Score("A004", "P4", 10, 8, 2, 0, 90L, t.plusMinutes(3)));
        fm.appendScore(new Score("A005", "P5", 10, 9, 1, 0, 100L, t.plusMinutes(4)));
        fm.appendScore(new Score("A006", "P6", 10, 7, 3, 0, 80L, t.plusMinutes(5)));

        Leaderboard lb = new Leaderboard(fm);
        Leaderboard.LeaderboardSnapshot snap = lb.getTopAttempts();
        assertEquals(5, snap.getTopEntries().size(), "Top 5 cutoff");
        assertEquals("P3", snap.getTopEntries().get(0).getScore().getParticipantName(), "Rank 1");
        assertEquals("P2", snap.getTopEntries().get(1).getScore().getParticipantName(), "Rank 2");
        assertEquals("P5", snap.getTopEntries().get(2).getScore().getParticipantName(), "Rank 3");
        assertEquals("P4", snap.getTopEntries().get(3).getScore().getParticipantName(), "Rank 4");
        assertEquals("P6", snap.getTopEntries().get(4).getScore().getParticipantName(), "Rank 5");
    }

    private static void tc18LeaderboardTieBreaking(Path tempDir) {
        LocalDateTime t = LocalDateTime.of(2026, 11, 18, 12, 0, 0);
        List<Score> scores = Arrays.asList(
                new Score("A001", "Slow", 10, 9, 1, 0, 150L, t),
                new Score("A002", "FastOne", 10, 9, 1, 0, 100L, t.plusMinutes(1)),
                new Score("A003", "FastTwo", 10, 9, 1, 0, 100L, t.plusMinutes(2)));
        List<Leaderboard.RankedEntry> ranked = Leaderboard.rankScores(scores, 5);
        assertEquals(1, ranked.get(0).getRank(), "FastOne rank");
        assertEquals(1, ranked.get(1).getRank(), "FastTwo tied rank 1");
        assertEquals(3, ranked.get(2).getRank(), "Slow gets competition rank 3");
    }

    private static void tc19EmptyLeaderboard(Path tempDir) throws QuizException {
        FileManager fm = new FileManager(tempDir.resolve("q.txt"), tempDir.resolve("absent.txt"));
        Leaderboard lb = new Leaderboard(fm);
        assertTrue(lb.getTopAttempts().isEmpty(), "Snapshot empty");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        lb.displayTopLeaderboard(new ConsoleUI(new PrintStream(baos, true, StandardCharsets.UTF_8), false));
        assertTrue(baos.toString(StandardCharsets.UTF_8).contains("No quiz scores recorded yet"), "Empty message shown");
    }

    private static void tc20InvalidMenuInput(Path tempDir) throws InvalidQuizDataException {
        InputValidator v = new InputValidator();
        assertEquals(Integer.valueOf(1), v.validateMenuChoice("1", 1, 5), "Valid 1");
        assertEquals(Integer.valueOf(5), v.validateMenuChoice(" 5 ", 1, 5), "Valid 5");
        assertThrows(InvalidQuizDataException.class, () -> v.validateMenuChoice("abc", 1, 5), "Non-numeric");
        assertThrows(InvalidQuizDataException.class, () -> v.validateMenuChoice("0", 1, 5), "Below range");
        assertThrows(InvalidQuizDataException.class, () -> v.validateMenuChoice("6", 1, 5), "Above range");
        assertThrows(InvalidQuizDataException.class, () -> v.validateMenuChoice("", 1, 5), "Empty");
    }

    private static void tc21ParticipantNameValidation(Path tempDir) throws InvalidQuizDataException {
        InputValidator v = new InputValidator();
        assertEquals("Dip Mondal", v.validateParticipantName("  Dip   Mondal  "), "Whitespace collapsed");
        assertEquals("Dr. A. K. Sen", v.validateParticipantName("Dr. A. K. Sen"), "Periods allowed");
        assertThrows(InvalidQuizDataException.class, () -> v.validateParticipantName(""), "Empty rejected");
        assertThrows(InvalidQuizDataException.class, () -> v.validateParticipantName("User123"), "Digits rejected");
        assertThrows(InvalidQuizDataException.class, () -> v.validateParticipantName("Bad@Name!"), "Symbols rejected");
    }

    private static void tc22NonRepeatingSelection(Path tempDir) throws QuizException {
        FileManager fm = new FileManager(Paths.get("data", "questions.txt"), tempDir.resolve("s.txt"));
        List<Question> bank = fm.loadQuestions().getRecords();
        QuizEngine engine = new QuizEngine(fm, new InputValidator(),
                new ConsoleUI(new PrintStream(new ByteArrayOutputStream()), false), new Random(42L));
        List<Question> selected = engine.selectQuestions(bank, 15);
        assertEquals(15, selected.size(), "Selected 15");
        Set<String> keys = new HashSet<>();
        for (Question q : selected) {
            keys.add(q.getNormalizedPromptKey());
        }
        assertEquals(15, keys.size(), "Zero duplicates in single attempt");
    }

    private static void tc23StrictTimeout(Path tempDir) throws Exception {
        FileManager fm = new FileManager(Paths.get("data", "questions.txt"), tempDir.resolve("timeout_scores.txt"));
        List<Question> bank = fm.loadQuestions().getRecords().subList(0, 3);
        ConsoleUI ui = new ConsoleUI(new PrintStream(new ByteArrayOutputStream()), false);
        QuizEngine engine = new QuizEngine(fm, new InputValidator(), ui, new Random(1L));

        PipedOutputStream pos = new PipedOutputStream();
        PipedInputStream pis = new PipedInputStream(pos);
        QueuedLineReader reader = new QueuedLineReader(pis);

        long startMs = System.currentTimeMillis();
        Score res = engine.runQuizSession(new Participant("A001", "Timeout Tester"), bank, 3, 1L, reader);
        long elapsedMs = System.currentTimeMillis() - startMs;

        pos.close();
        pis.close();

        assertTrue(elapsedMs < 3000L, "Timeout did not hang");
        assertEquals(Integer.valueOf(0), res.getCorrectAnswers(), "0 correct on timeout");
        assertEquals(Integer.valueOf(3), res.getUnansweredQuestions(), "3 unanswered on timeout");
    }

    private static void tc24EndToEndIntegration(Path tempDir) throws IOException {
        Path testQuestions = tempDir.resolve("e2e_q.txt");
        Files.writeString(testQuestions, String.join("\n",
                "What is Java?",
                "A) Programming Language", "B) Operating System", "C) Database", "D) Browser", "A",
                "",
                "Which keyword creates an object?",
                "A) class", "B) new", "C) static", "D) void", "B"), StandardCharsets.UTF_8);
        Path testScores = tempDir.resolve("e2e_s.txt");

        String session1Input = String.join("\n",
                "abc", "9", "1", "Bad123", "Dip Mondal", "2", "60", "Z", "a", "B", "5") + "\n";
        ByteArrayOutputStream out1 = new ByteArrayOutputStream();
        new Main(
                new FileManager(testQuestions, testScores),
                new ByteArrayInputStream(session1Input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out1, true, StandardCharsets.UTF_8),
                false).run();

        String s1 = out1.toString(StandardCharsets.UTF_8);
        assertTrue(s1.contains("QUIZ COMPLETED"), "Quiz completed banner");
        assertTrue(s1.contains("YOUR RESULT HAS BEEN SAVED"), "Saved confirmation");

        String session2Input = String.join("\n", "2", "3", "4", "5") + "\n";
        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        new Main(
                new FileManager(testQuestions, testScores),
                new ByteArrayInputStream(session2Input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out2, true, StandardCharsets.UTF_8),
                false).run();

        String s2 = out2.toString(StandardCharsets.UTF_8);
        assertTrue(s2.contains("TOP 5 LEADERBOARD"), "Leaderboard shown after restart");
        assertTrue(s2.contains("Dip Mondal"), "Saved participant persisted across restart");
        assertTrue(s2.contains("PREVIOUS QUIZ SCORES"), "Previous scores shown");
        assertTrue(s2.contains("QUIZ INSTRUCTIONS"), "Instructions shown");
    }

    private static void tc25ReadOnlyPermission(Path tempDir) throws IOException {
        Path roScores = tempDir.resolve("readonly.txt");
        Files.writeString(roScores, FileManager.SCORES_CSV_HEADER + "\n", StandardCharsets.UTF_8);
        boolean madeReadOnly = roScores.toFile().setReadOnly();
        try {
            if (madeReadOnly && !Files.isWritable(roScores)) {
                FileManager fm = new FileManager(tempDir.resolve("q.txt"), roScores);
                Score s = new Score("A001", "Perm Tester", 5, 5, 0, 0, 30L, LocalDateTime.now());
                assertThrows(QuizStorageException.class, () -> fm.appendScore(s),
                        "Read-only file throws QuizStorageException");
            }
        } finally {
            roScores.toFile().setWritable(true);
        }
    }

    private static void tc26NoTrailingNewlineAppend(Path tempDir) throws IOException, QuizException {
        Path scoresFile = tempDir.resolve("no_newline.txt");
        String initial = FileManager.SCORES_CSV_HEADER + "\n"
                + "A001,Ananya Roy,9,10,90.00,115,2026-11-15T10:15:00";
        Files.writeString(scoresFile, initial, StandardCharsets.UTF_8);

        FileManager fm = new FileManager(tempDir.resolve("q.txt"), scoresFile);
        fm.appendScore(new Score("A002", "Dip Mondal", 10, 8, 2, 0, 120L,
                LocalDateTime.of(2026, 11, 18, 19, 10, 0)));

        LoadResult<Score> loaded = fm.loadScores();
        assertEquals(2, loaded.getRecords().size(), "Both rows survive without concatenation");
        assertFalse(loaded.hasDiagnostics(), "Zero diagnostics expected");
        assertEquals("A001", loaded.getRecords().get(0).getAttemptId(), "First row preserved");
        assertEquals("A002", loaded.getRecords().get(1).getAttemptId(), "Second row preserved");
    }

    private static void tc27Utf8BomAndBlankLines(Path tempDir) throws IOException, QuizException {
        Path qFile = tempDir.resolve("bom_questions.txt");
        Files.writeString(qFile,
                "\uFEFFWhat is Java?\nA) Language\nB) OS\nC) DB\nD) Browser\nA\n\n"
                        + "What is Java?\nA) Language\nB) OS\nC) DB\nD) Browser\nA\n",
                StandardCharsets.UTF_8);

        Path sFile = tempDir.resolve("bom_scores.txt");
        Files.writeString(sFile,
                "\uFEFF\n\n" + FileManager.SCORES_CSV_HEADER + "\n"
                        + "A001,Ananya Roy,9,10,90.00,115,2026-11-15T10:15:00\n",
                StandardCharsets.UTF_8);

        FileManager fm = new FileManager(qFile, sFile);
        LoadResult<Question> qRes = fm.loadQuestions();
        assertEquals(1, qRes.getRecords().size(), "BOM-stripped duplicate detected");
        assertEquals(1, qRes.getDiagnostics().size(), "1 duplicate diagnostic");

        LoadResult<Score> sRes = fm.loadScores();
        assertEquals(1, sRes.getRecords().size(), "Header after BOM & blank lines skipped");
        assertFalse(sRes.hasDiagnostics(), "Zero false header diagnostics");
    }

    private static void tc28CorruptedCsvQuoting(Path tempDir) throws IOException, QuizException {
        Path sFile = tempDir.resolve("bad_csv.txt");
        Files.writeString(sFile, String.join("\n",
                FileManager.SCORES_CSV_HEADER,
                "A001,\"Dip Mondal\"GARBAGE,8,10,80.00,120,2026-11-18T10:00:00",
                "A002,\"Unclosed quote,8,10,80.00,120,2026-11-18T10:05:00",
                "A003,Tampered Percent,5,10,99.00,100,2026-11-18T10:10:00",
                "A004,Valid User,8,10,80.00,100,2026-11-18T10:15:00"), StandardCharsets.UTF_8);

        FileManager fm = new FileManager(tempDir.resolve("q.txt"), sFile);
        LoadResult<Score> loaded = fm.loadScores();
        assertEquals(1, loaded.getRecords().size(), "Only valid row A004 loaded");
        assertEquals(3, loaded.getDiagnostics().size(), "All 3 corrupted rows diagnosed");
        assertEquals("A004", loaded.getRecords().get(0).getAttemptId(), "Valid row preserved");
    }

    private static void tc29PercentageBoundsCheck(Path tempDir) {
        assertThrows(IllegalArgumentException.class,
                () -> Score.calculatePercentage(12, 10), "correct > total rejected");
        assertEquals(Double.valueOf(0.0), Score.calculatePercentage(null, 10), "Null yields 0.0");
        assertEquals(Double.valueOf(0.0), Score.calculatePercentage(-2, 10), "Negative yields 0.0");
    }

    private static void tc30DuplicateAndMalformedAttemptIds(Path tempDir) throws IOException, QuizException {
        Path sFile = tempDir.resolve("dup_ids.txt");
        Files.writeString(sFile, String.join("\n",
                FileManager.SCORES_CSV_HEADER,
                "A001,First User,8,10,80.00,120,2026-11-18T10:00:00",
                "A001,Duplicate ID User,9,10,90.00,100,2026-11-18T10:05:00",
                "A005,Corrupted Row,99,10,990.00,50,2026-11-18T10:10:00"), StandardCharsets.UTF_8);

        FileManager fm = new FileManager(tempDir.resolve("q.txt"), sFile);
        LoadResult<Score> loaded = fm.loadScores();
        assertEquals(1, loaded.getRecords().size(), "Duplicate ID skipped");
        assertEquals(2, loaded.getDiagnostics().size(), "2 diagnostics");
        assertEquals("A006", fm.generateNextAttemptId(), "Advances past malformed A005");
    }

    private static void tc31DuplicateParticipantsAcrossRestart(Path tempDir) throws IOException, QuizException {
        Path qFile = tempDir.resolve("q.txt");
        Files.writeString(qFile, String.join("\n",
                "What is Java?",
                "A) Programming Language", "B) Operating System", "C) Database", "D) Browser", "A"),
                StandardCharsets.UTF_8);
        Path sFile = tempDir.resolve("dup_participant_scores.txt");

        String input1 = String.join("\n",
                "1", "Dip Mondal", "1", "60", "B",
                "1", "Dip Mondal", "1", "60", "A",
                "5") + "\n";
        new Main(new FileManager(qFile, sFile),
                new ByteArrayInputStream(input1.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(new ByteArrayOutputStream()), false).run();

        String input2 = String.join("\n",
                "1", "Dip Mondal", "1", "60", "A",
                "2", "3", "5") + "\n";
        new Main(new FileManager(qFile, sFile),
                new ByteArrayInputStream(input2.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(new ByteArrayOutputStream()), false).run();

        FileManager verifier = new FileManager(qFile, sFile);
        LoadResult<Score> all = verifier.loadScores();
        assertEquals(3, all.getRecords().size(), "All 3 attempts archived");
        assertEquals("A001", all.getRecords().get(0).getAttemptId(), "ID 1");
        assertEquals("A002", all.getRecords().get(1).getAttemptId(), "ID 2");
        assertEquals("A003", all.getRecords().get(2).getAttemptId(), "ID 3 after restart");
    }

    private static void tc32NamePunctuationValidation(Path tempDir) throws InvalidQuizDataException {
        InputValidator v = new InputValidator();
        assertEquals("Li", v.validateParticipantName("Li"), "2-letter name allowed");
        assertEquals("Dr. A. K. Sen", v.validateParticipantName("Dr. A. K. Sen"), "Initials allowed");
        assertEquals("Mary-Jane O'Connor", v.validateParticipantName("Mary-Jane O'Connor"), "Hyphen & apostrophe");

        assertThrows(InvalidQuizDataException.class, () -> v.validateParticipantName("A."), "Single letter rejected");
        assertThrows(InvalidQuizDataException.class, () -> v.validateParticipantName("A..."), "Repeated dots rejected");
        assertThrows(InvalidQuizDataException.class, () -> v.validateParticipantName("Dip--Mondal"), "Double hyphen rejected");
        assertThrows(InvalidQuizDataException.class, () -> v.validateParticipantName("Dip . Mondal"), "Standalone dot rejected");
    }

    private static void tc33MalformedQuestionEdgeCases(Path tempDir) throws IOException, QuizException {
        Path qFile = tempDir.resolve("edge_q.txt");
        Files.writeString(qFile, String.join("\n",
                "Duplicate options question?",
                "A) Stack", "B) stack", "C) Queue", "D) Heap", "A",
                "",
                "Wrong option label order?",
                "B) First", "A) Second", "C) Third", "D) Fourth", "A",
                "",
                "Oversized block?",
                "A) One", "B) Two", "C) Three", "D) Four", "A", "Extra Line",
                "",
                "What executes Java bytecode?",
                "A) Compiler (javac)", "B) Virtual Machine (JVM)", "C) Linker", "D) Assembler", "B"),
                StandardCharsets.UTF_8);

        FileManager fm = new FileManager(qFile, tempDir.resolve("s.txt"));
        LoadResult<Question> res = fm.loadQuestions();
        assertEquals(1, res.getRecords().size(), "Only valid question loaded");
        assertEquals(3, res.getDiagnostics().size(), "All 3 malformed blocks diagnosed");
        assertEquals("Virtual Machine (JVM)", res.getRecords().get(0).getOptionText('B'), "Parentheses preserved");

        Path binFile = tempDir.resolve("binary.txt");
        Files.write(binFile, new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0x80, (byte) 0x81});
        FileManager binFm = new FileManager(binFile, tempDir.resolve("s.txt"));
        assertThrows(QuizStorageException.class, binFm::loadQuestions, "Binary invalid UTF-8 throws QuizStorageException");
    }

    private static void tc34EngineDeduplicatesBank(Path tempDir) throws QuizException {
        Question q1 = new Question("What is Java?",
                new String[]{"Language", "OS", "DB", "Browser"}, 'A');
        Question q1Dup = new Question("  what  is  java? ",
                new String[]{"Language", "OS", "DB", "Browser"}, 'A');
        Question q2 = new Question("Which keyword creates an object?",
                new String[]{"class", "new", "static", "void"}, 'B');

        FileManager fm = new FileManager(tempDir.resolve("q.txt"), tempDir.resolve("s.txt"));
        QuizEngine engine = new QuizEngine(fm, new InputValidator(),
                new ConsoleUI(new PrintStream(new ByteArrayOutputStream()), false), new Random(7L));

        List<Question> selected = engine.selectQuestions(Arrays.asList(q1, null, q1Dup, q2), 10);
        assertEquals(2, selected.size(), "Duplicates and nulls filtered");
    }

    private static void tc35TimerOverflowGuard(Path tempDir) {
        assertThrows(IllegalArgumentException.class, () -> new QuizTimer(0L), "Zero limit rejected");
        assertThrows(IllegalArgumentException.class, () -> new QuizTimer(-10L), "Negative limit rejected");
        assertThrows(IllegalArgumentException.class, () -> new QuizTimer(Long.MAX_VALUE), "Overflow limit rejected");
    }

    private static void tc36QuestionArrayEncapsulation(Path tempDir) {
        String[] inputOptions = {"Java", "Python", "C++", "Rust"};
        Question q = new Question("Which language runs on the JVM?", inputOptions, 'A');

        // Mutating caller's input array must not mutate Question internal state
        inputOptions[0] = "MUTATED";
        assertEquals("Java", q.getOptionText('A'), "Internal array isolated from input array mutation");

        // Mutating returned String[] copy must not mutate Question internal state
        String[] copy = q.getOptions();
        assertEquals(4, copy.length, "Returned String[] length is 4");
        copy[0] = "TAMPERED";
        assertEquals("Java", q.getOptionText('A'), "Internal array isolated from getter array mutation");
    }

    private static void tc37CommandLineArgsParsing(Path tempDir) throws InvalidQuizDataException {
        QuizConfig cfg = QuizConfig.fromArgs(new String[]{
                "--questions", "15",
                "--time", "180",
                "--bank", tempDir.resolve("custom_q.txt").toString(),
                "--scores", tempDir.resolve("custom_s.txt").toString(),
                "--no-color"
        });
        assertEquals(15, cfg.getDefaultQuestionCount(), "Parsed --questions");
        assertEquals(180L, cfg.getDefaultTimeLimitSeconds(), "Parsed --time");
        assertEquals(tempDir.resolve("custom_q.txt"), cfg.getQuestionsPath(), "Parsed --bank");
        assertEquals(tempDir.resolve("custom_s.txt"), cfg.getScoresPath(), "Parsed --scores");
        assertFalse(cfg.isColorEnabled(), "Parsed --no-color");
        assertTrue(cfg.hasCustomArgs(), "Custom args flag set");

        QuizConfig helpCfg = QuizConfig.fromArgs(new String[]{"--help"});
        assertTrue(helpCfg.isHelpRequested(), "Parsed --help");

        assertThrows(InvalidQuizDataException.class,
                () -> QuizConfig.fromArgs(new String[]{"--unknown"}), "Unknown flag rejected");
        assertThrows(InvalidQuizDataException.class,
                () -> QuizConfig.fromArgs(new String[]{"--questions"}), "Missing value rejected");
        assertThrows(InvalidQuizDataException.class,
                () -> QuizConfig.fromArgs(new String[]{"--time", "5"}), "Below min time rejected");
        assertThrows(InvalidQuizDataException.class,
                () -> QuizConfig.fromArgs(new String[]{"--questions", "abc"}), "Non-numeric value rejected");
    }

    private static void tc38BackgroundTimerThread(Path tempDir) throws InterruptedException {
        QuizTimer timer = new QuizTimer(1L); // 1 second real monotonic timer
        CountDownLatch timeoutLatch = new CountDownLatch(1);
        timer.start(timeoutLatch::countDown);

        assertTrue(timer.isMonitorThreadAlive(), "Background quiz-countdown-monitor thread is alive");
        boolean fired = timeoutLatch.await(2500L, TimeUnit.MILLISECONDS);
        timer.stop();

        assertTrue(fired, "Background monitor thread fired timeout callback on expiration");
        assertTrue(timer.isTimeExpired(), "Timer flagged expired by background thread");
    }

    private static void tc39EndToEndWithCliArgs(Path tempDir) throws IOException, InvalidQuizDataException, QuizException {
        Path customBank = tempDir.resolve("cli_questions.txt");
        Files.writeString(customBank, String.join("\n",
                "What is Java?",
                "A) Programming Language", "B) Operating System", "C) Database", "D) Browser", "A"),
                StandardCharsets.UTF_8);
        Path customScores = tempDir.resolve("cli_scores.txt");

        QuizConfig cfg = QuizConfig.fromArgs(new String[]{
                "-q", "1",
                "-t", "90",
                "-b", customBank.toString(),
                "-s", customScores.toString(),
                "--no-color"
        });

        // Blank entries accept the CLI-configured defaults (1 question, 90 seconds)
        String input = String.join("\n", "1", "CLI Tester", "", "", "A", "5") + "\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new Main(cfg, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8)).run();

        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Time Limit        : 01:30"), "CLI --time 90 applied as session default");
        assertTrue(output.contains("Final Score       : 1/1"), "CLI --questions 1 applied as session default");

        FileManager checkFm = new FileManager(customBank, customScores);
        assertEquals(1, checkFm.loadScores().getRecords().size(), "Score saved to CLI --scores path");
    }

    private static void tc40CrossPackagePolymorphism(Path tempDir) {
        AssessmentItem item = new Question("Polymorphism check?",
                new String[]{"Opt1", "Opt2", "Opt3", "Opt4"}, 'C');
        assertTrue(item.evaluateAnswer("c"), "Dynamic dispatch through AssessmentItem reference");
        assertEquals("C) Opt3", item.getCorrectAnswerDisplay(), "Polymorphic display method");

        QuizDataStorage storage = new FileManager(tempDir.resolve("q.txt"), tempDir.resolve("s.txt"));
        assertTrue(storage instanceof FileManager, "Interface realization across storage package");

        QuizException storageEx = new QuizStorageException("disk error");
        QuizException dataEx = new InvalidQuizDataException("bad format");
        assertEquals("STORAGE_ERROR", storageEx.getErrorCode(), "Subclass error code 1");
        assertEquals("INVALID_DATA", dataEx.getErrorCode(), "Subclass error code 2");
    }
}
