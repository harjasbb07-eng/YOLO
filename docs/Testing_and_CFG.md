# Project Planning, Testing, Control Flow Graph & Risk Analysis

## 1. Project Planning & Effort Estimation (COCOMO)
We applied the Basic COCOMO model (Organic Mode) to estimate the theoretical baseline effort for Project YOLO:
* **Estimated Project Size:** 3.5 KLOC (across backend services, controllers, models, JUnit tests, frontend HTML/JS, and documentation).
* **Organic Mode Constants:** `a = 2.4`, `b = 1.05`
* **Calculation:** `Effort (E) = a * (KLOC)^b = 2.4 * (3.5)^1.05 ≈ 8.95 Person-Months`

## 2. White-Box Testing: Control Flow Graph (CFG) & Cyclomatic Complexity
We performed white-box basis path analysis on `JudgeService.evaluateCppCode(String sourceCode, String testInput, String expectedOutput, int timeLimitSeconds)`.

### Decision Points (Predicate Nodes) in `JudgeService.evaluateCppCode`
1. **Decision 1 (Source Code Guard):** `if (sourceCode == null || sourceCode.isBlank())` -> returns `"CE"`.
2. **Decision 2 (Time Limit Normalization):** Ternary/check `timeLimitSeconds > 0 ? timeLimitSeconds : default` ensuring a valid timeout.
3. **Decision 3 (Compilation Exit Code):** `if (compileProcess.exitValue() != 0)` -> returns `"CE"`.
4. **Decision 4 (Test Input Check):** `if (testInput != null && !testInput.isEmpty())` -> writes `testInput` to process `stdin`.
5. **Decision 5 (Execution Timeout Check):** `if (!finished)` from `runProcess.waitFor(timeLimitSeconds, TimeUnit.SECONDS)` -> calls `destroyForcibly()` and returns `"TLE"`.
6. **Decision 6 (Runtime Exit Code Check):** `if (runProcess.exitValue() != 0)` -> returns `"RE"`.
7. **Decision 7 (Stdout Read Loop):** `while ((line = reader.readLine()) != null)` reading standard output stream.
8. **Decision 8 (Null Output Guard):** Null check during output/expected string normalization.
9. **Decision 9 (Output Verdict Comparison):** `if (actualOutput.equals(expectedOutput))` -> returns `"AC"`, else returns `"WA"`.
10. **Decision 10 (Exception Handler):** `catch (Exception e)` block returning error verdict on process/IO failure.
11. **Decision 11 (Finally File Cleanup):** Conditional existence checks before deleting temporary `temp_sub_<UUID>.cpp` and compiled executable files.

### Cyclomatic Complexity Calculation
* **Number of Predicate / Decision Points (D):** `11`
* **Formula:** `V(G) = D + 1 = 11 + 1 = 12` (equivalently `V(G) = E - N + 2P = 12`)
* This gives **12 independent basis paths** covering blank input, compilation error (`CE`), timeout (`TLE`), runtime crash (`RE`), wrong answer (`WA`), accepted solution (`AC`), and cleanup paths.

### Control Flow Graph (CFG) Diagram for `JudgeService.evaluateCppCode`
```mermaid
flowchart TD
    Start([Start: evaluateCppCode]) --> D1{1. sourceCode null or blank?}
    D1 -- Yes --> RetCE1([Return 'CE'])
    D1 -- No --> D2{2. timeLimitSeconds <= 0?}
    D2 -- Yes --> SetDef[Normalize timeLimitSeconds]
    D2 -- No --> WriteCpp[Write temp_sub_UUID.cpp & Run g++]
    SetDef --> WriteCpp
    WriteCpp --> D3{3. compileProcess.exitValue != 0?}
    D3 -- Yes --> Clean1[Finally: Delete temp files] --> RetCE2([Return 'CE'])
    D3 -- No --> RunBin[Start compiled executable process]
    RunBin --> D4{4. testInput != null && !isEmpty?}
    D4 -- Yes --> WriteIn[Write testInput to stdin & close]
    D4 -- No --> WaitExec[Wait with waitFor timeLimitSeconds]
    WriteIn --> WaitExec
    WaitExec --> D5{5. !finished within timeout?}
    D5 -- Yes --> KillProc[runProcess.destroyForcibly] --> Clean2[Finally: Delete temp files] --> RetTLE([Return 'TLE'])
    D5 -- No --> D6{6. runProcess.exitValue != 0?}
    D6 -- Yes --> Clean3[Finally: Delete temp files] --> RetRE1([Return 'RE'])
    D6 -- No --> D7{7. while readLine != null}
    D7 -- Loop --> AppendOut[Append stdout line] --> D7
    D7 -- Done --> D8{8. Null check on output strings}
    D8 --> Norm[Trim whitespace & normalize CRLF/LF]
    Norm --> D9{9. actualOutput.equals expectedOutput?}
    D9 -- Yes --> Clean4[Finally: Delete temp files] --> RetAC([Return 'AC'])
    D9 -- No --> Clean5[Finally: Delete temp files] --> RetWA([Return 'WA'])
    WriteCpp -.-> D10{10. Exception Caught?}
    D10 -- Yes --> D11{11. Finally: temp files exist?}
    D11 -- Yes --> DelTemp[Delete temp .cpp & executable] --> RetErr([Return Error Verdict])
```

## 3. Software Testing Strategy (V-Model Mapping)
* **Unit Testing (White-Box):** Implemented in `PlagiarismServiceTest.java` using JUnit 5 to test `PlagiarismService` (`tokenize`, `winnow`, `calculateSimilarity`) across comments, preprocessor directives, variable renaming, starter-code exclusion, and `JudgeService.evaluateCppCode` timeout (`TLE`) handling.
* **Integration Testing:** When a student submits code via `POST /api/submissions`, `PortalController` invokes `JudgeService.evaluateCppCode` to evaluate the code and stores the submission with its verdict. When an instructor triggers `GET /api/assignments/{id}/plagiarism`, `PortalController` filters for the latest submission per student and invokes `PlagiarismService.calculateSimilarity` on the fly (computing token streams, Winnowing fingerprints, and 65% Dice + 35% LCS scores without storing fingerprints in a database).
* **System & Acceptance Testing (Black-Box):** Partitioned test inputs into valid solutions (`AC`), compilation syntax errors (`CE`), wrong answers (`WA`), infinite `while(1)` loops (`TLE`), exact copies (Type-1), and variable-renamed / reformatted copies (Type-2).

### C++ Plagiarism Engine Experimental Results (`PlagiarismServiceTest`)
All 7 JUnit 5 tests pass (`Tests run: 7, Failures: 0, Errors: 0`). Below are the exact similarity scores generated by `PlagiarismService` (`k = 5`, `w = 4`, 65% Sorensen-Dice + 35% LCS blend, `75.0%` threshold) across our 6 C++ similarity experiments:

| Experiment Type | Similarity Score | Engine Verdict (`>= 75%` Flagged) | UI Status Badge |
| :--- | :--- | :--- | :--- |
| 1. Exact Copy | 100.0% | FLAGGED | FLAGGED FOR REVIEW |
| 2. Copy with Renamed Variables | 100.0% | FLAGGED | FLAGGED FOR REVIEW |
| 3. Copy with Changed Formatting & Comments | 100.0% | FLAGGED | FLAGGED FOR REVIEW |
| 4. Binary Search vs Linear Search | 34.7% | SAFE | SAFE |
| 5. Two Independently Written Addition Programs | 21.5% | SAFE | SAFE |
| 6. Same Starter Code, Different Implementations | 21.7% | SAFE | SAFE |

## 4. Risk Mitigation, Monitoring, and Management (RMMM) Table
| Risk ID | Risk Description | Category | Probability | Impact | Mitigation Strategy (RMMM) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **R1** | Student code runs infinite `while(1)` loops or leaves temporary binaries behind. | Technical / Security | High | High | **Implemented:** Process timeout (`waitFor` + `destroyForcibly`) and temp file cleanup in `finally`. **Planned/Future:** Docker container isolation. |
| **R2** | False-positive flags on short programs or shared starter boilerplate code. | Algorithmic | Medium | High | **Implemented:** Starter-code Winnowing hash subtraction (`k=5, w=4`), blended scoring (65% Sorensen-Dice + 35% LCS), 75% threshold, and side-by-side manual code review. |
| **R3** | Redundant comparisons when a student submits multiple attempts. | Performance | Medium | Medium | **Implemented:** Deduplicate submissions to compare only the latest attempt per `(assignmentId, studentRollNo)`. **Planned/Future:** Inverted fingerprint index for 100+ files. |
| **R4** | Schedule slippage before the Nov 17 evaluation. | Project Schedule | Medium | High | **Implemented:** Followed the Incremental SDLC model with locked API contracts across branches. |