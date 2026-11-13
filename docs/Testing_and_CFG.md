# Project Planning, Testing, Control Flow Graph & Risk Analysis

## 1. Project Planning & Effort Estimation (COCOMO)
We applied the Basic COCOMO model (Organic Mode) to estimate the theoretical baseline effort for Project YOLO:
* **Estimated Project Size:** 3.5 KLOC (Total Project Deliverable Scope)
  * *Justification:* Estimated across the complete project deliverable footprint (~1.5 KLOC for Spring Boot REST controllers, models, and core services; ~1.0 KLOC for JUnit 5 test suites and build/config files; and ~1.0 KLOC for the HTML/JS frontend UI and Mermaid/IEEE engineering documentation).
* **Organic Mode Constants:** `a = 2.4`, `b = 1.05`
* **Calculation:** `Effort (E) = a * (KLOC)^b = 2.4 * (3.5)^1.05 ≈ 8.95 Person-Months`

## 2. White-Box Testing: Control Flow Graph (CFG) & Cyclomatic Complexity
We performed white-box basis path analysis on `JudgeService.evaluateCppCode(String sourceCode, String testInput, String expectedOutput, int timeLimitSeconds)` (reflecting the Increment 2 file-redirected I/O implementation).

### Decision Points (Predicate Nodes) in `JudgeService.evaluateCppCode`
1. **Decision 1 (Time Limit Normalization):** `if (timeLimitSeconds <= 0)` -> defaults to `2` seconds.
2. **Decision 2 (Compilation Exit Code):** `if (compileExitCode != 0)` -> returns `"CE"`.
3. **Decision 3 (Test Input Presence Check):** `if (testInput != null)` -> writes `testInput` to temporary input file before process redirection.
4. **Decision 4 (Execution Timeout Check):** `if (!finished)` from `runProcess.waitFor(timeLimitSeconds, TimeUnit.SECONDS)` -> calls `destroyForcibly()` and returns `"TLE"`.
5. **Decision 5 (Runtime Exit Code Check):** `if (runProcess.exitValue() != 0)` -> returns `"RE"`.
6. **Decision 6 (Output Verdict Comparison):** `if (actualOutput.equals(normExpected))` -> returns `"AC"`, else returns `"WA"`.
7. **Decision 7 (Exception Handler):** `catch (Exception e)` block returning error verdict on process/IO failure.
8. **Decision 8 (Finally Temp File Cleanup Guard):** `if (f != null && f.exists())` inside cleanup helper/loop deleting temporary `.cpp`, binary, `.in`, and `.out` files.

### Cyclomatic Complexity Calculation
* **Number of Predicate / Decision Points (D):** `8`
* **Formula:** `V(G) = D + 1 = 8 + 1 = 9` (equivalently `V(G) = E - N + 2P = 9`)
* This yields **9 independent basis paths** covering default time-limit fallback, compilation error (`CE`), timeout (`TLE`), runtime error (`RE`), wrong answer (`WA`), accepted solution (`AC`), exception handling, and safe temporary file cleanup.

### Control Flow Graph (CFG) Diagram for `JudgeService.evaluateCppCode`
```mermaid
flowchart TD
    Start([Start: evaluateCppCode]) --> D1{1. timeLimitSeconds <= 0?}
    D1 -- Yes --> SetDef[Set timeLimitSeconds = 2]
    D1 -- No --> Compile[Write temp .cpp & Run g++ via ProcessBuilder]
    SetDef --> Compile
    Compile --> D2{2. compileExitCode != 0?}
    D2 -- Yes --> Clean1[Finally: Cleanup temp files] --> RetCE([Return 'CE'])
    D2 -- No --> D3{3. testInput != null?}
    D3 -- Yes --> WriteIn[Write testInput to temp .in file]
    D3 -- No --> Redirect[Configure redirectInput & redirectOutput]
    WriteIn --> Redirect
    Redirect --> RunBin[Start binary & waitFor timeLimitSeconds]
    RunBin --> D4{4. !finished within timeout?}
    D4 -- Yes --> KillProc[runProcess.destroyForcibly] --> Clean2[Finally: Cleanup temp files] --> RetTLE([Return 'TLE'])
    D4 -- No --> D5{5. runProcess.exitValue != 0?}
    D5 -- Yes --> Clean3[Finally: Cleanup temp files] --> RetRE([Return 'RE'])
    D5 -- No --> ReadOut[Files.readString from redirected .out & normalize]
    ReadOut --> D6{6. actualOutput.equals normExpected?}
    D6 -- Yes --> Clean4[Finally: Cleanup temp files] --> RetAC([Return 'AC'])
    D6 -- No --> Clean5[Finally: Cleanup temp files] --> RetWA([Return 'WA'])
    Compile -.-> D7{7. Exception Caught?}
    D7 -- Yes --> D8{8. Finally: if f != null && f.exists()?}
    D8 -- Yes --> DelTemp[Delete temp .cpp, binary, .in, .out] --> RetErr([Return Error Verdict])