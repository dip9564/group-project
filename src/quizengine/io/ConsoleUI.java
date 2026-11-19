package quizengine.io;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import quizengine.model.AssessmentItem;
import quizengine.model.Participant;
import quizengine.model.Question;
import quizengine.model.Score;

/**
 * Cross-platform 7-bit ASCII CLI presentation helper for Windows, Linux, and macOS.
 *
 * <p>Renders framed section headers, word-wrapped question cards, ASCII progress bars,
 * structured result summaries, and bordered tables with conservative ANSI detection
 * and automatic plain-text fallback.</p>
 */
public class ConsoleUI {

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String WHITE = "\u001B[37m";

    public static final int CARD_WIDTH = 64;
    public static final String SEPARATOR = "=".repeat(CARD_WIDTH);
    public static final String THIN_SEPARATOR = "-".repeat(CARD_WIDTH);
    private static final String DOUBLE_BORDER = "+" + "=".repeat(CARD_WIDTH - 2) + "+";
    private static final String SINGLE_BORDER = "+" + "-".repeat(CARD_WIDTH - 2) + "+";

    private final PrintStream out;
    private final boolean colorEnabled;

    public ConsoleUI() {
        this(System.out, detectAnsiSupport());
    }

    public ConsoleUI(PrintStream out, boolean colorEnabled) {
        this.out = out;
        this.colorEnabled = colorEnabled;
    }

    public static boolean detectAnsiSupport() {
        if (System.console() == null) {
            return false;
        }
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }
        String term = System.getenv("TERM");
        if (term != null && term.equalsIgnoreCase("dumb")) {
            return false;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return System.getenv("WT_SESSION") != null
                    || System.getenv("ANSICON") != null
                    || "ON".equalsIgnoreCase(System.getenv("ConEmuANSI"))
                    || System.getenv("TERM_PROGRAM") != null
                    || (term != null && !term.isBlank());
        }
        return true;
    }

    public boolean isColorEnabled() {
        return colorEnabled;
    }

    public PrintStream getOut() {
        return out;
    }

    private String style(String ansiPrefix, String text) {
        if (!colorEnabled) {
            return text;
        }
        return ansiPrefix + text + RESET;
    }

    public void printLine(String text) {
        out.println(text);
    }

    public void printBlankLine() {
        out.println();
    }

    public void printPrompt(String prompt) {
        out.print(style(BOLD, prompt));
        out.flush();
    }

    public void printBanner(String title) {
        printBanner(title, null);
    }

    public void printBanner(String title, String subtitle) {
        out.println();
        out.println(style(CYAN + BOLD, DOUBLE_BORDER));
        out.println(style(CYAN + BOLD, "|")
                + style(BOLD + WHITE, centerText(title, CARD_WIDTH - 2))
                + style(CYAN + BOLD, "|"));
        if (subtitle != null && !subtitle.isBlank()) {
            out.println(style(CYAN + BOLD, "|")
                    + style(DIM, centerText(subtitle, CARD_WIDTH - 2))
                    + style(CYAN + BOLD, "|"));
        }
        out.println(style(CYAN + BOLD, DOUBLE_BORDER));
    }

    public void printMainMenu() {
        printBanner("TECHNICAL QUIZ COMPETITION ENGINE", "Group 5  |  OOP Lab (PCC-CS593) CLI");
        out.println(style(CYAN, "|") + " ".repeat(CARD_WIDTH - 2) + style(CYAN, "|"));
        printMenuRow("1", "Start New Quiz", "Timed MCQ competition");
        printMenuRow("2", "View Leaderboard", "Top 5 ranked attempts");
        printMenuRow("3", "View Previous Scores", "Full attempt archive");
        printMenuRow("4", "View Quiz Instructions", "Rules & scoring guide");
        printMenuRow("5", "Exit", "Save & quit safely");
        out.println(style(CYAN, "|") + " ".repeat(CARD_WIDTH - 2) + style(CYAN, "|"));
        out.println(style(CYAN, SINGLE_BORDER));
    }

    private void printMenuRow(String key, String label, String hint) {
        out.println(style(CYAN, "|")
                + style(BOLD, "  [" + key + "] ")
                + String.format(Locale.US, "%-26s", label)
                + style(DIM, String.format(Locale.US, "%28s  ", hint))
                + style(CYAN, "|"));
    }

    public void printSuccess(String message) {
        out.println(style(GREEN + BOLD, "[OK] " + message));
    }

    public void printError(String message) {
        out.println(style(RED + BOLD, "[ERROR] " + message));
    }

    public void printWarning(String message) {
        out.println(style(YELLOW + BOLD, "[WARNING] " + message));
    }

    public void printInfo(String message) {
        out.println(style(CYAN, "[INFO] " + message));
    }

    public void printDiagnostics(String heading, List<String> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return;
        }
        printWarning(heading + " (" + diagnostics.size() + " issue(s) reported):");
        for (String diag : diagnostics) {
            out.println("  - " + diag);
        }
    }

    public void printSessionOverview(Participant participant, int totalQuestions, long timeLimitSeconds) {
        printBanner("QUIZ SESSION STARTED", "Answer A, B, C, or D  |  Background Timer Active");
        printKeyValueRow("Attempt ID", participant.getAttemptId());
        printKeyValueRow("Participant", truncate(participant.getFullName(), 40));
        printKeyValueRow("Questions", totalQuestions + " (shuffled, non-repeating)");
        printKeyValueRow("Time Limit", Score.formatSeconds(timeLimitSeconds) + " (strict timeout)");
        out.println(style(CYAN, SINGLE_BORDER));
        out.println();
    }

    /**
     * Renders a single question inside a structured ASCII card with progress bar,
     * live timer readout, running score, and word-wrapped prompt &amp; options array.
     */
    public void printQuestionCard(
            AssessmentItem item,
            int questionNumber,
            int totalQuestions,
            int correctSoFar,
            int answeredSoFar,
            long remainingSeconds,
            long elapsedSeconds,
            boolean lowTimeAlert) {
        String progressBar = buildProgressBar(questionNumber, totalQuestions, 20);
        int progressPct = (totalQuestions > 0)
                ? (int) Math.round((questionNumber * 100.0) / totalQuestions)
                : 0;

        out.println(style(CYAN, SINGLE_BORDER));
        String headerLine = String.format(
                Locale.US,
                " Question %d of %d   %s %3d%%",
                questionNumber, totalQuestions, progressBar, progressPct);
        printFramedLine(headerLine, BOLD);

        String statusLine = String.format(
                Locale.US,
                " Remaining: %s  |  Elapsed: %s  |  Score: %d/%d",
                Score.formatSeconds(remainingSeconds),
                Score.formatSeconds(elapsedSeconds),
                correctSoFar,
                answeredSoFar);
        printFramedLine(statusLine, lowTimeAlert ? YELLOW + BOLD : DIM);
        if (lowTimeAlert) {
            printFramedLine(" [TIME ALERT] 30 seconds or less remaining!", YELLOW + BOLD);
        }
        out.println(style(CYAN, SINGLE_BORDER));

        printFramedLine("", "");
        for (String line : wrapText(item.getPrompt(), CARD_WIDTH - 6)) {
            printFramedLine(" " + line, BOLD);
        }
        printFramedLine("", "");

        if (item instanceof Question) {
            Question mcq = (Question) item;
            String[] options = mcq.getOptions();
            for (int i = 0; i < options.length; i++) {
                char label = Question.OPTION_LABELS[i];
                List<String> wrappedOpt = wrapText(options[i], CARD_WIDTH - 12);
                for (int lineIdx = 0; lineIdx < wrappedOpt.size(); lineIdx++) {
                    String prefix = (lineIdx == 0) ? ("   " + label + ") ") : "      ";
                    printFramedLine(prefix + wrappedOpt.get(lineIdx), "");
                }
            }
        } else {
            for (String line : item.formatForDisplay(questionNumber, totalQuestions).split("\\R")) {
                printFramedLine(" " + truncate(line, CARD_WIDTH - 4), "");
            }
        }

        printFramedLine("", "");
        out.println(style(CYAN, SINGLE_BORDER));
    }

    public void printAnswerFeedback(boolean isCorrect, String correctAnswerDisplay) {
        if (isCorrect) {
            out.println(style(GREEN + BOLD, "  [CORRECT] Great job! Your answer is right."));
        } else {
            out.println(style(RED + BOLD, "  [INCORRECT] Correct answer: " + correctAnswerDisplay));
        }
        out.println();
    }

    public void printScoreSummary(Score score, boolean savedToDisk, boolean timedOut) {
        printBanner(
                timedOut ? "TIME EXPIRED - QUIZ ENDED" : "QUIZ COMPLETED",
                getPerformanceTier(score.getPercentage().doubleValue()));

        printKeyValueRow("Attempt ID", score.getAttemptId());
        printKeyValueRow("Participant", truncate(score.getParticipantName(), 40));
        out.println(style(CYAN, SINGLE_BORDER));
        printKeyValueRow("Total Questions", String.valueOf(score.getTotalQuestions()));
        printKeyValueRow("Correct Answers", String.valueOf(score.getCorrectAnswers()));
        printKeyValueRow("Incorrect", String.valueOf(score.getIncorrectAnswers()));
        printKeyValueRow("Unanswered", String.valueOf(score.getUnansweredQuestions()));
        out.println(style(CYAN, SINGLE_BORDER));
        printKeyValueRow("Final Score", score.getFinalScore() + "/" + score.getTotalQuestions());
        printKeyValueRow("Percentage", score.getFormattedPercentage() + "%");
        printKeyValueRow("Accuracy Bar",
                buildProgressBar(score.getCorrectAnswers(), score.getTotalQuestions(), 24)
                        + " " + score.getFormattedPercentage() + "%");
        printKeyValueRow("Time Taken", score.getFormattedDuration());
        out.println(style(CYAN + BOLD, DOUBLE_BORDER));

        if (savedToDisk) {
            out.println(style(CYAN + BOLD, "|")
                    + style(GREEN + BOLD, centerText("YOUR RESULT HAS BEEN SAVED", CARD_WIDTH - 2))
                    + style(CYAN + BOLD, "|"));
        } else {
            out.println(style(CYAN + BOLD, "|")
                    + style(RED + BOLD, centerText("RESULT COULD NOT BE SAVED", CARD_WIDTH - 2))
                    + style(CYAN + BOLD, "|"));
        }
        out.println(style(CYAN + BOLD, DOUBLE_BORDER));
    }

    /**
     * Renders the ranked Top-N leaderboard table using parallel score list and {@code int[] ranks} array.
     */
    public void printLeaderboard(
            List<Score> rankedScores,
            int[] ranks,
            int totalArchivedCount,
            List<String> diagnostics) {
        printBanner("TOP 5 LEADERBOARD", "Ranked by Percentage -> Correct Answers -> Fastest Time");
        out.println();

        if (diagnostics != null && !diagnostics.isEmpty()) {
            printDiagnostics("Score archive warnings", diagnostics);
            out.println();
        }

        if (rankedScores == null || rankedScores.isEmpty()) {
            printInfo("No quiz scores recorded yet. Complete a quiz to appear on the leaderboard!");
            out.println(style(CYAN, SINGLE_BORDER));
            return;
        }

        String border = "+------+--------+------------------------+---------+----------+--------+";
        out.println(style(CYAN, border));
        out.println(style(CYAN, "|")
                + style(BOLD, String.format(Locale.US,
                " %-4s | %-6s | %-22s | %-7s | %-8s | %-6s ",
                "Rank", "ID", "Participant", "Score", "Percent", "Time"))
                + style(CYAN, "|"));
        out.println(style(CYAN, border));

        for (int i = 0; i < rankedScores.size(); i++) {
            Score s = rankedScores.get(i);
            int rank = ranks[i];
            String rankText = String.format(Locale.US, "#%-3d", rank);
            String scoreFraction = s.getFinalScore() + "/" + s.getTotalQuestions();
            String rowBody = String.format(
                    Locale.US,
                    " %-4s | %-6s | %-22s | %-7s | %7s%% | %-6s ",
                    rankText,
                    truncate(s.getAttemptId(), 6),
                    truncate(s.getParticipantName(), 22),
                    scoreFraction,
                    s.getFormattedPercentage(),
                    s.getFormattedDuration());

            String rowStyle = (rank == 1) ? (YELLOW + BOLD) : (rank <= 3 ? GREEN : "");
            out.println(style(CYAN, "|") + style(rowStyle, rowBody) + style(CYAN, "|"));
        }

        out.println(style(CYAN, border));
        out.println(style(DIM, "  Showing top " + rankedScores.size()
                + " of " + totalArchivedCount
                + " archived attempt(s). Ties share competition rank."));
    }

    public void printPreviousScores(List<Score> scores) {
        printBanner("PREVIOUS QUIZ SCORES", "Persistent Score Archive");
        out.println();
        if (scores == null || scores.isEmpty()) {
            printInfo("No previous quiz scores found in the archive.");
            out.println(style(CYAN, SINGLE_BORDER));
            return;
        }

        String border = "+--------+----------------------+---------+----------+--------+---------------------+";
        out.println(style(CYAN, border));
        out.println(style(CYAN, "|")
                + style(BOLD, String.format(Locale.US,
                " %-6s | %-20s | %-7s | %-8s | %-6s | %-19s ",
                "ID", "Participant", "Score", "Percent", "Time", "Date & Time"))
                + style(CYAN, "|"));
        out.println(style(CYAN, border));

        for (Score s : scores) {
            String scoreFraction = s.getFinalScore() + "/" + s.getTotalQuestions();
            String row = String.format(
                    Locale.US,
                    " %-6s | %-20s | %-7s | %7s%% | %-6s | %-19s ",
                    truncate(s.getAttemptId(), 6),
                    truncate(s.getParticipantName(), 20),
                    scoreFraction,
                    s.getFormattedPercentage(),
                    s.getFormattedDuration(),
                    s.getFormattedCompletedAt());
            out.println(style(CYAN, "|") + row + style(CYAN, "|"));
        }

        out.println(style(CYAN, border));
        out.println(style(DIM, "  Total archived attempts: " + scores.size()));
    }

    public void printInstructions(int defaultQuestionCount, long defaultTimeLimitSeconds) {
        printBanner("QUIZ INSTRUCTIONS", "Rules, Timing, CLI Flags & Scoring Guide");
        out.println();
        out.println(style(BOLD, "  1. Participant Registration"));
        out.println("     - Enter your full name (2-60 characters; letters, spaces,");
        out.println("       periods, hyphens, and apostrophes are allowed).");
        out.println("     - Each session receives a unique Attempt ID (e.g., A001).");
        out.println();
        out.println(style(BOLD, "  2. Dynamic Question Bank & Command-Line Configuration"));
        out.println("     - Questions load dynamically from the configured text file.");
        out.println("     - Questions are shuffled and never repeated in an attempt.");
        out.println("     - Current defaults: " + defaultQuestionCount + " questions, "
                + Score.formatSeconds(defaultTimeLimitSeconds) + " time limit.");
        out.println("     - CLI flags: --questions N --time SEC --bank FILE --scores FILE --no-color");
        out.println();
        out.println(style(BOLD, "  3. Answering Questions"));
        out.println("     - Enter A, B, C, or D (uppercase or lowercase accepted).");
        out.println("     - Invalid inputs are rejected without losing the question.");
        out.println("     - Immediate feedback appears after each submitted answer.");
        out.println();
        out.println(style(BOLD, "  4. Multithreaded Background Countdown Timer"));
        out.println("     - A background timer thread monitors remaining time live.");
        out.println("     - If time expires, the quiz stops immediately and remaining");
        out.println("       questions are marked as Unanswered.");
        out.println();
        out.println(style(BOLD, "  5. Scoring & Leaderboard Ranking"));
        out.println("     - Percentage = (Correct Answers / Total Questions) * 100");
        out.println("     - Ranking: Percentage (desc) -> Correct (desc) -> Time (asc)");
        out.println("     - Results are archived automatically in CSV format.");
        out.println();
        out.println(style(CYAN, SINGLE_BORDER));
    }

    private void printKeyValueRow(String key, String value) {
        String content = String.format(Locale.US, "  %-17s : %s", key, value);
        printFramedLine(content, "");
    }

    private void printFramedLine(String text, String ansiStyle) {
        int innerWidth = CARD_WIDTH - 2;
        String safe = truncate(text, innerWidth);
        int pad = Math.max(0, innerWidth - safe.length());
        String padded = safe + " ".repeat(pad);
        String body = (ansiStyle == null || ansiStyle.isEmpty()) ? padded : style(ansiStyle, padded);
        out.println(style(CYAN, "|") + body + style(CYAN, "|"));
    }

    public static String buildProgressBar(int current, int total, int width) {
        int safeWidth = Math.max(4, width);
        int filled = 0;
        if (total > 0 && current > 0) {
            double ratio = Math.min(1.0, (double) current / (double) total);
            filled = (int) Math.round(ratio * safeWidth);
        }
        return "[" + "#".repeat(filled) + "-".repeat(safeWidth - filled) + "]";
    }

    public static List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        int limit = Math.max(10, maxWidth);
        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (word.length() > limit) {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                int idx = 0;
                while (idx < word.length()) {
                    int end = Math.min(idx + limit, word.length());
                    lines.add(word.substring(idx, end));
                    idx = end;
                }
                continue;
            }
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= limit) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    public static String getPerformanceTier(double percentage) {
        if (percentage >= 90.0) {
            return "Performance Rating: Outstanding";
        }
        if (percentage >= 75.0) {
            return "Performance Rating: Great Job";
        }
        if (percentage >= 50.0) {
            return "Performance Rating: Good Effort";
        }
        return "Performance Rating: Keep Practicing";
    }

    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        if (maxLength <= 3) {
            return text.substring(0, maxLength);
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private static String centerText(String text, int width) {
        if (text == null) {
            return " ".repeat(Math.max(0, width));
        }
        String safe = truncate(text, width);
        int totalPad = Math.max(0, width - safe.length());
        int leftPad = totalPad / 2;
        int rightPad = totalPad - leftPad;
        return " ".repeat(leftPad) + safe + " ".repeat(rightPad);
    }
}
