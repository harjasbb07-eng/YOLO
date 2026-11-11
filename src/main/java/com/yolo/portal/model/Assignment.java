package com.yolo.portal.model;

public class Assignment {
    private Long id;
    private String title;
    private String description;
    private String starterCode;
    private String testInput;
    private String expectedOutput;
    private int timeLimit;

    public Assignment() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStarterCode() { return starterCode; }
    public void setStarterCode(String starterCode) { this.starterCode = starterCode; }
    public String getTestInput() { return testInput; }
    public void setTestInput(String testInput) { this.testInput = testInput; }
    public String getExpectedOutput() { return expectedOutput; }
    public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }
    public int getTimeLimit() { return timeLimit; }
    public void setTimeLimit(int timeLimit) { this.timeLimit = timeLimit; }
}