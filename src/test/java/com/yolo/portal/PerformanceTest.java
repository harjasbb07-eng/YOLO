package com.yolo.portal;

import com.yolo.portal.service.JudgeService;
import com.yolo.portal.service.PlagiarismService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple performance / scalability tests for Project YOLO (verifies NFR-2).
 * Run with: mvn test -Dtest=PerformanceTest
 */
public class PerformanceTest {

    private final PlagiarismService plagiarism = new PlagiarismService();

    /** Builds a synthetic C++ program; different i gives a different structure. */
    private String generateProgram(int i) {
        StringBuilder sb = new StringBuilder();
        sb.append("#include <iostream>\n#include <vector>\nusing namespace std;\n");
        sb.append("int main() {\n  int n; cin >> n;\n  vector<int> a(n);\n");
        sb.append("  for (int i = 0; i < n; i++) cin >> a[i];\n");
        for (int k = 0; k < 8 + (i % 7); k++) {
            sb.append("  int v").append(k).append(" = ").append(i + k).append(";\n");
            if ((i + k) % 3 == 0) sb.append("  for (int j = 0; j < n; j++) { v").append(k).append(" += a[j] * ").append(k).append("; }\n");
            if ((i + k) % 4 == 0) sb.append("  if (v").append(k).append(" > ").append(i).append(") { cout << v").append(k).append(" << endl; }\n");
            if ((i + k) % 5 == 0) sb.append("  while (v").append(k).append(" > 0) { v").append(k).append(" -= ").append(k + 1).append("; }\n");
        }
        sb.append("  return 0;\n}\n");
        return sb.toString();
    }

    private long timePairwise(int students) {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < students; i++) codes.add(generateProgram(i));

        long start = System.nanoTime();
        int pairs = 0;
        for (int i = 0; i < students; i++) {
            for (int j = i + 1; j < students; j++) {
                plagiarism.calculateSimilarity(codes.get(i), codes.get(j), "");
                pairs++;
            }
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("PERF: %d students -> %d pairs -> %d ms%n", students, pairs, ms);
        return ms;
    }

    @Test
    @DisplayName("Perf 1: 100 students (4950 pairs) finish within 5 seconds")
    void hundredStudentsUnderFiveSeconds() {
        timePairwise(10); // JVM warm-up, result ignored
        long ms = timePairwise(100);
        assertTrue(ms < 5000, "100 students took " + ms + " ms (limit 5000 ms)");
    }

    @Test
    @DisplayName("Perf 2: Scaling check 25 / 50 / 100 / 200 students")
    void scalingReport() {
        timePairwise(10); // warm-up
        for (int n : new int[]{25, 50, 100, 200}) {
            timePairwise(n); // read the PERF lines in the console for the report table
        }
    }

    @Test
    @DisplayName("Perf 3: Judge handles 5 sequential submissions (compile + run)")
    void judgeSequentialThroughput() {
        JudgeService judge = new JudgeService();
        String code = "#include <iostream>\nusing namespace std;\nint main(){int a,b;cin>>a>>b;cout<<a+b;return 0;}";
        long start = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            assertEquals("AC", judge.evaluateCppCode(code, "10 20\n", "30", 2));
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("PERF: 5 judge runs -> %d ms (avg %d ms each)%n", ms, ms / 5);
    }

    @Test
    @DisplayName("Perf 4: Infinite loop is killed close to the time limit (1s)")
    void tleIsEnforcedPromptly() {
        JudgeService judge = new JudgeService();
        String code = "int main(){ while(true){} return 0; }";
        long start = System.nanoTime();
        assertEquals("TLE", judge.evaluateCppCode(code, "", "", 1));
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("PERF: TLE case total (incl. compile) -> %d ms%n", ms);
        assertTrue(ms < 6000, "TLE handling too slow: " + ms + " ms");
    }
}