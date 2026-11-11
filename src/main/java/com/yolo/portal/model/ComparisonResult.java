package com.yolo.portal.model;

public class ComparisonResult {
    private String studentRollA;
    private String studentRollB;
    private double similarityPercentage;
    private boolean flagged;
    private String codeA;
    private String codeB;

    public ComparisonResult() {}

    public ComparisonResult(String rollA, String rollB, double sim, boolean flagged, String codeA, String codeB) {
        this.studentRollA = rollA;
        this.studentRollB = rollB;
        this.similarityPercentage = sim;
        this.flagged = flagged;
        this.codeA = codeA;
        this.codeB = codeB;
    }

    // Getters and Setters
    public String getStudentRollA() { return studentRollA; }
    public void setStudentRollA(String studentRollA) { this.studentRollA = studentRollA; }
    public String getStudentRollB() { return studentRollB; }
    public void setStudentRollB(String studentRollB) { this.studentRollB = studentRollB; }
    public double getSimilarityPercentage() { return similarityPercentage; }
    public void setSimilarityPercentage(double similarityPercentage) { this.similarityPercentage = similarityPercentage; }
    public boolean isFlagged() { return flagged; }
    public void setFlagged(boolean flagged) { this.flagged = flagged; }
    public String getCodeA() { return codeA; }
    public void setCodeA(String codeA) { this.codeA = codeA; }
    public String getCodeB() { return codeB; }
    public void setCodeB(String codeB) { this.codeB = codeB; }
}