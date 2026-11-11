# System Design Diagrams — Project YOLO

## 1. Use Case Diagram
```mermaid
flowchart LR
    subgraph Actors
        I[Instructor]
        S[Student]
    end

    subgraph YOLO_Portal [Project YOLO Web Portal]
        UC1([Create C++ Assignment & I/O Test Cases])
        UC2([Provide Optional Starter / Boilerplate Code])
        UC3([Run Plagiarism Check])
        UC4([View Similarity Report: FLAGGED FOR REVIEW / SAFE])
        UC5([Compare Student Code Side-by-Side])
        UC6([Select Assignment & Paste C++ Code])
        UC7([View Instant Execution Verdict: AC / WA / TLE / CE / RE])
    end

    I --> UC1
    UC1 -.->|includes| UC2
    I --> UC3
    UC3 -.->|includes| UC4
    UC4 -.->|extends| UC5
    S --> UC6
    UC6 -.->|includes| UC7
```

## 2. Data Flow Diagram — Level 0 (Context Diagram)
```mermaid
flowchart LR
    Instructor[Instructor] -- "Assignment Details, Starter Code, .in/.out" --> System((Project YOLO Portal))
    System -- "Pairwise Similarity Scores, Status Badges & Side-by-Side Diff" --> Instructor
    Student[Student] -- "Roll Number, Name, C++ Source Code" --> System
    System -- "Execution Verdict (AC, WA, TLE, CE, RE)" --> Student
```

## 3. Data Flow Diagram — Level 1
```mermaid
flowchart TD
    Instructor[Instructor] -->|POST /api/assignments| P1(1.0 Assignment Management)
    P1 -->|Store Assignment| D1[(Assignments Store)]
    
    Student[Student] -->|POST /api/submissions| P2(2.0 JudgeService.evaluateCppCode)
    D1 -->|Read testInput, expectedOutput, timeLimit| P2
    P2 -->|Compile via g++ & Run with waitFor Timeout| P2
    P2 -->|Save Submission & Verdict| D2[(Submissions Store)]
    P2 -->|Return Verdict JSON| Student

    Instructor -->|GET /api/assignments/id/plagiarism| P3(3.0 PlagiarismService Engine)
    D1 -->|Read Starter Code| P3
    D2 -->|Filter Latest Submission per Student| P3
    P3 -->|On-the-fly: tokenize -> winnow k=5,w=4 -> 65% Dice + 35% LCS| P3
    P3 -->|Sorted ComparisonResult List| Instructor
```

## 4. Sequence Diagrams

### A. Student Code Submission & Judging Sequence
```mermaid
sequenceDiagram
    actor S as Student
    participant UI as student.html
    participant C as PortalController
    participant J as JudgeService

    S->>UI: Select Assignment, Enter Roll No & Paste C++ Code
    UI->>C: POST /api/submissions
    C->>C: Lookup Assignment (testInput, expectedOutput, timeLimit)
    C->>J: evaluateCppCode(sourceCode, testInput, expectedOutput, timeLimit)
    J->>J: Write temp_sub_UUID.cpp & compile via g++
    alt Compilation Fails (exitValue != 0)
        J-->>C: Return "CE"
    else Compilation Succeeds
        J->>J: Run executable with waitFor(timeLimitSeconds)
        alt Timeout Exceeded
            J->>J: destroyForcibly()
            J-->>C: Return "TLE"
        else Completed Within Time Limit
            J->>J: Normalize stdout & compare with expectedOutput
            J-->>C: Return "AC" / "WA" / "RE"
        end
    end
    J->>J: Delete temp files in finally block
    C->>C: Save Submission & return JSON
    C-->>UI: { submissionId, studentRollNo, verdict, submittedAt }
    UI-->>S: Display Colored Verdict Alert
```

### B. On-the-Fly Plagiarism Check Sequence
```mermaid
sequenceDiagram
    actor I as Instructor
    participant UI as instructor.html
    participant C as PortalController
    participant P as PlagiarismService

    I->>UI: Select Assignment & Click "Run Plagiarism Check"
    UI->>C: GET /api/assignments/{id}/plagiarism
    C->>C: Filter latest submission per unique studentRollNo
    loop For each unique student pair (A, B)
        C->>P: calculateSimilarity(codeA, codeB, starterCode)
        P->>P: tokenize(codeA), tokenize(codeB), tokenize(starterCode)
        P->>P: winnow(k=5, w=4) & subtract starterCode fingerprints
        P->>P: Compute 65% Sorensen-Dice + 35% LCS
        P-->>C: Return similarityPercentage (0.0 - 100.0)
        C->>C: Set flagged = (similarityPercentage >= 75.0)
    end
    C->>C: Sort ComparisonResult list descending
    C-->>UI: Return JSON Array
    UI-->>I: Render Compact Pair Table & Compare Code Modal
```

## 5. Class Diagram
```mermaid
classDiagram
    class Assignment {
        -Long id
        -String title
        -String description
        -String starterCode
        -String testInput
        -String expectedOutput
        -int timeLimit
    }

    class Submission {
        -Long submissionId
        -Long assignmentId
        -String studentRollNo
        -String studentName
        -String sourceCode
        -String verdict
        -String submittedAt
    }

    class ComparisonResult {
        -String studentRollA
        -String studentRollB
        -double similarityPercentage
        -boolean flagged
        -String codeA
        -String codeB
    }

    class PortalController {
        -JudgeService judgeService
        -PlagiarismService plagiarismService
        +createAssignment(Assignment) Assignment
        +getAllAssignments() List~Assignment~
        +submitCode(Submission) Submission
        +checkPlagiarism(Long id) List~ComparisonResult~
    }

    class JudgeService {
        +evaluateCppCode(String sourceCode, String testInput, String expectedOutput, int timeLimitSeconds) String
    }

    class PlagiarismService {
        +tokenize(String code) List~String~
        +winnow(List~String~ tokens, int k, int w) Set~Long~
        +calculateSimilarity(String codeA, String codeB, String starterCode) double
    }

    PortalController --> JudgeService : uses
    PortalController --> PlagiarismService : uses
    PortalController ..> Assignment : manages
    PortalController ..> Submission : manages
    PortalController ..> ComparisonResult : returns
```