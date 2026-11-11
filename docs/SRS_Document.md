# Software Requirements Specification (SRS)
**Project YOLO: Plagiarism Detection & Automated Code Assessment Portal**

## 1. Introduction & SDLC Justification
This document specifies the software requirements for Project YOLO following the IEEE 830 standard.

### Selected SDLC Model: Incremental Model
We adopted the Incremental SDLC Model combined with a 3-Tier Layered Architecture:
* **Increment 1 (Core Plagiarism Engine & Sandbox Judge):** Built and unit-tested `PlagiarismService.java` (Tokenization, Rabin-Karp K-Gram Hashing, Winnowing, and Sorensen-Dice + LCS scoring) and `JudgeService.java` (C++ compilation and timeout-enforced execution).
* **Increment 2 (Backend REST APIs & Data Models):** Built `Assignment.java`, `Submission.java`, `ComparisonResult.java`, and REST endpoints in `PortalController.java`.
* **Increment 3 (Web Portal UI & SE Documentation):** Built `index.html`, `instructor.html`, `student.html`, and the Software Engineering documentation suite.

**Why Incremental over Waterfall?** Waiting until the final phase to integrate OS process execution with the similarity engine carries high integration risk. Incremental development allowed us to build, unit-test, and freeze the core services first.
**Why Incremental over Pure Agile?** Our core algorithms (Winnowing and standard I/O evaluation) were fixed upfront, giving us structured IEEE documentation alongside early working vertical slices.

## 2. Functional Requirements (FR)

### Module 1: Assignment & Submission Portal (`PortalController` & UI)
* **FR-1.1:** The system shall allow Instructors to create C++ programming assignments specifying a title, problem description, and execution time limit (in seconds).
* **FR-1.2:** The system shall allow Instructors to enter test case input (`.in`) and expected output (`.out`) via textareas, along with an optional **Starter / Boilerplate Code** textarea to be excluded from similarity calculations.
* **FR-1.3:** The system shall allow Students to select an assignment from a dropdown, enter their Roll Number and Name, paste C++ source code into the textarea editor, and view their instant execution verdict.

### Module 2: Automated Code Assessment Engine (`JudgeService.evaluateCppCode`)
* **FR-2.1:** The `evaluateCppCode` method shall write submitted C++ source code to a temporary file (`temp_sub_<UUID>.cpp`) and compile it using `g++` via Java's `ProcessBuilder`. If compilation fails (non-zero exit code), the system shall return `"CE"` (Compilation Error).
* **FR-2.2:** The system shall execute the compiled binary against the assignment's `testInput` while enforcing a strict OS process timeout using `process.waitFor(timeLimitSeconds, TimeUnit.SECONDS)`. If execution exceeds the time limit, the process is terminated via `destroyForcibly()` and `"TLE"` (Time Limit Exceeded) is returned. Non-zero runtime exits return `"RE"` (Runtime Error).
* **FR-2.3:** The system shall normalize standard output streams (trimming trailing whitespace and normalizing CRLF/LF line endings) and compare against `expectedOutput` to assign `"AC"` (Accepted) or `"WA"` (Wrong Answer). All temporary `.cpp` and executable files shall be deleted in a `finally` block.

### Module 3: Token-Based Similarity Detection Engine (`PlagiarismService`)
* **FR-3.1 (Comment Stripping & Lexical Tokenization):** The `tokenize` method shall strip single-line (`//`) and multi-line (`/* */`) comments, `#include` directives, and `using namespace std;`, converting raw C++ code into a normalized token stream where C++ keywords (`if`, `else`, `for`, `while`, `return`, `int`, `long`, `vector`, `void`, etc.) remain intact, user identifiers map to `ID`, and numeric literals map to `NUM`.
* **FR-3.2 (Starter-Code Fingerprint Exclusion):** When an instructor provides starter/boilerplate code, the engine shall generate its Winnowing fingerprints first and remove all matching starter hashes from each student's fingerprint set before scoring.
* **FR-3.3 (Rabin-Karp K-Gram Hashing & Winnowing):** The `winnow` method shall group normalized tokens into contiguous k-grams of size **`k = 5`**, compute polynomial rolling hashes, and select minimum hashes across a sliding window of size **`w = 4`** using Aiken's Winnowing Algorithm.
* **FR-3.4 (Blended Similarity Scoring):** For every unique pair of students (evaluating only the latest submission per `(assignmentId, studentRollNo)`), `calculateSimilarity` shall compute both the Sorensen-Dice Coefficient on the Winnowing fingerprint sets and the Longest Common Subsequence (LCS) ratio on the token streams, returning a weighted blend of **65% Sorensen-Dice + 35% LCS**.
* **FR-3.5 (Review Flagging & Side-by-Side Inspection):** Pairs scoring **`>= 75.0%`** shall be marked `flagged = true` and displayed with a `"FLAGGED FOR REVIEW"` badge, while pairs below `75.0%` display `"SAFE"`. Definitive or accusatory terms ("Plagiarized" or "Cheater") are strictly avoided. Clicking **"Compare Code"** opens a side-by-side modal displaying both students' submitted code for manual instructor review.

## 3. Non-Functional Requirements (NFR)
* **NFR-1 (Execution Safety & Cleanup):**
  * **Implemented:** Process timeout enforcement (`waitFor` + `destroyForcibly`) so infinite loops (`while(1)`) never hang the server, plus automatic temporary file cleanup in `finally`.
  * **Planned / Future:** Docker container isolation (`--network none`, read-only filesystem, memory caps, and syscall restrictions).
* **NFR-2 (Performance & Scalability):**
  * **Implemented:** Deduplicating to keep only the latest submission per student and computing compact Winnowing fingerprints (`k = 5, w = 4`) on the fly during plagiarism checks.
  * **Planned / Future:** Inverted hash-table indexing to benchmark 100+ submission batches under 5 seconds.
* **NFR-3 (Accuracy & Low False-Positive Rate):** Starter-code fingerprint subtraction combined with the 65% Sorensen-Dice + 35% LCS blend prevents false positives on shared boilerplate templates and short C++ programs.
* **NFR-4 (Maintainability & Modularity):** Built using modular service classes (`PlagiarismService` and `JudgeService`) cleanly separated from the REST controller (`PortalController`) and frontend UI.

## 4. Requirements Traceability Matrix (RTM)
| Requirement ID | Requirement Summary | Implemented Class / Method | Verification Artifact |
| :--- | :--- | :--- | :--- |
| **FR-1.1, FR-1.2** | Assignment creation with starter code & I/O test cases | `PortalController.createAssignment`, `instructor.html` | System Test (Assignment Creation) |
| **FR-1.3** | Student code paste submission & verdict display | `PortalController.submitCode`, `student.html` | System Test (Student Submission) |
| **FR-2.1, FR-2.2, FR-2.3** | `g++` compilation, timeout enforcement, I/O match, temp cleanup | `JudgeService.evaluateCppCode` | JUnit 5 TLE Test & White-Box CFG Analysis |
| **FR-3.1, FR-3.2, FR-3.3** | Tokenization, starter-code subtraction, Winnowing (`k=5, w=4`) | `PlagiarismService.tokenize`, `PlagiarismService.winnow` | `PlagiarismServiceTest.java` (Experiments 1-6) |
| **FR-3.4, FR-3.5** | 65% Dice + 35% LCS blend, `>= 75%` flagging, Compare Code modal | `PlagiarismService.calculateSimilarity`, `instructor.html` | `PlagiarismServiceTest.java` & UI Inspection |

## 5. Implementation Scope & Future Work
| Feature Area | Implemented in Current Version | Planned Future Scope |
| :--- | :--- | :--- |
| **Supported Languages** | C++ (`g++` via `ProcessBuilder`) | C, Java, and Python via a pluggable tokenizer interface |
| **Code & Test Input** | Textarea paste in web portal | File-upload dropzones (`.cpp`, `.in`, `.out`) and assignment deadlines |
| **Execution Sandbox** | OS process timeout (`waitFor` + `destroyForcibly`) & temp file cleanup | Docker container isolation, memory limit (MLE) caps, syscall filtering |
| **Similarity Engine** | On-the-fly Winnowing (`k=5, w=4`) + always-on 65% Dice + 35% LCS blend (`75%` threshold) | Persistent inverted fingerprint index for large multi-section cohorts |