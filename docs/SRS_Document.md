# Software Requirements Specification (SRS)
**Project YOLO: Plagiarism Detection & Automated Code Assessment Portal**

## 1. Introduction
This document outlines the software requirements for Project YOLO using the IEEE 830 standard. The system is designed to provide automated code execution and structural plagiarism detection using the Incremental SDLC Model.

## 2. Functional Requirements (FR)

### Module 1: User & Assignment Management (Portal)
* **FR-1.1:** The system shall allow Instructors to create assignments specifying supported languages (C, C++, Java), submission deadlines, execution time limits (e.g., 1.0s - 5.0s), and memory limits (e.g., 256 MB).
* **FR-1.2:** The system shall allow Instructors to upload hidden and sample test cases as paired `.in` and `.out` text files, along with an optional Base/Starter Code input (to be excluded from similarity checks).
* **FR-1.3:** The system shall allow Students to upload source code files before the deadline and view compilation status and sample test verdicts.

### Module 2: Automated Code Assessment Engine (Sandbox Runner)
* **FR-2.1:** The system shall compile submitted source code in an isolated environment and capture stderr streams if compilation fails (CE).
* **FR-2.2:** The system shall execute compiled binaries against each test case `.in` file while enforcing strict OS-level process timeouts (TLE) and memory caps (MLE).
* **FR-2.3:** The system shall normalize output streams (stripping trailing whitespaces and normalizing CRLF/LF line endings) to assign verdicts: Accepted (AC), Wrong Answer (WA), Time Limit Exceeded (TLE), Memory Limit Exceeded (MLE), or Runtime Error (RE).

### Module 3: AST / Token-Based Plagiarism Detection Engine
* **FR-3.1 (Lexical Normalization):** The system shall strip all single-line/multi-line comments, preprocessor directives, and whitespace, converting source code into a standardized token stream where language keywords remain intact and all user-defined identifiers, literals, and types are mapped to generic tokens (ID, NUM, STR, TYPE).
* **FR-3.2 (Base-Code Filtering):** If an instructor provides starter/boilerplate code, the system shall generate its fingerprints first and subtract matching hashes from all student submissions to prevent false positives on standard templates.
* **FR-3.3 (K-Gram & Winnowing):** The system shall group normalized tokens into contiguous k-grams (default k=10), compute rolling hashes using Rabin-Karp, and select minimal hashes across a sliding window of size w (default w=5) using Aiken's Winnowing Algorithm.
* **FR-3.4 (Two-Stage Similarity Scoring):**
  * Stage 1 (Fast Filter): Compute the Sorensen-Dice Coefficient across submission fingerprint sets.
  * Stage 2 (Structural Verification): For pairs where SDice >= 0.50, run Longest Common Subsequence (LCS) or Running Karp-Rabin Greedy String Tiling (RKR-GST) on the normalized token streams to compute exact structural overlap.
* **FR-3.5 (Similarity Flagging & Visual Diff):** The system shall label pairs crossing the configurable threshold (default 80%) with a "FLAGGED FOR REVIEW" status, while pairs below the threshold are marked "SAFE". The UI shall strictly avoid definitive terms like "plagiarized" or "cheater". The system shall also provide a "Compare Code" option to display both submissions side-by-side for manual instructor review.

## 3. Non-Functional Requirements (NFR)
* **NFR-1 (Security & Isolation):** Student code must execute with zero network access, read-only filesystem permissions, and restricted system calls (preventing fork bombs like `while(1) fork();` or unauthorized file reads).
* **NFR-2 (Performance & Scalability):** Pairwise fingerprinting for a batch of 100 submissions (averaging 300 lines of code each) shall complete in under 5 seconds by indexing fingerprints in an inverted hash table rather than running naive O(N^2) string comparisons.
* **NFR-3 (Accuracy / Low False-Positive Rate):** Standard algorithmic templates must yield similarity scores below the flagging threshold when base-code suppression and appropriate k-gram sizes (k >= 10) are applied.
* **NFR-4 (Maintainability & Extensibility):** Language lexers must implement a common interface (Language Tokenizer) so support for a new language (e.g., Python) can be added without modifying the core Winnowing or LCS modules (Open-Closed Principle).
* **NFR-5 (Reliability):** A crash or infinite loop in one student's submission must never crash the main server thread or block the assessment queue.