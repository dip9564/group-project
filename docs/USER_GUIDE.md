# User Guide: Interactive Quiz Competition Engine

**Course:** Object-Oriented Programming Lab (PCC-CS593) — Group 5  

## 1. Launching the Application

Run from the **project root directory** after compiling with `javac` (see [README.md](../README.md)):

```bash
# Launch with defaults (10 questions, 05:00 time limit, data/questions.txt, data/scores.txt)
java -cp out quizengine.app.Main

# Launch with custom Command-Line Arguments
java -cp out quizengine.app.Main --questions 5 --time 180
java -cp out quizengine.app.Main -q 10 -t 300 -b data/questions.txt -s data/scores.txt --no-color

# Display all CLI options
java -cp out quizengine.app.Main --help
```

### Supported Command-Line Arguments

| Flag | Short | Default | Description |
| :--- | :--- | :--- | :--- |
| `--questions <N>` | `-q <N>` | `10` | Default number of questions per quiz (`1`–`500`). |
| `--time <seconds>` | `-t <seconds>` | `300` | Default overall quiz duration in seconds (`30`–`3600`). |
| `--bank <path>` | `-b <path>` | `data/questions.txt` | Path to the external plain-text question bank. |
| `--scores <path>` | `-s <path>` | `data/scores.txt` | Path to the persistent RFC-4180 CSV score archive. |
| `--no-color` | — | auto-detect | Forces plain 7-bit ASCII output without ANSI color codes. |
| `--help` | `-h` | — | Prints CLI usage instructions and exits cleanly. |

---

## 2. Main Menu Navigation

```text
+==============================================================+
|              TECHNICAL QUIZ COMPETITION ENGINE               |
|             Group 5  |  OOP Lab (PCC-CS593) CLI              |
+==============================================================+
|                                                              |
|  [1] Start New Quiz                   Timed MCQ competition  |
|  [2] View Leaderboard                 Top 5 ranked attempts  |
|  [3] View Previous Scores              Full attempt archive  |
|  [4] View Quiz Instructions           Rules & scoring guide  |
|  [5] Exit                                Save & quit safely  |
|                                                              |
+--------------------------------------------------------------+
Enter your choice:
```

- **1. Start New Quiz:** Validates `data/questions.txt`, registers your full name with a unique sequential Attempt ID (`A001`, `A002`, ...), lets you press Enter for CLI defaults or type custom question count/time limit, presents shuffled non-repeating question cards with live ASCII progress bars and a background countdown timer, displays immediate `[CORRECT]`/`[INCORRECT]` feedback, and archives your final score card.
- **2. View Leaderboard:** Displays the bordered Top 5 Leaderboard sorted by **Percentage (desc) -> Correct Answers (desc) -> Shortest Time (asc) -> Timestamp (asc)**. Tied scores share competition rank (`#1, #1, #3`).
- **3. View Previous Scores:** Displays all valid archived attempts in a bordered table and prints warnings if any corrupted CSV lines were skipped.
- **4. View Quiz Instructions:** Shows in-app rules, CLI flags, timer behavior, and scoring formulas.
- **5. Exit:** Exits cleanly while preserving all archived scores.

---

## 3. Question Bank Format (`data/questions.txt`)

Each question is a 6-line block separated by a blank line:

```text
What is Java?
A) Programming Language
B) Operating System
C) Database
D) Browser
A
```

Rules enforced automatically:
- 4 distinct options labeled `A)`, `B)`, `C)`, `D)` in order (stored in `Question`'s `String[4]` array).
- Single-letter answer key (`A`–`D`).
- Duplicate questions, duplicate options within one question, or malformed blocks are reported with line numbers and skipped safely.

---

## 4. Troubleshooting

| Issue | Cause | Solution |
| :--- | :--- | :--- |
| `[ERROR] Question bank file not found` | Launched outside project root or wrong `--bank` path. | Run from project root or pass `-b <valid_path>`. |
| `[ERROR] Unknown command-line argument` | Misspelled CLI flag. | Run `java -cp out quizengine.app.Main --help`. |
| `[ERROR] Permission denied` | Read-only `data/scores.txt` or `data/` directory. | Grant write permissions (`chmod u+w data/scores.txt`). |
| Plain text without colors | Redirected I/O, `--no-color`, `NO_COLOR`, or legacy Windows CMD. | Expected cross-platform fallback; all borders remain crisp 7-bit ASCII. |
