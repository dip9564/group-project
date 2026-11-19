# Interactive Quiz Competition Engine with Score Archiving

**Course:** Object-Oriented Programming Lab (**PCC-CS593**) — Group-Based Innovative Assignment  
**Group:** Group 5  
**Language:** Pure Core Java SE (Target Release: Java 17+, verified on Temurin OpenJDK 25.0.4.1 LTS)  
**Interface:** Cross-Platform 7-Bit ASCII Command Line Interface (CLI)  
**Storage:** Flat Text Files (`data/questions.txt`, `data/scores.txt`) — Zero Databases, Zero External Libraries  

---

## Mandatory Core Java Concepts Implemented (PCC-CS593 Viva Map)

| Mandatory Concept | Where Implemented | Purpose in Application |
| :--- | :--- | :--- |
| **Objects & Classes** | 18 modular classes across 6 packages (`Question`, `Participant`, `Score`, `QuizEngine`, `FileManager`, `Leaderboard`, `QuizTimer`, `QuizConfig`, etc.) | Clean single-responsibility domain modeling without monolithic code. |
| **Methods** | Overloaded constructors (`Question`, `QuizTimer`, `Main`, `QuizEngine`), static utility/factory methods (`Score.calculatePercentage`, `Score.fromArchivedRecord`, `QuizConfig.fromArgs`), instance methods | Encapsulated operations, reusable parsing, and deterministic scoring. |
| **Inheritance & Polymorphism** | `Question extends AssessmentItem`; `QuizStorageException` & `InvalidQuizDataException` extend `QuizException`; `FileManager implements QuizDataStorage`; `Score implements Comparable<Score>` | Dynamic method dispatch on `AssessmentItem`, polymorphic storage abstraction, structured exception hierarchy, and natural score sorting. |
| **Arrays** | `String[] options` (length 4) & `char[] OPTION_LABELS` in `Question`; `String[] parsedOptions` in `FileManager`; `int[] ranks` in `Leaderboard`/`ConsoleUI`; `String[] args` in `Main`/`QuizConfig` | Fixed-size 4-option MCQ storage with defensive copying (`Arrays.copyOf`), competition rank array, and CLI arguments. |
| **Strings & String Manipulation** | `FileManager` (`StringTokenizer`, `substring`, `indexOf`, `trim`, RFC-4180 CSV parser/escaper), `InputValidator` (regex & whitespace normalization), `ConsoleUI` (`wrapText`, `truncate`, `buildProgressBar`) | Robust text parsing, input sanitization, CSV safety, and formatted CLI output. |
| **Command-Line Arguments** | `Main.main(String[] args)` delegating to `QuizConfig.fromArgs(args)` | Startup configuration via `--questions`, `--time`, `--bank`, `--scores`, `--no-color`, and `--help`. |
| **Wrapper Classes** | `Integer` & `Double` in `Score`, `InputValidator`, `QuizConfig`, and `FileManager` (`Integer.valueOf`, `Double.valueOf`, `Double.compare`, `Integer.compare`, `Double.isNaN`) | Score calculation, null-safe parsing, bounds validation, and leaderboard comparison. |
| **Exception Handling** | `QuizException`, `QuizStorageException`, `InvalidQuizDataException`, try-with-resources, zero empty catch blocks | Clean recovery from invalid input, missing/empty/binary files, read-only archives, and malformed CSV rows. |
| **Input/Output File Operations** | `FileManager` (`BufferedReader`, `BufferedWriter`, `Files`, `SeekableByteChannel`) | Dynamic UTF-8 question bank loading (with BOM strip) and append-only RFC-4180 CSV score archiving (with trailing-newline guard). |
| **Packages** | 6 acyclic packages: `quizengine.app`, `quizengine.model`, `quizengine.engine`, `quizengine.storage`, `quizengine.io`, `quizengine.exception` (+ `quizengine.test`) | Textbook modular architecture with zero circular package dependencies. |
| **Threads (Multithreading)** | `QuizTimer` (`quiz-countdown-monitor` daemon `Thread` + `Runnable` + `volatile`/`AtomicBoolean`) and `QueuedLineReader` (`quiz-console-input-reader` daemon `Thread` + `BlockingQueue`) | Real-time background countdown monitoring and non-bypassable strict quiz timeouts. |

---

## Project Structure

```text
.
├── src/
│   └── quizengine/
│       ├── app/
│       │   ├── Main.java                       # Entry point, CLI args, menu controller
│       │   └── QuizConfig.java                 # Command-line argument parser
│       ├── model/
│       │   ├── AssessmentItem.java             # Abstract assessment superclass
│       │   ├── Question.java                   # Concrete MCQ using String[4] array
│       │   ├── Participant.java                # Registered participant & Attempt ID
│       │   ├── Score.java                      # Integer/Double metrics & Comparable
│       │   └── LoadResult.java                 # Generic records + diagnostics wrapper
│       ├── engine/
│       │   ├── QuizEngine.java                 # Session orchestration & evaluation
│       │   ├── QuizTimer.java                  # Monotonic timer + background monitor Thread
│       │   └── Leaderboard.java                # Multi-criteria Top 5 ranking & ties
│       ├── storage/
│       │   ├── QuizDataStorage.java            # Persistence interface abstraction
│       │   └── FileManager.java                # Flat-file & RFC-4180 CSV I/O manager
│       ├── io/
│       │   ├── ConsoleUI.java                  # Cross-platform 7-bit ASCII CLI renderer
│       │   ├── InputValidator.java             # Menu, name & A-D validation
│       │   └── QueuedLineReader.java           # Daemon reader Thread + BlockingQueue
│       └── exception/
│           ├── QuizException.java              # Base checked exception
│           ├── QuizStorageException.java       # Storage/I/O checked exception
│           └── InvalidQuizDataException.java   # Malformed data/input checked exception
├── data/
│   ├── questions.txt                           # 36 technical MCQs across 6 CS domains
│   └── scores.txt                              # Persistent RFC-4180 CSV score archive
├── tests/
│   └── quizengine/
│       └── test/
│           └── StandaloneTestSuite.java        # Independent core-Java test runner (40 cases)
├── docs/
│   ├── PROJECT_REPORT.docx                     # Formatted academic report (Word)
├── README.md
└── .gitignore
```

---

## Build, Test, and Run (Pure `javac` / `java` — Zero External Tools)

Run all commands from the **project root directory**:

```bash
# macOS (if Temurin JDK is not already on PATH):
export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# 1. Compile all 6 application packages (Java 17 target, strict warnings)
mkdir -p out out-tests
javac --release 17 -Xlint:all -Werror -d out \
  src/quizengine/exception/*.java \
  src/quizengine/model/*.java \
  src/quizengine/storage/*.java \
  src/quizengine/io/*.java \
  src/quizengine/engine/*.java \
  src/quizengine/app/*.java

# 2. Compile and run the independent 40-case core-Java test suite
javac --release 17 -Xlint:all -Werror -cp out -d out-tests \
  tests/quizengine/test/StandaloneTestSuite.java
java -cp out:out-tests quizengine.test.StandaloneTestSuite

# 3. Run the application with default settings
java -cp out quizengine.app.Main

# 4. Run with Command-Line Arguments (examples)
java -cp out quizengine.app.Main --help
java -cp out quizengine.app.Main --questions 5 --time 180
java -cp out quizengine.app.Main -q 10 -t 300 -b data/questions.txt -s data/scores.txt --no-color
```

> **Windows Note:** Replace `:` with `;` in the `-cp` flag (`java -cp out;out-tests quizengine.test.StandaloneTestSuite`) and use backslashes or forward slashes for paths.

---

## Documentation Index

- [User Guide](docs/USER_GUIDE.md) — Menu guide, CLI arguments, question format, and troubleshooting.
- [Technical Documentation](docs/TECHNICAL_DOCUMENTATION.md) — Package DAG, OOP & PCC-CS593 concept map, algorithms, and multithreading design.
- [Test Report](docs/TEST_REPORT.md) — Verified 40-case core-Java automated test matrix and QA audit summary.
- [Academic Project Report](docs/PROJECT_REPORT.md) | [DOCX](docs/PROJECT_REPORT.docx) | [PDF](docs/PROJECT_REPORT.pdf)
