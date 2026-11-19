# Automated Test & Independent QA Audit Report (PCC-CS593)

**Project:** Interactive Quiz Competition Engine with Score Archiving  
**Course:** Object-Oriented Programming Lab (PCC-CS593) — Group 5  
**Execution Date:** 2026-11-18  
**JDK Verified:** Eclipse Temurin OpenJDK 25.0.4.1 LTS (compiled with `javac --release 17 -Xlint:all -Werror`)  
**Test Framework:** Independent Core-Java Test Runner (`tests/quizengine/test/StandaloneTestSuite.java`) — Zero Maven, Zero JUnit, Zero External Libraries  
**Build & Test Status:** **PASS — 40 executed, 40 passed, 0 failed**

> **Isolation Guarantee:** All file I/O and integration tests execute inside isolated temporary directories (`Files.createTempDirectory`) that are deleted after each test case, keeping `data/scores.txt` untouched during test runs.

---

## 1. Complete 40-Case Core-Java Test Matrix

| ID | Test Description | Input / Scenario | Expected Output | Actual Output | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **TC01** | Question object creation & invariants | Valid 4-opt `String[]`; blank prompt; 2 opts; key `'E'` | Valid built; 3 invalid calls throw `IllegalArgumentException` | Exact match | **PASS** |
| **TC02** | Question parsing via String tokenization | 6-line valid block; 2-line short block | Parsed `Question`; short block throws `InvalidQuizDataException` | Exact match | **PASS** |
| **TC03** | Correct answer evaluation | `evaluateAnswer("B")` on key `'B'` | `true` | `true` | **PASS** |
| **TC04** | Incorrect answer evaluation | `evaluateAnswer("A")`, `"C"`, `"D"` on `'B'` | `false` for all 3 | `false` for all 3 | **PASS** |
| **TC05** | Lowercase answer normalization | `"a"`, `" b "`, `"c"`, `"d"` | Normalized `A`–`D`; evaluates `true` | Exact match | **PASS** |
| **TC06** | Invalid answer rejection | `"E"`, `"1"`, `""`, `"AB"` | Throws `InvalidQuizDataException` | All 4 rejected | **PASS** |
| **TC07** | Score calculation & wrapper classes | `10` tot, `8` corr, `1` inc, `1` unans, `135s` | Exact `Integer` getters; `"02:15"` | Exact match | **PASS** |
| **TC08** | Percentage calculation & 2-decimal format | `8/10` and `2/3` | `80.0`, `66.67`, `"66.67"` | Exact match | **PASS** |
| **TC09** | Zero-question protection | `calculatePercentage(0, 0)` | `0.0` (`"0.00"`), no NaN/error | Exact match | **PASS** |
| **TC10** | Timer duration & countdown calculation | Injected clock `+75s`, `+50s` (>120s), stop | `"01:15"`, `45s` left, expired, frozen | Exact match | **PASS** |
| **TC11** | Dynamic question file loading | Real `data/questions.txt` | >=30 valid, 0 diagnostics | 36 valid, 0 diags | **PASS** |
| **TC12** | Missing question file handling | Absent path | Throws `QuizStorageException` | Exact match | **PASS** |
| **TC13** | Malformed, duplicate & empty bank | 1 valid, 1 dup, 1 bad key, 1 short; empty | 1 valid + 3 diags; empty throws | Exact match | **PASS** |
| **TC14** | Score file auto-creation | Missing `sub/scores.txt` | Auto-creates dir, CSV header + row | Exact match | **PASS** |
| **TC15** | Score appending & CSV escaping | 2 rows incl. commas & quotes | History intact; next ID `A003` | Exact match | **PASS** |
| **TC16** | Previous score retrieval & corrupt recovery | 2 valid + 2 corrupt CSV rows | 2 valid + 2 diagnostics | Exact match | **PASS** |
| **TC17** | Leaderboard sorting & Top 5 cutoff | 6 scores incl. `10/10` vs `5/5` (100%) | Top 5; `10/10` #1 ahead of `5/5` #2 | Exact match | **PASS** |
| **TC18** | Tie-breaking & shared competition ranks | Three 90%: `150s`, `100s`, `100s` | Faster pair `#1, #1`; slower `#3` | Ranks `#1, #1, #3` | **PASS** |
| **TC19** | Empty leaderboard handling | Absent score archive | Empty snapshot & friendly message | Exact match | **PASS** |
| **TC20** | Invalid menu input rejection | `"1"`, `" 5 "`, `"abc"`, `"0"`, `"6"`, `""` | Valid parsed; 4 invalid rejected | Exact match | **PASS** |
| **TC21** | Participant name validation | `"  Dip  Mondal "`, `"User123"`, `"Bad@"` | Valid normalized; invalid rejected | Exact match | **PASS** |
| **TC22** | Non-repeating question selection | Select 15 from 36 questions | 15 unique prompts, 0 duplicates | Exact match | **PASS** |
| **TC23** | Strict non-bypassable quiz timeout | Blocked `PipedInputStream`, 1s limit | Aborts <3s; 0 corr, 3 unanswered | Aborted at ~1s | **PASS** |
| **TC24** | End-to-end integration & restart persistence | Session 1 quiz -> Session 2 fresh `Main` | Score persists across restart | Verified | **PASS** |
| **TC25** | Read-only file permission handling | Read-only `scores.txt` | Throws `QuizStorageException` | Exact match | **PASS** |
| **TC26** | Append without trailing newline | Existing `scores.txt` missing final `\n` | Both rows intact, 0 diagnostics | Exact match | **PASS** |
| **TC27** | UTF-8 BOM & leading blank lines | `\uFEFF` in both data files | BOM stripped; header skipped cleanly | Exact match | **PASS** |
| **TC28** | Corrupted CSV quotes & tampered % | `"Dip"GARBAGE`, unclosed quote, `99%` | 1 valid + 3 diagnostics | Exact match | **PASS** |
| **TC29** | Percentage upper-bound guard | `(12, 10)`, `(null, 10)`, `(-2, 10)` | `(12, 10)` throws; others `0.0` | Exact match | **PASS** |
| **TC30** | Duplicate & malformed Attempt IDs | Duplicate `A001` + malformed `A005` | 1 valid, 2 diags, next ID `A006` | Exact match | **PASS** |
| **TC31** | Duplicate participants across restart | `"Dip Mondal"` 2 runs + 1 post-restart | Distinct `A001`–`A003`, all ranked | Exact match | **PASS** |
| **TC32** | Name punctuation-abuse rejection | `"Li"`, `"Dr. A. K. Sen"`, `"A."`, `"A..."`, `"--"` | Valid pass; 4 abuse names rejected | Exact match | **PASS** |
| **TC33** | Malformed question edges & invalid UTF-8 | Dup opts, `B)` before `A)`, 7 lines, binary | 1 valid + 3 diags; binary throws | Exact match | **PASS** |
| **TC34** | `QuizEngine` caller list deduplication | `[q1, null, q1Dup, q2]` | Returns 2 unique non-null questions | Exact match | **PASS** |
| **TC35** | `QuizTimer` overflow & invalid limits | `0L`, `-10L`, `Long.MAX_VALUE` | Throws `IllegalArgumentException` | All 3 rejected | **PASS** |
| **TC36** | `Question` `String[]` array & defensive copy | Mutate input `String[]` & returned `getOptions()` | Internal `String[4]` unaffected | Encapsulation verified | **PASS** |
| **TC37** | Command-line arguments parsing (`QuizConfig`) | `--questions 15 --time 180 --bank ... --scores ... --no-color`, `--help`, invalid flags | Valid parsed; 4 invalid args throw `InvalidQuizDataException` | Exact match | **PASS** |
| **TC38** | Background countdown monitor `Thread` | `QuizTimer(1L)` with `CountDownLatch` timeout callback | Daemon thread alive; callback fires on expiry; stops cleanly | Fired within 1s | **PASS** |
| **TC39** | End-to-end execution via CLI args | `Main` launched with `-q 1 -t 90 -b ... -s ... --no-color` | Defaults `01:30` & `1/1` applied; score archived to custom path | Exact match | **PASS** |
| **TC40** | Cross-package inheritance & polymorphism | `AssessmentItem` -> `Question`, `QuizDataStorage` -> `FileManager`, `QuizException` subclasses | Dynamic dispatch & error codes verified across packages | Exact match | **PASS** |

---

## 2. Live CLI Execution Verification

Executed directly via `java -cp out quizengine.app.Main --questions 5 --time 180 --no-color` and restarted in a second JVM (`docs/outputs/session1_quiz_run.txt` and `docs/outputs/session2_restart_verify.txt`):
- Verified `--help` prints full CLI usage and exits cleanly.
- Verified `--questions 5 --time 180 --no-color` configures default session count (`5`) and countdown duration (`03:00`).
- Verified `A005,Dip Mondal,1,5,20.00,0,2026-11-18T19:30:02` persisted cleanly to `data/scores.txt` and reloaded intact in Previous Scores and Top 5 Leaderboard across JVM restart.
