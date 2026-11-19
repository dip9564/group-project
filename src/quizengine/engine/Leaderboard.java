package quizengine.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import quizengine.exception.QuizException;
import quizengine.io.ConsoleUI;
import quizengine.model.LoadResult;
import quizengine.model.Score;
import quizengine.storage.QuizDataStorage;

/**
 * Computes, ranks, and displays the Top-N leaderboard from archived quiz scores.
 *
 * <p><strong>Ranking Rules:</strong></p>
 * <ol>
 *   <li>Higher score percentage (descending).</li>
 *   <li>Higher total correct answers (descending).</li>
 *   <li>Shorter completion time in seconds (ascending tie-breaker).</li>
 *   <li>Earlier completion timestamp (ascending deterministic tie-breaker).</li>
 * </ol>
 * <p>Equal scores on percentage, correct answers, and duration share the same
 * competition rank (e.g., 1, 1, 3).</p>
 */
public class Leaderboard {

    public static final int DEFAULT_TOP_LIMIT = 5;

    public static final class RankedEntry {
        private final int rank;
        private final Score score;

        public RankedEntry(int rank, Score score) {
            this.rank = rank;
            this.score = Objects.requireNonNull(score, "Score must not be null.");
        }

        public int getRank() {
            return rank;
        }

        public Score getScore() {
            return score;
        }
    }

    public static final class LeaderboardSnapshot {
        private final List<RankedEntry> topEntries;
        private final int totalArchivedCount;
        private final List<String> diagnostics;

        public LeaderboardSnapshot(
                List<RankedEntry> topEntries, int totalArchivedCount, List<String> diagnostics) {
            this.topEntries = Collections.unmodifiableList(new ArrayList<>(topEntries));
            this.totalArchivedCount = totalArchivedCount;
            this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
        }

        public List<RankedEntry> getTopEntries() {
            return topEntries;
        }

        public int getTotalArchivedCount() {
            return totalArchivedCount;
        }

        public List<String> getDiagnostics() {
            return diagnostics;
        }

        public boolean isEmpty() {
            return topEntries.isEmpty();
        }
    }

    private final QuizDataStorage storage;

    public Leaderboard(QuizDataStorage storage) {
        this.storage = Objects.requireNonNull(storage, "Storage must not be null.");
    }

    public LeaderboardSnapshot getTopAttempts() throws QuizException {
        return getTopAttempts(DEFAULT_TOP_LIMIT);
    }

    public LeaderboardSnapshot getTopAttempts(int limit) throws QuizException {
        if (limit <= 0) {
            throw new IllegalArgumentException("Leaderboard limit must be at least 1.");
        }
        LoadResult<Score> loaded = storage.loadScores();
        List<RankedEntry> ranked = rankScores(loaded.getRecords(), limit);
        return new LeaderboardSnapshot(ranked, loaded.getRecords().size(), loaded.getDiagnostics());
    }

    public static List<RankedEntry> rankScores(List<Score> scores, int limit) {
        if (scores == null || scores.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        List<Score> sorted = new ArrayList<>(scores);
        Collections.sort(sorted);

        List<RankedEntry> ranked = new ArrayList<>();
        int maxItems = Math.min(limit, sorted.size());
        int currentRank = 1;

        for (int i = 0; i < maxItems; i++) {
            Score current = sorted.get(i);
            if (i > 0) {
                Score previous = sorted.get(i - 1);
                if (!current.isTiedWith(previous)) {
                    currentRank = i + 1;
                }
            }
            ranked.add(new RankedEntry(currentRank, current));
        }
        return Collections.unmodifiableList(ranked);
    }

    public void displayTopLeaderboard(ConsoleUI ui) throws QuizException {
        LeaderboardSnapshot snapshot = getTopAttempts(DEFAULT_TOP_LIMIT);
        List<RankedEntry> entries = snapshot.getTopEntries();
        List<Score> topScores = new ArrayList<>(entries.size());
        int[] ranks = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            topScores.add(entries.get(i).getScore());
            ranks[i] = entries.get(i).getRank();
        }
        ui.printLeaderboard(topScores, ranks, snapshot.getTotalArchivedCount(), snapshot.getDiagnostics());
    }
}
