package quizengine.storage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import quizengine.exception.InvalidQuizDataException;
import quizengine.exception.QuizException;
import quizengine.exception.QuizStorageException;
import quizengine.model.LoadResult;
import quizengine.model.Question;
import quizengine.model.Score;

/**
 * Flat-file persistence manager implementing {@link QuizDataStorage}.
 *
 * <p>Demonstrates Java File I/O ({@link BufferedReader}, {@link BufferedWriter},
 * {@link SeekableByteChannel}), String manipulation and tokenization
 * ({@link StringTokenizer}, RFC-4180 CSV escaping/parsing), array construction
 * ({@code String[4]} options), and defensive recovery from malformed records.</p>
 */
public class FileManager implements QuizDataStorage {

    public static final Path DEFAULT_QUESTIONS_PATH = Paths.get("data", "questions.txt");
    public static final Path DEFAULT_SCORES_PATH = Paths.get("data", "scores.txt");
    public static final String SCORES_CSV_HEADER =
            "attemptId,participant,score,total,percentage,duration,date";

    private static final int EXPECTED_CSV_COLUMNS = 7;

    private final Path questionsPath;
    private final Path scoresPath;

    public FileManager() {
        this(DEFAULT_QUESTIONS_PATH, DEFAULT_SCORES_PATH);
    }

    public FileManager(Path questionsPath, Path scoresPath) {
        this.questionsPath = Objects.requireNonNull(questionsPath, "Questions path must not be null.");
        this.scoresPath = Objects.requireNonNull(scoresPath, "Scores path must not be null.");
    }

    public Path getQuestionsPath() {
        return questionsPath;
    }

    public Path getScoresPath() {
        return scoresPath;
    }

    @Override
    public LoadResult<Question> loadQuestions() throws QuizException {
        if (!Files.exists(questionsPath)) {
            throw new QuizStorageException("Question bank file not found at: " + questionsPath);
        }
        if (Files.isDirectory(questionsPath)) {
            throw new QuizStorageException("Question bank path points to a directory, not a file: " + questionsPath);
        }
        if (!Files.isReadable(questionsPath)) {
            throw new QuizStorageException("Permission denied: cannot read question bank file at " + questionsPath);
        }

        List<Question> validQuestions = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        Set<String> seenPrompts = new HashSet<>();

        List<String> currentBlock = new ArrayList<>();
        int blockStartLine = -1;
        int lineNumber = 0;
        boolean sawAnyNonBlankLine = false;

        try (BufferedReader reader = Files.newBufferedReader(questionsPath, StandardCharsets.UTF_8)) {
            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1) {
                    rawLine = stripUtf8Bom(rawLine);
                }
                String trimmed = rawLine.trim();
                if (trimmed.isEmpty()) {
                    if (!currentBlock.isEmpty()) {
                        processQuestionBlock(currentBlock, blockStartLine, validQuestions, diagnostics, seenPrompts);
                        currentBlock.clear();
                        blockStartLine = -1;
                    }
                } else {
                    sawAnyNonBlankLine = true;
                    if (currentBlock.isEmpty()) {
                        blockStartLine = lineNumber;
                    }
                    currentBlock.add(trimmed);
                }
            }
            if (!currentBlock.isEmpty()) {
                processQuestionBlock(currentBlock, blockStartLine, validQuestions, diagnostics, seenPrompts);
            }
        } catch (AccessDeniedException e) {
            throw new QuizStorageException("Access denied while reading question bank: " + questionsPath, e);
        } catch (IOException e) {
            throw new QuizStorageException(
                    "I/O error while reading question bank (" + questionsPath + "): " + e.getMessage(), e);
        }

        if (!sawAnyNonBlankLine) {
            throw new InvalidQuizDataException("Question bank file is empty: " + questionsPath);
        }

        return new LoadResult<>(validQuestions, diagnostics);
    }

    /**
     * Parses a 6-line question block into a {@link Question} backed by a {@code String[4]} array.
     *
     * @param blockLines 6 lines (1 prompt, 4 options A)-D), 1 answer key)
     * @return validated {@link Question}
     * @throws InvalidQuizDataException if formatting or option invariants fail
     */
    public Question parseQuestionBlock(List<String> blockLines) throws InvalidQuizDataException {
        if (blockLines == null || blockLines.size() != 6) {
            int found = (blockLines == null) ? 0 : blockLines.size();
            throw new InvalidQuizDataException(
                    "Expected 6 lines (1 prompt, 4 options A-D, 1 answer key) but found " + found + ".");
        }

        String prompt = blockLines.get(0).trim();
        if (prompt.isEmpty()) {
            throw new InvalidQuizDataException("Question prompt is empty.");
        }
        if (prompt.length() >= 2 && Question.isValidOptionLetter(prompt.charAt(0)) && prompt.charAt(1) == ')') {
            throw new InvalidQuizDataException(
                    "Missing question prompt (found option line '" + prompt + "' instead).");
        }

        String[] parsedOptions = new String[Question.EXPECTED_OPTION_COUNT];
        for (int i = 0; i < Question.EXPECTED_OPTION_COUNT; i++) {
            char expectedLabel = Question.OPTION_LABELS[i];
            String optionLine = blockLines.get(i + 1).trim();
            parsedOptions[i] = extractOptionText(optionLine, expectedLabel);
        }

        String answerLine = blockLines.get(5).trim();
        char correctOption;
        try {
            correctOption = Question.parseAnswerLetter(answerLine);
        } catch (IllegalArgumentException e) {
            throw new InvalidQuizDataException(
                    "Invalid answer key '" + answerLine + "'. Expected A, B, C, or D.", e);
        }

        try {
            return new Question(prompt, parsedOptions, correctOption);
        } catch (IllegalArgumentException e) {
            throw new InvalidQuizDataException(e.getMessage(), e);
        }
    }

    private String extractOptionText(String optionLine, char expectedLabel) throws InvalidQuizDataException {
        if (optionLine == null || optionLine.isEmpty()) {
            throw new InvalidQuizDataException("Option " + expectedLabel + " line is empty.");
        }

        StringTokenizer tokenizer = new StringTokenizer(optionLine, ")", true);
        if (!tokenizer.hasMoreTokens()) {
            throw new InvalidQuizDataException("Malformed option line: '" + optionLine + "'.");
        }

        String labelToken = tokenizer.nextToken().trim();
        if (labelToken.length() != 1 || Character.toUpperCase(labelToken.charAt(0)) != expectedLabel) {
            throw new InvalidQuizDataException(
                    "Expected option label '" + expectedLabel + ")' but found '" + optionLine + "'.");
        }

        if (!tokenizer.hasMoreTokens() || !")".equals(tokenizer.nextToken())) {
            throw new InvalidQuizDataException(
                    "Missing ')' delimiter after option label " + expectedLabel + " in '" + optionLine + "'.");
        }

        int delimiterIndex = optionLine.indexOf(')');
        String optionText = optionLine.substring(delimiterIndex + 1).trim();
        if (optionText.isEmpty()) {
            throw new InvalidQuizDataException(
                    "Option " + expectedLabel + " has no text after '" + expectedLabel + ")'.");
        }

        return optionText;
    }

    private void processQuestionBlock(
            List<String> blockLines,
            int startLine,
            List<Question> validQuestions,
            List<String> diagnostics,
            Set<String> seenPrompts) {
        try {
            Question question = parseQuestionBlock(blockLines);
            String promptKey = question.getNormalizedPromptKey();
            if (!seenPrompts.add(promptKey)) {
                diagnostics.add("Line " + startLine + ": Duplicate question skipped (\"" + question.getPrompt() + "\").");
                return;
            }
            validQuestions.add(question);
        } catch (InvalidQuizDataException e) {
            diagnostics.add("Line " + startLine + ": Malformed question skipped - " + e.getMessage());
        }
    }

    @Override
    public LoadResult<Score> loadScores() throws QuizException {
        if (!Files.exists(scoresPath)) {
            return new LoadResult<>(new ArrayList<>(), new ArrayList<>());
        }
        if (Files.isDirectory(scoresPath)) {
            throw new QuizStorageException("Score archive path points to a directory, not a file: " + scoresPath);
        }
        if (!Files.isReadable(scoresPath)) {
            throw new QuizStorageException("Permission denied: cannot read score archive at " + scoresPath);
        }

        List<Score> validScores = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        Set<String> seenAttemptIds = new HashSet<>();
        int lineNumber = 0;
        boolean checkedHeader = false;

        try (BufferedReader reader = Files.newBufferedReader(scoresPath, StandardCharsets.UTF_8)) {
            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1) {
                    rawLine = stripUtf8Bom(rawLine);
                }
                String trimmed = rawLine.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (!checkedHeader) {
                    checkedHeader = true;
                    if (isHeaderLine(trimmed)) {
                        continue;
                    }
                }
                try {
                    Score parsed = parseScoreCsvLine(rawLine);
                    String idKey = parsed.getAttemptId().toUpperCase(Locale.ROOT);
                    if (!seenAttemptIds.add(idKey)) {
                        diagnostics.add("Line " + lineNumber
                                + ": Duplicate attempt ID '" + parsed.getAttemptId() + "' skipped.");
                        continue;
                    }
                    validScores.add(parsed);
                } catch (InvalidQuizDataException e) {
                    diagnostics.add("Line " + lineNumber + ": Corrupted score entry skipped - " + e.getMessage());
                }
            }
        } catch (AccessDeniedException e) {
            throw new QuizStorageException("Access denied while reading score archive: " + scoresPath, e);
        } catch (IOException e) {
            throw new QuizStorageException(
                    "I/O error while reading score archive (" + scoresPath + "): " + e.getMessage(), e);
        }

        return new LoadResult<>(validScores, diagnostics);
    }

    @Override
    public void appendScore(Score score) throws QuizException {
        Objects.requireNonNull(score, "Score to archive must not be null.");

        try {
            Path parent = scoresPath.toAbsolutePath().getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            if (Files.exists(scoresPath) && Files.isDirectory(scoresPath)) {
                throw new QuizStorageException(
                        "Cannot write score archive because target path is a directory: " + scoresPath);
            }
            if (Files.exists(scoresPath) && !Files.isWritable(scoresPath)) {
                throw new QuizStorageException("Permission denied: cannot write to score archive at " + scoresPath);
            }

            boolean fileExists = Files.exists(scoresPath);
            long existingSize = fileExists ? Files.size(scoresPath) : 0L;
            boolean needsHeader = !fileExists || existingSize == 0L;
            boolean needsLeadingNewline = !needsHeader && !endsWithNewline(scoresPath, existingSize);
            String csvRow = formatScoreCsvLine(score);

            try (BufferedWriter writer = Files.newBufferedWriter(
                    scoresPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                if (needsHeader) {
                    writer.write(SCORES_CSV_HEADER);
                    writer.newLine();
                } else if (needsLeadingNewline) {
                    writer.newLine();
                }
                writer.write(csvRow);
                writer.newLine();
                writer.flush();
            }
        } catch (QuizStorageException e) {
            throw e;
        } catch (AccessDeniedException e) {
            throw new QuizStorageException("Permission denied while writing to score archive: " + scoresPath, e);
        } catch (IOException e) {
            throw new QuizStorageException(
                    "I/O error while saving score to " + scoresPath + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String generateNextAttemptId() throws QuizException {
        LoadResult<Score> existing = loadScores();
        int maxNumber = 0;
        Set<String> usedIds = new HashSet<>();

        for (Score s : existing.getRecords()) {
            maxNumber = registerAttemptId(s.getAttemptId(), usedIds, maxNumber);
        }

        if (Files.exists(scoresPath) && !Files.isDirectory(scoresPath) && Files.isReadable(scoresPath)) {
            try (BufferedReader reader = Files.newBufferedReader(scoresPath, StandardCharsets.UTF_8)) {
                String rawLine;
                int lineNo = 0;
                while ((rawLine = reader.readLine()) != null) {
                    lineNo++;
                    if (lineNo == 1) {
                        rawLine = stripUtf8Bom(rawLine);
                    }
                    String trimmed = rawLine.trim();
                    if (trimmed.isEmpty() || isHeaderLine(trimmed)) {
                        continue;
                    }
                    int commaIdx = trimmed.indexOf(',');
                    String firstCol = (commaIdx >= 0 ? trimmed.substring(0, commaIdx) : trimmed)
                            .replace("\"", "").trim();
                    if (!firstCol.isEmpty()) {
                        maxNumber = registerAttemptId(firstCol, usedIds, maxNumber);
                    }
                }
            } catch (IOException ignored) {
                // Already validated by loadScores(); best-effort scan for malformed rows
            }
        }

        int next = maxNumber + 1;
        String candidate = String.format(Locale.US, "A%03d", next);
        while (usedIds.contains(candidate.toUpperCase(Locale.ROOT))) {
            next++;
            candidate = String.format(Locale.US, "A%03d", next);
        }
        return candidate;
    }

    private int registerAttemptId(String id, Set<String> usedIds, int currentMax) {
        if (id == null || id.trim().isEmpty()) {
            return currentMax;
        }
        String trimmed = id.trim();
        usedIds.add(trimmed.toUpperCase(Locale.ROOT));
        if (trimmed.length() >= 2 && (trimmed.charAt(0) == 'A' || trimmed.charAt(0) == 'a')) {
            try {
                int parsed = Integer.parseInt(trimmed.substring(1));
                if (parsed > currentMax) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // Non-numeric custom IDs are tracked in usedIds only
            }
        }
        return currentMax;
    }

    public static String formatScoreCsvLine(Score score) {
        return String.join(",",
                escapeCsvField(score.getAttemptId()),
                escapeCsvField(score.getParticipantName()),
                score.getFinalScore().toString(),
                score.getTotalQuestions().toString(),
                score.getFormattedPercentage(),
                Long.toString(score.getDurationSeconds()),
                escapeCsvField(score.getIsoCompletedAt()));
    }

    public static String escapeCsvField(String raw) {
        if (raw == null) {
            return "";
        }
        String sanitized = raw.replace("\r", " ").replace("\n", " ");
        boolean needsQuoting = sanitized.contains(",")
                || sanitized.contains("\"")
                || sanitized.startsWith(" ")
                || sanitized.endsWith(" ");
        if (!needsQuoting) {
            return sanitized;
        }
        return "\"" + sanitized.replace("\"", "\"\"") + "\"";
    }

    public static List<String> parseCsvLine(String line) throws InvalidQuizDataException {
        if (line == null) {
            throw new InvalidQuizDataException("CSV line is null.");
        }
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean justClosedQuote = false;
        int len = line.length();

        for (int i = 0; i < len; i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                        justClosedQuote = true;
                    }
                } else {
                    current.append(c);
                }
            } else if (justClosedQuote) {
                if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                    justClosedQuote = false;
                } else {
                    throw new InvalidQuizDataException(
                            "Unexpected character '" + c + "' after closing quote in CSV field.");
                }
            } else {
                if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else if (c == '"') {
                    if (current.length() > 0) {
                        throw new InvalidQuizDataException("Unexpected quote inside unquoted CSV field.");
                    }
                    inQuotes = true;
                } else {
                    current.append(c);
                }
            }
        }

        if (inQuotes) {
            throw new InvalidQuizDataException("Unclosed quoted field in CSV line.");
        }
        fields.add(current.toString());
        return fields;
    }

    public static Score parseScoreCsvLine(String line) throws InvalidQuizDataException {
        List<String> fields = parseCsvLine(line);
        if (fields.size() != EXPECTED_CSV_COLUMNS) {
            throw new InvalidQuizDataException(
                    "Expected " + EXPECTED_CSV_COLUMNS + " CSV columns but found " + fields.size() + ".");
        }

        String attemptId = fields.get(0).trim();
        String participant = fields.get(1).trim();
        if (attemptId.isEmpty() || participant.isEmpty()) {
            throw new InvalidQuizDataException("Attempt ID and participant name must not be empty.");
        }

        Integer scoreVal;
        Integer totalVal;
        Double archivedPercentage;
        long durationVal;
        LocalDateTime dateVal;

        try {
            scoreVal = Integer.valueOf(fields.get(2).trim());
            totalVal = Integer.valueOf(fields.get(3).trim());
            archivedPercentage = Double.valueOf(fields.get(4).trim());
            durationVal = Long.parseLong(fields.get(5).trim());
        } catch (NumberFormatException e) {
            throw new InvalidQuizDataException("Invalid numeric field in score record.", e);
        }

        if (Double.isNaN(archivedPercentage) || Double.isInfinite(archivedPercentage)
                || archivedPercentage < 0.0 || archivedPercentage > 100.0) {
            throw new InvalidQuizDataException("Percentage out of valid range [0.00, 100.00]: " + archivedPercentage);
        }
        if (totalVal <= 0 || scoreVal < 0 || scoreVal > totalVal) {
            throw new InvalidQuizDataException("Invalid score/total values: " + scoreVal + "/" + totalVal);
        }
        if (durationVal < 0L) {
            throw new InvalidQuizDataException("Negative duration value: " + durationVal);
        }

        Double expectedPercentage = Score.calculatePercentage(scoreVal, totalVal);
        if (Math.abs(archivedPercentage - expectedPercentage) > 0.05) {
            throw new InvalidQuizDataException(
                    "Archived percentage (" + archivedPercentage + ") does not match score "
                            + scoreVal + "/" + totalVal + " (expected " + expectedPercentage + ").");
        }

        try {
            dateVal = LocalDateTime.parse(fields.get(6).trim(), Score.ARCHIVE_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new InvalidQuizDataException("Invalid ISO-8601 timestamp: '" + fields.get(6).trim() + "'.", e);
        }

        try {
            return Score.fromArchivedRecord(attemptId, participant, scoreVal, totalVal, durationVal, dateVal);
        } catch (IllegalArgumentException e) {
            throw new InvalidQuizDataException(e.getMessage(), e);
        }
    }

    private boolean isHeaderLine(String trimmedLine) {
        return trimmedLine.equalsIgnoreCase(SCORES_CSV_HEADER)
                || trimmedLine.toLowerCase(Locale.ROOT).startsWith("attemptid,");
    }

    private static String stripUtf8Bom(String line) {
        if (line != null && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
            return line.substring(1);
        }
        return line;
    }

    private static boolean endsWithNewline(Path path, long size) throws IOException {
        if (size <= 0L) {
            return true;
        }
        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            channel.position(size - 1L);
            ByteBuffer buffer = ByteBuffer.allocate(1);
            int read = channel.read(buffer);
            if (read <= 0) {
                return true;
            }
            byte lastByte = buffer.get(0);
            return lastByte == '\n' || lastByte == '\r';
        }
    }
}
