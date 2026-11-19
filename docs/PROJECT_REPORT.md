# PROJECT REPORT ON INTERACTIVE QUIZ COMPETITION ENGINE WITH SCORE ARCHIVING

Submitted in partial fulfilment of the requirements for the course  
**Object-Oriented Programming Lab (PCC-CS593)**

**Submitted by (GROUP 5):**
1. **Dip Mondal** — Roll No.: `10200124063`
2. **Samriddha Das** — Roll No.: `102001240**`
3. **Dipanwita Mondal** — Roll No.: `102001240**`
4. **Krishna Halder** — Roll No.: `102001240**`
5. **Rejaul Mali** — Roll No.: `102001240**`
6. **Protap Kumar Halder** — Roll No.: `102001240**`

**Under the guidance of:**  
[Guide / Faculty Name]  
Department of Computer Science and Engineering  
Kalyani Government Engineering College (KGEC)  
Academic Session: [20__–__]

---

## ABSTRACT

The **Interactive Quiz Competition Engine with Score Archiving** is a core Java SE 17+ Command Line Interface (CLI) application developed by **Group 5** for Object-Oriented Programming Lab (**PCC-CS593**). Designed without any database server, web/GUI framework, or external library, the platform loads 36 technical MCQs from `data/questions.txt` into fixed-size `String[4]` option arrays, accepts command-line configuration via `QuizConfig`, enforces strict deadlines using two daemon threads (`quiz-countdown-monitor` and `quiz-console-input-reader`), archives scores in RFC-4180 CSV (`data/scores.txt`), and displays a bordered Top 5 Leaderboard. All 18 classes across 6 acyclic packages compiled with zero warnings and passed **40/40 automated tests** in `quizengine.test.StandaloneTestSuite`.

---

## TABLE OF CONTENTS

| Section | Title | Page (PDF) |
| :--- | :--- | :--- |
| **Preliminary** | Certificate of Approval & Declaration | i |
| **Preliminary** | Abstract | ii |
| **Preliminary** | Table of Contents | iii |
| **Preliminary** | List of Figures, Tables & Abbreviations | iv |
| **Section 1** | 1. Introduction | 1 |
| **Section 2** | 2. Feasibility Study and Planning | 3 |
| **Section 3** | 3. Software Requirements Specification | 5 |
| **Section 4** | 4. System Design | 8 |
| **Section 5** | 5. Implementation Details | 13 |
| **Section 6** | 6. Testing and Verification Results | 15 |
| **Section 7** | 7. Deployment and User Guide | 19 |
| **Section 8** | 8. Conclusion and Future Scope | 20 |
| **References** | References (IEEE Style) | 21 |
| **Appendix** | Submission Checklist & Plagiarism Placeholder | 22 |

## LIST OF FIGURES & TABLES

| Figure | Caption | Page (PDF) |
| :--- | :--- | :--- |
| **Figure 4.1** | Layered System Architecture of the Interactive Quiz Competition Engine | 8 |
| **Figure 4.2** | UML Class Diagram Showing Packages, Inheritance, Interfaces, and Threads | 10 |
| **Figure 4.3** | Flat-File Storage Structure and Validation Pipeline (questions.txt and scores.txt) | 11 |
| **Figure 4.4** | System Use Case Diagram for Participant and Administrator Actors | 12 |
| **Figure 4.5** | End-to-End Application Workflow Diagram | 12 |

| Table | Caption | Page (PDF) |
| :--- | :--- | :--- |
| **Table 2.1** | Feasibility Assessment | 3 |
| **Table 2.2** | Core Java Technology Stack (Zero Databases / Zero External Libraries) | 3 |
| **Table 2.3** | Project Work Plan | 3 |
| **Table 2.4** | Group 5 Member Roster and Module Allocation | 4 |
| **Table 3.1** | Mandatory PCC-CS593 Core Java Concepts Implemented | 6 |
| **Table 4.1** | Layered System Architecture | 8 |
| **Table 4.2** | Java Packages and Class Modules | 9 |
| **Table 4.3** | CSV Score Archive Schema (data/scores.txt) | 11 |
| **Table 5.1** | Custom Exception Handling Matrix | 14 |
| **Table 6.1** | Executed Core-Java Test Cases (40/40 Passed) | 15 |
| **Table 6.2** | Real Bugs Found and Resolved During Independent QA Audit | 17 |

---

# 1. INTRODUCTION

## 1.1 Background
Technical multiple-choice quizzes are widely used in engineering labs, coding competitions, and placement preparation. This application provides an offline, zero-dependency core-Java CLI platform that loads external questions, enforces strict timing, evaluates answers automatically, and archives ranked scores persistently.

## 1.2 Problem Statement
Manual quiz administration and ad-hoc console scripts suffer from hardcoded questions, crashes on invalid inputs or malformed files, blocking console reads bypassing deadlines, and corrupted historical score records.

## 1.3 Objectives
- Load and validate external MCQs from `data/questions.txt` into `String[4]` arrays.
- Validate participant names and assign unique monotonic Attempt IDs.
- Enforce non-bypassable deadlines using multithreaded background monitoring.
- Compute 2-decimal percentages using `Integer`/`Double` wrapper classes and append RFC-4180 CSV rows to `data/scores.txt`.
- Display a bordered Top 5 Leaderboard with competition tie-ranking and verify via 40 automated core-Java tests.

## 1.4 Scope & 1.5 Intended Users
- **Participant (Student):** Registers, configures session count/timer, completes timed quizzes, views immediate feedback, checks previous scores and Top 5 Leaderboard.
- **Quiz Administrator (Faculty / Organizer):** Maintains `data/questions.txt`, launches custom CLI runs (`--questions`, `--time`, `--bank`, `--scores`), and audits `data/scores.txt`.

---

# 2. FEASIBILITY STUDY AND PLANNING

### Group 5 Member Roster and Module Allocation

| Sl. | Student Name | Roll No. | Primary Responsibilities |
| :--- | :--- | :--- | :--- |
| **1** | **Dip Mondal** | `10200124063` | System Architecture, Core QuizEngine, Multithreaded QuizTimer & QA Audit |
| **2** | **Samriddha Das** | `102001240**` | Domain Models (AssessmentItem, Question String[4] Array, Participant, Score) |
| **3** | **Dipanwita Mondal** | `102001240**` | Flat-File Storage (QuizDataStorage, FileManager, RFC-4180 CSV Archiving) |
| **4** | **Krishna Halder** | `102001240**` | Leaderboard Ranking, Tie-Breaking & Command-Line Arguments (QuizConfig) |
| **5** | **Rejaul Mali** | `102001240**` | CLI User Interface (ConsoleUI), InputValidator & QueuedLineReader |
| **6** | **Protap Kumar Halder** | `102001240**` | Question Bank Curation (36 MCQs), StandaloneTestSuite (40 Cases) & Docs |

---

# 3. SOFTWARE REQUIREMENTS SPECIFICATION

## 3.1 Functional Requirements
- **FR-01:** The system shall parse optional command-line arguments (--questions/-q, --time/-t, --bank/-b, --scores/-s, --no-color, --help) via QuizConfig.
- **FR-02:** The system shall display a validated 5-option main menu and reject empty, non-numeric, or out-of-range inputs without crashing.
- **FR-03:** The system shall register participants with validated full names (>=2 letters; rejecting digits, symbols, and punctuation abuse) and assign unique monotonic Attempt IDs.
- **FR-04:** The system shall dynamically load and validate multiple-choice questions from data/questions.txt into fixed-size String[4] option arrays using BufferedReader and StringTokenizer.
- **FR-05:** The system shall skip malformed blocks, duplicate questions, and duplicate options while printing line-numbered diagnostics, and prevent quiz launch if zero valid questions exist.
- **FR-06:** The system shall shuffle questions without repetition, display one framed question card at a time with progress indicators, accept case-insensitive A-D answers, retry invalid choices without losing the question, and provide immediate feedback.
- **FR-07:** The system shall enforce a configurable overall quiz deadline using a background countdown monitor Thread (QuizTimer) and daemon BlockingQueue reader (QueuedLineReader), marking remaining questions unanswered on timeout.
- **FR-08:** The system shall compute total, correct, incorrect, unanswered, final score, and 2-decimal percentage using Integer and Double wrapper classes with zero-division and upper-bound safeguards.
- **FR-09:** The system shall persist completed attempts by appending RFC-4180 escaped CSV rows to data/scores.txt (creating missing directories/headers and guarding missing trailing newlines) without deleting history.
- **FR-10:** The system shall display bordered Previous Scores and a Top 5 Leaderboard ranked by Percentage (desc), Correct Answers (desc), and Shortest Completion Time (asc) with shared competition ranks.

---

# 4. SYSTEM DESIGN

- **Figure 4.1:** ![System Architecture](diagrams/01_system_architecture.png)
- **Figure 4.2:** ![UML Class Diagram](diagrams/02_uml_class_diagram.png)
- **Figure 4.3:** ![File Storage Structure](diagrams/05_file_storage_structure.png)
- **Figure 4.4:** ![Use Case Diagram](diagrams/03_use_case_diagram.png)
- **Figure 4.5:** ![Application Workflow](diagrams/04_application_workflow.png)

---

# 5. IMPLEMENTATION DETAILS

Implemented across **6 acyclic Java packages** (`quizengine.app`, `quizengine.model`, `quizengine.engine`, `quizengine.storage`, `quizengine.io`, `quizengine.exception`) plus `quizengine.test`, demonstrating all 11 mandatory PCC-CS593 core Java concepts with zero databases and zero external libraries.

---

# 6. TESTING AND VERIFICATION RESULTS

| Test ID | Test Scenario | Verified Result | Status |
| :--- | :--- | :--- | :--- |
| **TC01** | Question creation & invariants | Valid String[4] built; blank/short/bad key throw | **PASS** |
| **TC02** | Question parsing via StringTokenizer | Valid 6-line parsed; 2-line short block rejected | **PASS** |
| **TC03** | Correct answer evaluation | evaluateAnswer("B") returns true on key 'B' | **PASS** |
| **TC04** | Incorrect answer evaluation | evaluateAnswer("A","C","D") returns false on 'B' | **PASS** |
| **TC05** | Lowercase answer normalization | "a", " b ", "c", "d" normalized to A-D | **PASS** |
| **TC06** | Invalid answer rejection | "E", "1", "", "AB" throw InvalidQuizDataException | **PASS** |
| **TC07** | Score calculation & wrapper classes | Integer getters & MM:SS (02:15) verified | **PASS** |
| **TC08** | Percentage calculation & 2-decimal format | 8/10 -> 80.00; 2/3 -> 66.67 verified | **PASS** |
| **TC09** | Zero-question protection | calculatePercentage(0, 0) returns 0.00 without NaN/error | **PASS** |
| **TC10** | Timer duration & countdown calculation | Injected clock +75s, +50s (>120s), stop freeze verified | **PASS** |
| **TC11** | Dynamic question file loading | Loaded 36 valid questions from data/questions.txt, 0 diagnostics | **PASS** |
| **TC12** | Missing question file handling | Absent file throws QuizStorageException | **PASS** |
| **TC13** | Malformed, duplicate & empty bank | 1 valid + 3 diagnostics; empty throws InvalidQuizDataException | **PASS** |
| **TC14** | Score file auto-creation | Auto-creates nested dir, CSV header & first data row | **PASS** |
| **TC15** | Score appending & CSV field escaping | Appends history intact; commas/quotes round-trip; next ID A003 | **PASS** |
| **TC16** | Previous score retrieval & corrupt recovery | Loads 2 valid rows + 2 line-numbered diagnostics | **PASS** |
| **TC17** | Leaderboard sorting & Top 5 cutoff | 10/10 (100%) ranks #1 over 5/5 (100%); Top 5 cutoff enforced | **PASS** |
| **TC18** | Tie-breaking & shared competition ranks | Three 90% scores (150s, 100s, 100s) ranked #1, #1, #3 | **PASS** |
| **TC19** | Empty leaderboard handling | Absent archive returns empty snapshot & friendly message | **PASS** |
| **TC20** | Invalid menu input rejection | Valid 1..5 accepted; "abc", "0", "6", "" rejected | **PASS** |
| **TC21** | Participant name validation | Spaces/periods normalized; digits/symbols/blank rejected | **PASS** |
| **TC22** | Non-repeating question selection | 15 selected from 36 contain 15 unique normalized prompts | **PASS** |
| **TC23** | Strict non-bypassable quiz timeout | Blocked PipedInputStream aborts at ~1s with 3 unanswered | **PASS** |
| **TC24** | End-to-end integration & restart persistence | Session 1 archived score persists across Session 2 fresh Main | **PASS** |
| **TC25** | File permission error handling | Read-only scores.txt throws QuizStorageException | **PASS** |
| **TC26** | Append without trailing newline | Missing final \n recovered cleanly; both rows intact | **PASS** |
| **TC27** | UTF-8 BOM & leading blank lines | BOM stripped; header skipped cleanly with 0 false warnings | **PASS** |
| **TC28** | Corrupted CSV quotes & tampered % | "Dip"GARBAGE, unclosed quote, 99% tamper rejected (3 diags) | **PASS** |
| **TC29** | Percentage upper-bound guard | (12, 10) throws IllegalArgumentException; null/neg yield 0.0 | **PASS** |
| **TC30** | Duplicate & malformed Attempt IDs | Duplicate A001 skipped; next ID advances past bad A005 to A006 | **PASS** |
| **TC31** | Duplicate participants across restart | Dip Mondal 2 runs + 1 post-restart saved as A001-A003 & ranked | **PASS** |
| **TC32** | Punctuation-abuse name rejection | "Li", "Dr. A. K. Sen" pass; "A.", "A...", "--" rejected | **PASS** |
| **TC33** | Malformed question edges & binary UTF-8 | Dup options, B) before A), 7-line diagnosed; binary throws | **PASS** |
| **TC34** | QuizEngine caller list deduplication | selectQuestions filters duplicate prompts & null entries | **PASS** |
| **TC35** | QuizTimer overflow & invalid limits | 0L, -10L, Long.MAX_VALUE rejected with IllegalArgumentException | **PASS** |
| **TC36** | Question String[] defensive copy | Mutating input array or getOptions() cannot alter internal state | **PASS** |
| **TC37** | Command-line arguments parsing (QuizConfig) | Valid flags parsed; unknown/missing/bad numeric flags rejected | **PASS** |
| **TC38** | Background countdown monitor Thread | quiz-countdown-monitor fires timeout callback within 1s & stops | **PASS** |
| **TC39** | End-to-end execution via CLI args | -q 1 -t 90 -b ... -s ... --no-color applied & saved cleanly | **PASS** |
| **TC40** | Cross-package inheritance & polymorphism | AssessmentItem/QuizDataStorage/QuizException dispatch verified | **PASS** |

### Captured Terminal Outputs
```text
$ java -cp out quizengine.app.Main --questions 5 --time 180 --no-color
[INFO] CLI configuration applied: questions=5, time=03:00, bank=data/questions.txt, scores=data/scores.txt

+==============================================================+
|              TECHNICAL QUIZ COMPETITION ENGINE               |
|             Group 5  |  OOP Lab (PCC-CS593) CLI              |
+==============================================================+
|  [1] Start New Quiz                   Timed MCQ competition  |
|  [2] View Leaderboard                 Top 5 ranked attempts  |
|  [3] View Previous Scores              Full attempt archive  |
|  [4] View Quiz Instructions           Rules & scoring guide  |
|  [5] Exit                                Save & quit safely  |
+--------------------------------------------------------------+
Enter participant full name: [ERROR] Invalid name 'A...'. Use at least 2 letters...
Enter participant full name: [OK] Registered Dip Mondal (Attempt ID: A005)

+--------------------------------------------------------------+
| Question 1 of 5   [####----------------]  20%                |
| Remaining: 03:00  |  Elapsed: 00:00  |  Score: 0/0           |
+--------------------------------------------------------------+
Your answer (A/B/C/D): [ERROR] Invalid choice 'X'. Please enter A, B, C, or D.
Your answer (A/B/C/D):   [CORRECT] Great job! Your answer is right.
```
```text
+==============================================================+
|                        QUIZ COMPLETED                        |
|             Performance Rating: Keep Practicing              |
+==============================================================+
|  Attempt ID        : A005          Participant : Dip Mondal  |
|  Total Questions   : 5   | Correct: 1 | Incorrect: 4 | Un: 0 |
|  Final Score       : 1/5           Percentage  : 20.00%      |
|  Accuracy Bar      : [#####-------------------] 20.00%       |
|  Time Taken        : 00:00                                   |
+==============================================================+
|                  YOUR RESULT HAS BEEN SAVED                  |
+==============================================================+

--- FRESH JVM RESTART (SESSION 2): TOP 5 LEADERBOARD ---
+------+--------+------------------------+---------+----------+--------+
| Rank | ID     | Participant            | Score   | Percent  | Time   |
+------+--------+------------------------+---------+----------+--------+
| #1   | A003   | Priya Sharma           | 9/10    |   90.00% | 01:44  |
| #2   | A001   | Ananya Roy             | 9/10    |   90.00% | 01:55  |
| #3   | A002   | Rohan Sen              | 8/10    |   80.00% | 02:12  |
| #4   | A004   | Arjun Das              | 7/10    |   70.00% | 02:28  |
| #5   | A005   | Dip Mondal             | 1/5     |   20.00% | 00:00  |
+------+--------+------------------------+---------+----------+--------+
```

---

# 7. DEPLOYMENT AND USER GUIDE

```bash
mkdir -p out out-tests
javac --release 17 -Xlint:all -Werror -d out \
  src/quizengine/exception/*.java src/quizengine/model/*.java \
  src/quizengine/storage/*.java src/quizengine/io/*.java \
  src/quizengine/engine/*.java src/quizengine/app/*.java
javac --release 17 -cp out -d out-tests tests/quizengine/test/StandaloneTestSuite.java
java -cp out:out-tests quizengine.test.StandaloneTestSuite
java -cp out quizengine.app.Main --questions 5 --time 180
```

---

# 8. CONCLUSION AND FUTURE SCOPE

Group 5 successfully delivered a pure core-Java SE CLI quiz competition engine for **PCC-CS593** verified with **40/40 passing tests** and zero compiler warnings. Future work includes topic/difficulty metadata tags, post-quiz explanations, additional `AssessmentItem` subclasses, and standard Java Socket classroom multiplayer mode.

---

# REFERENCES

[1] Kalyani Government Engineering College (KGEC), Final Project Report Detailed Guide, SRS Tutorial, and System Design Description Tutorial, Department of Computer Science and Engineering course material.

[2] Oracle Corporation, The Java Language Specification, Java SE 17 Edition. Redwood Shores, CA, USA: Oracle, 2021. [Online]. Available: https://docs.oracle.com/javase/specs/jls/se17/html/index.html

[3] Oracle Corporation, Java Platform, Standard Edition 17 API Specification (java.io, java.nio.file, java.time, java.util.concurrent). Redwood Shores, CA, USA: Oracle, 2021. [Online]. Available: https://docs.oracle.com/en/java/javase/17/docs/api/

[4] J. Bloch, Effective Java, 3rd ed. Boston, MA, USA: Addison-Wesley Professional, 2018.

[5] R. C. Martin, Clean Code: A Handbook of Agile Software Craftsmanship. Upper Saddle River, NJ, USA: Prentice Hall, 2008.

[6] Y. Shafranovich, "Common Format and MIME Type for Comma-Separated Values (CSV) Files," Internet Engineering Task Force (IETF), RFC 4180, Oct. 2005. [Online]. Available: https://datatracker.ietf.org/doc/html/rfc4180

[7] B. Goetz, T. Peierls, J. Bloch, J. Bowbeer, D. Holmes, and D. Lea, Java Concurrency in Practice. Upper Saddle River, NJ, USA: Addison-Wesley Professional, 2006.

[8] H. Schildt, Java: The Complete Reference, 12th ed. New York, NY, USA: McGraw-Hill Education, 2022.

---

# SUBMISSION CHECKLIST & PLAGIARISM PLACEHOLDER

- **Dip Mondal (`10200124063`)** is populated first everywhere; fill in the remaining five `102001240**` roll numbers.
- Replace `[Guide / Faculty Name]`, `[Head of Department Name]`, and `[20__–__]`.
- Obtain genuine signatures on Page `i` and attach the genuine plagiarism report certificate from the institution-approved checker.
