package com.yolo.portal.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolo.portal.model.Assignment;
import com.yolo.portal.model.ComparisonResult;
import com.yolo.portal.model.Submission;
import com.yolo.portal.service.JudgeService;
import com.yolo.portal.service.PlagiarismService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api")
@CrossOrigin("*")
public class PortalController {

    @Autowired
    private JudgeService judgeService;

    @Autowired
    private PlagiarismService plagiarismService;

    // In-Memory Storage
    private ConcurrentHashMap<Long, Assignment> assignments = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Long, Submission> submissions = new ConcurrentHashMap<>();
    
    private AtomicLong assignmentIdCounter = new AtomicLong(0);
    private AtomicLong submissionIdCounter = new AtomicLong(0);

    // JSON Persistence Setup
    private final String DATA_FILE = "./data/yolo_data.json";
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        File file = new File(DATA_FILE);
        if (file.exists()) {
            try {
                DataWrapper data = mapper.readValue(file, DataWrapper.class);
                if (data.assignments != null) this.assignments.putAll(data.assignments);
                if (data.submissions != null) this.submissions.putAll(data.submissions);
                
                long maxAssignId = assignments.keySet().stream().max(Long::compare).orElse(0L);
                long maxSubId = submissions.keySet().stream().max(Long::compare).orElse(0L);
                assignmentIdCounter.set(maxAssignId);
                submissionIdCounter.set(maxSubId);
            } catch (IOException e) {
                System.err.println("Failed to load existing data: " + e.getMessage());
            }
        } else {
            file.getParentFile().mkdirs();
        }
    }

    private synchronized void persistData() throws IOException {
        mapper.writeValue(new File(DATA_FILE), new DataWrapper(assignments, submissions));
    }

    // --- Endpoints ---

    
    @PostMapping("/assignments")
    public Assignment createAssignment(@RequestBody Assignment assignment) {
        if (assignment.getTimeLimit() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Time limit must be positive");
        }
        long id = assignmentIdCounter.incrementAndGet();
        assignment.setId(id);
        assignments.put(id, assignment);
        
        try {
            persistData();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save assignment data");
        }
        return assignment;
    }

    @GetMapping("/assignments")
    public Collection<Assignment> getAllAssignments() {
        return assignments.values();
    }

    
    @PostMapping("/submissions")
    public Submission submitCode(@RequestBody Submission submission) {
        if (submission.getAssignmentId() == null || 
            submission.getSourceCode() == null || submission.getSourceCode().isBlank() || 
            submission.getStudentRollNo() == null || submission.getStudentRollNo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required submission fields");
        }

        Assignment assignment = assignments.get(submission.getAssignmentId());
        if (assignment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found");
        }

        String verdict = judgeService.evaluateCppCode(
                submission.getSourceCode(),
                assignment.getTestInput(),
                assignment.getExpectedOutput(),
                assignment.getTimeLimit()
        );

        long subId = submissionIdCounter.incrementAndGet();
        submission.setSubmissionId(subId);
        submission.setVerdict(verdict);
        submission.setSubmittedAt(LocalDateTime.now().toString());

        submissions.put(subId, submission);
        
        try {
            persistData();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save submission data");
        }
        
        return submission;
    }

    @GetMapping("/assignments/{id}/plagiarism")
    public List<ComparisonResult> checkPlagiarism(@PathVariable Long id) {
        // Restore the 404 Not Found check
        Assignment assignment = assignments.get(id);
        if (assignment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found");
        }

        // 1. Filter for only the latest submission per student roll number (Single clean loop)
        Map<String, Submission> latestSubmissions = new HashMap<>();
        for (Submission sub : submissions.values()) {
            if (id.equals(sub.getAssignmentId())) { // Null-safe equality check
                Submission existing = latestSubmissions.get(sub.getStudentRollNo());
                if (existing == null || sub.getSubmissionId() > existing.getSubmissionId()) {
                    latestSubmissions.put(sub.getStudentRollNo(), sub);
                }
            }
        }

        List<Submission> validSubs = new ArrayList<>(latestSubmissions.values());
        if (validSubs.size() < 2) {
            return Collections.emptyList();
        }

        // 2. Nested loop for pairwise comparison
        List<ComparisonResult> results = new ArrayList<>();
        String starterCode = assignment.getStarterCode() != null ? assignment.getStarterCode() : "";

        for (int i = 0; i < validSubs.size(); i++) {
            for (int j = i + 1; j < validSubs.size(); j++) {
                Submission subA = validSubs.get(i);
                Submission subB = validSubs.get(j);

                double sim = plagiarismService.calculateSimilarity(subA.getSourceCode(), subB.getSourceCode(), starterCode);
                boolean flagged = plagiarismService.isFlagged(sim);

                results.add(new ComparisonResult(
                        subA.getStudentRollNo(),
                        subB.getStudentRollNo(),
                        sim,
                        flagged,
                        subA.getSourceCode(),
                        subB.getSourceCode()
                ));
            }
        }

        // 3. Sort descending by similarity percentage
        results.sort((a, b) -> Double.compare(b.getSimilarityPercentage(), a.getSimilarityPercentage()));
        return results;
    }

    // Internal class for JSON Serialization
    private static class DataWrapper {
        public Map<Long, Assignment> assignments;
        public Map<Long, Submission> submissions;

        public DataWrapper() {}
        public DataWrapper(Map<Long, Assignment> a, Map<Long, Submission> s) {
            this.assignments = a;
            this.submissions = s;
        }
    }
}
