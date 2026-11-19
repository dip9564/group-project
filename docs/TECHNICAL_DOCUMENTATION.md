# Technical Documentation (PCC-CS593)

**Project:** Interactive Quiz Competition Engine with Score Archiving  
**Course:** Object-Oriented Programming Lab (PCC-CS593) — Group 5  
**Platform:** Pure Java SE 17+ (Zero Databases, Zero Frameworks, Zero External Libraries)

---

## 1. Acyclic Java Package Architecture

The codebase is organized into 6 clean production packages plus 1 independent test package forming a strict Directed Acyclic Graph (zero circular dependencies):

- `quizengine.exception` (leaf) — `QuizException`, `QuizStorageException`, `InvalidQuizDataException`
- `quizengine.model` (leaf) — `AssessmentItem`, `Question` (`String[4]` array), `Participant`, `Score` (`Integer`/`Double`, `Comparable`), `LoadResult<T>`
- `quizengine.storage` (depends on `model`, `exception`) — `QuizDataStorage` interface, `FileManager`
- `quizengine.io` (depends on `model`, `exception`) — `InputValidator`, `QueuedLineReader` (daemon reader thread), `ConsoleUI`
- `quizengine.engine` (depends on `model`, `storage`, `io`, `exception`) — `QuizTimer` (background monitor thread), `Leaderboard`, `QuizEngine`
- `quizengine.app` (top-level wiring) — `QuizConfig` (CLI args parser), `Main`
- `quizengine.test` — `StandaloneTestSuite` (40 independent core-Java unit, concurrency, CLI-arg, and integration tests)

---

## 2. Mapping of All 11 Mandatory PCC-CS593 Core Java Concepts

1. **Objects and Classes:** 18 focused classes encapsulating distinct domain entities, services, I/O helpers, and exceptions.
2. **Methods:** Overloaded constructors (`Question`, `QuizTimer`, `Main`, `QuizEngine`), static factory/helper methods (`Score.calculatePercentage`, `Score.fromArchivedRecord`, `QuizConfig.fromArgs`, `ConsoleUI.buildProgressBar`), and cohesive instance methods.
3. **Inheritance & Polymorphism:**
   - `Question extends AssessmentItem` overrides `evaluateAnswer(String)`, `getCorrectAnswerDisplay()`, and `formatForDisplay(int, int)`.
   - `QuizStorageException` and `InvalidQuizDataException` extend `QuizException`.
   - `FileManager implements QuizDataStorage` (interface-based polymorphism).
   - `Score implements Comparable<Score>` (polymorphic sorting via `Collections.sort`).
4. **Arrays:**
   - `Question` stores its 4 options in `private final String[] options` (with `public static final char[] OPTION_LABELS = {'A', 'B', 'C', 'D'}`) and returns defensive copies via `Arrays.copyOf(...)`.
   - `FileManager.parseQuestionBlock()` populates `String[] parsedOptions = new String[4]`.
   - `Leaderboard.displayTopLeaderboard()` passes parallel `int[] ranks` into `ConsoleUI.printLeaderboard(...)`.
   - `Main.main(String[] args)` passes command-line `String[] args` to `QuizConfig.fromArgs(String[])`.
5. **Strings and String Manipulation:** `StringTokenizer` option parsing, `substring`/`indexOf`/`trim`, regex validation (`InputValidator`), whitespace normalization, word-wrapping (`ConsoleUI.wrapText`), and RFC-4180 CSV escaping/state-machine parsing (`FileManager`).
6. **Command-Line Arguments:** `QuizConfig.fromArgs(String[] args)` supports `--questions`/`-q`, `--time`/`-t`, `--bank`/`-b`, `--scores`/`-s`, `--no-color`, and `--help`/`-h` with strict error reporting.
7. **Wrapper Classes:** `Integer` and `Double` in `Score`, `InputValidator`, `QuizConfig`, and `FileManager` (`Integer.valueOf`, `Double.valueOf`, `Integer.compare`, `Double.compare`, `Double.isNaN`, `Double.isInfinite`).
8. **Exception Handling:** Checked `QuizException` hierarchy, try-with-resources streams, zero empty catch blocks, no raw stack traces in normal CLI flow.
9. **Input/Output File Operations:** `BufferedReader`, `BufferedWriter`, `Files`, and `SeekableByteChannel` for UTF-8 BOM stripping, missing trailing-newline recovery, auto-created CSV headers, and diagnostic skipping of malformed records.
10. **Packages:** 6 acyclic production packages + `quizengine.test` cleanly encapsulating visibility and module boundaries.
11. **Threads (Multithreading):**
    - **`quiz-countdown-monitor` (`QuizTimer`):** Daemon `Thread` running a `Runnable` loop that monitors monotonic remaining time via `System.nanoTime()`, triggers low-time alerts (`<= 30s`), sets `volatile boolean expired`, invokes an optional timeout callback via `AtomicBoolean`, and terminates cleanly via `Thread.interrupt()` on `stop()`.
    - **`quiz-console-input-reader` (`QueuedLineReader`):** Single daemon `Thread` isolating blocking `BufferedReader.readLine()` onto a `LinkedBlockingQueue<String>` so `poll(remainingMillis, MILLISECONDS)` enforces strict non-bypassable quiz timeouts without thread leaks.

---

## 3. Ranking & Scoring Specification

- **Percentage Formula:** `(Correct Answers / Total Questions) * 100`, rounded to 2 decimals (`Locale.US`), returning `0.00` safely when `total <= 0` and throwing `IllegalArgumentException` if `correct > total`.
- **Leaderboard Priority (`Score.compareTo`):**
  1. Percentage (descending)
  2. Total Correct Answers (descending)
  3. Completion Duration in seconds (ascending)
  4. Completion Timestamp (ascending), then Attempt ID (ascending)
- **Ties:** Identical percentage, correct answers, and duration share competition rank (e.g., `#1`, `#1`, `#3`).
