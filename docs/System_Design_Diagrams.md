# System Design & UML Diagrams (Project YOLO)

## 1. Use Case Diagram
```mermaid
flowchart LR
    Instructor([Instructor])
    Student([Student])

    subgraph YOLO_System ["Project YOLO - Code Assessment & Plagiarism Portal"]
        UC1("Create Assignment & I/O Test Cases")
        UC2("Configure Optional Starter Code")
        UC3("Submit C++ Solution")
        UC4("Compile & Evaluate Code Verdict")
        UC5("Run Pairwise Plagiarism Check")
        UC6("Filter by Flagged / Min Similarity % & Compare Code")
    end

    Instructor --> UC1
    Instructor --> UC2
    Instructor --> UC5
    Instructor --> UC6
    Student --> UC3
    UC3 -.->|includes| UC4
```

## 2. Class Diagram
```mermaid
classDiagram
    class PortalController {
        -List~Assignment~ assignments
        -List~Submission~ submissions
        -JudgeService judgeService
        -PlagiarismService plagiarismService
        +createAssignment(Assignment) Assignment
        +getAllAssignments() List~Assignment~
        +submitCode(Submission) Submission
        +checkPlagiarism(Long assignmentId) List~ComparisonResult~
    }

    class JudgeService {
        +evaluateCppCode(String sourceCode, String testInput, String expectedOutput, int timeLimitSeconds) String
        -cleanupFiles(File... files) void
    }

    class PlagiarismService {
        -int K = 5
        -int W = 4
        -double SIMILARITY_THRESHOLD = 75.0
        +calculateSimilarity(String codeA, String codeB, String starterCode) double
        -tokenize(String code) List~String~
        -winnow(List~String~ tokens) Set~Long~
        -computeLcsRatio(List~String~ tokensA, List~String~ tokensB) double
    }

    class Assignment {
        -Long id
        -String title
        -String description
        -int timeLimit
        -String starterCode
        -String testInput
        -String expectedOutput
    }

    class Submission {
        -Long id
        -Long assignmentId
        -String studentRollNo
        -String studentName
        -String sourceCode
        -String verdict
        -LocalDateTime submittedAt
    }

    class ComparisonResult {
        -String studentRollA
        -String studentRollB
        -double similarityPercentage
        -boolean flagged
        -String codeA
        -String codeB
    }

    PortalController --> JudgeService : uses
    PortalController --> PlagiarismService : uses
    PortalController o-- Assignment : manages
    PortalController o-- Submission : stores
    PortalController ..> ComparisonResult : produces
```

## 3. Sequence Diagrams

### A. Code Submission & File-Redirected Judge Flow
```mermaid
sequenceDiagram
    actor Student
    participant UI as student.html
    participant API as PortalController
    participant Judge as JudgeService
    participant OS as OS ProcessBuilder / g++

    Student->>UI: Enter Roll No, Name & C++ Code -> Submit
    UI->>API: POST /api/submissions
    API->>Judge: evaluateCppCode(sourceCode, testInput, expectedOutput, timeLimit)
    Judge->>OS: Write temp .cpp & compile with g++
    alt Compilation Fails (exitCode != 0)
        Judge-->>API: Return "CE"
    else Compilation Succeeds
        Judge->>OS: Redirect stdin (.in) & stdout (.out), run binary with timeout
        alt Exceeds timeLimitSeconds
            Judge->>OS: destroyForcibly()
            Judge-->>API: Return "TLE"
        else Non-zero Exit Code
            Judge-->>API: Return "RE"
        else Normal Exit
            Judge->>Judge: Read .out file & compare with expectedOutput
            Judge-->>API: Return "AC" or "WA"
        end
    end
    Judge->>Judge: finally -> cleanupFiles(.cpp, binary, .in, .out)
    API->>API: Save Submission with verdict
    API-->>UI: JSON Submission Response
    UI-->>Student: Display Verdict Badge (AC / WA / CE / TLE / RE)
```

### B. Plagiarism Detection & Filtering Flow
```mermaid
sequenceDiagram
    actor Instructor
    participant UI as instructor.html
    participant API as PortalController
    participant Plag as PlagiarismService

    Instructor->>UI: Click "Run Plagiarism Check"
    UI->>API: GET /api/assignments/{id}/plagiarism
    API->>API: Deduplicate (keep latest submission per studentRollNo)
    loop For each unique pair (Student A, Student B)
        API->>Plag: calculateSimilarity(codeA, codeB, starterCode)
        Plag->>Plag: tokenize() -> strip comments, includes, main, return 0
        Plag->>Plag: winnow(tokens) -> k=5 polynomial hashes, w=4 window
        Plag->>Plag: Subtract starterCode fingerprint hashes
        Plag->>Plag: Blend 65% Sorensen-Dice + 35% LCS (+ <20 token dampening)
        Plag-->>API: similarityPercentage
        API->>API: Set flagged = (similarityPercentage >= 75.0)
    end
    API-->>UI: JSON ComparisonResult List
    UI-->>Instructor: Render Table (Flagged Orange / Safe Green) & Apply Filters
```

## 4. Data Flow Diagram (Level 1 DFD)
```mermaid
flowchart TD
    Inst([Instructor]) -->|Assignment Details, Starter Code & I/O| P1((1.0 Manage Assignments))
    P1 -->|Store Assignment| D1[(Assignments Store)]

    Stud([Student]) -->|C++ Source Code| P2((2.0 Evaluate Submission))
    D1 -->|Test Input, Expected Output, Time Limit| P2
    P2 -->|Compile & Run via File-Redirected I/O| OS[OS Process Runner]
    OS -->|Exit Status, Timeout & .out File| P2
    P2 -->|AC / WA / CE / TLE / RE| Stud
    P2 -->|Save Submission + Verdict| D2[(Submissions Store)]

    Inst -->|Trigger Check & Set UI Filters| P3((3.0 Plagiarism Engine))
    D1 -->|Starter Code| P3
    D2 -->|Latest Submissions per Student| P3
    P3 -->|Pairwise Scores, Flag Status & Code Diffs| Inst
```