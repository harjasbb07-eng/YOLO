package com.yolo.portal;

import com.yolo.portal.service.JudgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JudgeServiceTest {

    private JudgeService judgeService;

    @BeforeEach
    void setUp() {
        judgeService = new JudgeService();
    }

    @Test
    @DisplayName("Judge Test 1: Correct C++ solution returns AC")
    void testAccepted() {
        String code = """
            #include <iostream>
            using namespace std;
            int main() {
                int a, b;
                cin >> a >> b;
                cout << a + b << endl;
                return 0;
            }
        """;
        assertEquals("AC", judgeService.evaluateCppCode(code, "10 20\n", "30", 2));
    }

    @Test
    @DisplayName("Judge Test 2: Wrong output returns WA")
    void testWrongAnswer() {
        String code = """
            #include <iostream>
            using namespace std;
            int main() {
                int a, b;
                cin >> a >> b;
                cout << a - b << endl;
                return 0;
            }
        """;
        assertEquals("WA", judgeService.evaluateCppCode(code, "10 20\n", "30", 2));
    }

    @Test
    @DisplayName("Judge Test 3: Infinite loop triggers timeout and returns TLE")
    void testTimeLimitExceeded() {
        String code = """
            int main() {
                while(true) {}
                return 0;
            }
        """;
        assertEquals("TLE", judgeService.evaluateCppCode(code, "", "", 1));
    }

    @Test
    @DisplayName("Judge Test 4: Syntax error returns CE")
    void testCompilationError() {
        String code = "int main() { cout << undeclaredVar; return 0; }";
        assertEquals("CE", judgeService.evaluateCppCode(code, "", "", 2));
    }

    @Test
    @DisplayName("Judge Test 5: Non-zero exit code returns RE")
    void testRuntimeError() {
        String code = "int main() { return 1; }";
        assertEquals("RE", judgeService.evaluateCppCode(code, "", "", 2));
    }
}