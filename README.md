 YOLO — Your Own Lab Orchestrator

**Automated C++ Code Assessment & Plagiarism Detection Portal**
*Software Engineering Course Project*

## What is this project?

In programming labs, instructors have to (1) check whether each student's code gives the correct output, and (2) check whether students copied from each other. Doing both by hand is slow and error-prone.

YOLO is a web portal that does both automatically:

- **Student side:** paste C++ code, get an instant verdict (`AC`, `WA`, `TLE`, `CE`, `RE`).
- **Instructor side:** create assignments with test cases, then run a similarity check that compares every pair of students and highlights suspicious pairs for **manual review**.

> The system never says "plagiarized" or "cheater". It only says **FLAGGED FOR REVIEW**, and the instructor makes the final call after viewing both codes side by side.

## Features

| Area | What it does |
| :--- | :--- |
| Assignments | Title, description, time limit, test input/output, optional starter code |
| Judge | Compiles with `g++`, runs with a time limit, compares output |
| Verdicts | `AC` Accepted, `WA` Wrong Answer, `TLE` Time Limit Exceeded, `CE` Compilation Error, `RE` Runtime Error |
| Plagiarism check | Token-based similarity between all student pairs (latest submission per student) |
| Starter code | Boilerplate given by the instructor is ignored, so shared template code is not counted as copying |
| Review UI | Filter by flagged / minimum similarity, and "Compare Code" side-by-side view |
| Storage | Saved to a JSON file (`data/yolo_data.json`), so data survives restarts |

## How the similarity check works (short version)

1. **Tokenize:** remove comments and `#include`; rename all variable names to `ID` and numbers to `NUM`. So renaming variables does not fool the checker.
2. **Winnowing (k=5, w=4):** break the token stream into groups of 5 tokens, hash each, and keep the smallest hash in each window of 4. These are the code's "fingerprints".
3. **Score = 65% Sørensen–Dice (fingerprints) + 35% LCS (token order).**
4. **Flag** if score is **>= 75%**.

## Tech stack

- Java 21, Spring Boot 3.2.5 (REST APIs + static file hosting)
- HTML + Bootstrap 5 + vanilla JavaScript (frontend)
- JUnit 5 (testing), Maven (build)
- `g++` on the host machine (to compile student code)

## Project structure

```
src/main/java/com/yolo/portal/
├── YoloApplication.java
├── controller/PortalController.java     # REST endpoints + JSON persistence
├── model/{Assignment, Submission, ComparisonResult}.java
└── service/
    ├── JudgeService.java                # compile + run + verdict
    └── PlagiarismService.java           # tokenize, winnow, similarity
src/main/resources/static/               # index.html, instructor.html, student.html
src/test/java/com/yolo/portal/           # JudgeServiceTest, PlagiarismServiceTest, PerformanceTest
docs/                                    # SRS, design diagrams, testing & CFG
```

## How to run

**Requirements:** JDK 21, Maven, and `g++` installed and available on PATH.

```bash
git clone https://github.com/harjasbb07-eng/YOLO.git
cd YOLO
mvn spring-boot:run
```

Open **http://localhost:8080** and choose Instructor or Student portal.

Run all tests:

```bash
mvn test
```

## REST API

| Method | Endpoint | Purpose |
| :--- | :--- | :--- |
| POST | `/api/assignments` | Create an assignment |
| GET | `/api/assignments` | List assignments |
| POST | `/api/submissions` | Submit code, returns verdict |
| GET | `/api/assignments/{id}/plagiarism` | Pairwise similarity report (sorted high to low) |

## Known limitations

- Only C++ is supported.
- Student code runs directly on the host with a timeout. There is **no Docker isolation yet**, so this must only be used in a trusted lab setting.
- Data is stored in a JSON file, not a real database.
- No login/authentication.

## Future work

Docker sandbox with memory limits, more languages (C, Java, Python), database storage, authentication, file upload, and an inverted fingerprint index for very large classes.

## Documentation

- `docs/SRS_Document.md` — requirements (IEEE 830 style)
- `docs/System_Design_Diagrams.md` — use case, DFD, sequence, class diagrams
- `docs/Testing_and_CFG.md` — COCOMO, white-box testing, CFG, risk table
- `Project_Report.md` — summary report
