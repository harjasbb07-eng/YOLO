package com.yolo.portal;

import com.yolo.portal.service.PlagiarismService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PlagiarismServiceTest {

    private PlagiarismService plagiarismService;

    @BeforeEach
    void setUp() {
        plagiarismService = new PlagiarismService();
    }

    private void printExperimentResult(int expNo, String title, double score, boolean flagged) {
        System.out.println("------------------------------------------------------------");
        System.out.printf("Experiment %d: %s%n", expNo, title);
        System.out.println("Student A vs Student B");
        System.out.printf("Similarity: %.1f%%%n", score);
        System.out.printf("Verdict: %s%n", flagged ? "FLAGGED" : "SAFE");
    }

    @Test
    @DisplayName("Experiment 1: Exact Copy -> FLAGGED")
    void experiment1_ExactCopy() {
        String codeA = """
            #include <iostream>
            #include <vector>
            using namespace std;
            int main() {
                int n, target;
                cin >> n >> target;
                vector<int> arr(n);
                for (int i = 0; i < n; i++) cin >> arr[i];
                int left = 0, right = n - 1, ans = -1;
                while (left <= right) {
                    int mid = left + (right - left) / 2;
                    if (arr[mid] == target) { ans = mid; break; }
                    else if (arr[mid] < target) left = mid + 1;
                    else right = mid - 1;
                }
                cout << ans << endl;
                return 0;
            }
        """;
        double score = plagiarismService.calculateSimilarity(codeA, codeA, "");
        boolean flagged = plagiarismService.isFlagged(score);
        printExperimentResult(1, "Exact Copy", score, flagged);

        assertTrue(flagged, "Exact copy must be FLAGGED");
        assertEquals(100.0, score);
    }

    @Test
    @DisplayName("Experiment 2: Copy with Renamed Variables -> FLAGGED")
    void experiment2_RenamedVariables() {
        String codeA = """
            #include <iostream>
            #include <vector>
            using namespace std;
            int main() {
                int n, target;
                cin >> n >> target;
                vector<int> arr(n);
                for (int i = 0; i < n; i++) cin >> arr[i];
                int left = 0, right = n - 1, ans = -1;
                while (left <= right) {
                    int mid = left + (right - left) / 2;
                    if (arr[mid] == target) { ans = mid; break; }
                    else if (arr[mid] < target) left = mid + 1;
                    else right = mid - 1;
                }
                cout << ans << endl;
                return 0;
            }
        """;
        String codeB = """
            #include <bits/stdc++.h>
            using namespace std;
            int main() {
                int size, key;
                cin >> size >> key;
                vector<int> nums(size);
                for (int idx = 0; idx < size; idx++) cin >> nums[idx];
                int lo = 0, hi = size - 1, result = -1;
                while (lo <= hi) {
                    int pivot = lo + (hi - lo) / 2;
                    if (nums[pivot] == key) { result = pivot; break; }
                    else if (nums[pivot] < key) lo = pivot + 1;
                    else hi = pivot - 1;
                }
                cout << result << endl;
                return 0;
            }
        """;
        double score = plagiarismService.calculateSimilarity(codeA, codeB, "");
        boolean flagged = plagiarismService.isFlagged(score);
        printExperimentResult(2, "Copy with Renamed Variables", score, flagged);

        assertTrue(flagged, "Renamed variables copy must be FLAGGED");
    }

    @Test
    @DisplayName("Experiment 3: Copy with Changed Formatting & Comments -> FLAGGED")
    void experiment3_ChangedFormatting() {
        String codeA = """
            #include <iostream>
            using namespace std;
            int main() {
                int n; cin >> n;
                long long fact = 1;
                for (int i = 2; i <= n; i++) {
                    fact = fact * i;
                }
                if (fact > 1000) {
                    cout << fact << endl;
                } else {
                    cout << -1 << endl;
                }
                return 0;
            }
        """;
        String codeB = """
            // Author: Student B - Completely reformatted
            #include <iostream>
            using namespace std;
            int main()
            {
                int n;
                /* Read input from user */
                cin >> n;
                long long fact = 1;
                for(int i=2;i<=n;i++){fact=fact*i;}
                if(fact>1000){cout<<fact<<endl;}else{cout<<-1<<endl;}
                return 0;
            }
        """;
        double score = plagiarismService.calculateSimilarity(codeA, codeB, "");
        boolean flagged = plagiarismService.isFlagged(score);
        printExperimentResult(3, "Copy with Changed Formatting & Comments", score, flagged);

        assertTrue(flagged, "Formatting changes must still be FLAGGED");
    }

    @Test
    @DisplayName("Experiment 4: Binary Search vs Linear Search -> SAFE")
    void experiment4_BinaryVsLinearSearch() {
        String binarySearch = """
            #include <iostream>
            #include <vector>
            using namespace std;
            int main() {
                int n, x; cin >> n >> x;
                vector<int> a(n);
                for (int i = 0; i < n; i++) cin >> a[i];
                int l = 0, r = n - 1;
                while (l <= r) {
                    int m = (l + r) / 2;
                    if (a[m] == x) { cout << m; return 0; }
                    if (a[m] < x) l = m + 1;
                    else r = m - 1;
                }
                cout << -1;
                return 0;
            }
        """;
        String linearSearch = """
            #include <iostream>
            using namespace std;
            int search(int arr[], int n, int key) {
                for (int k = 0; k < n; k++) {
                    if (arr[k] == key) return k;
                }
                return -1;
            }
            int main() {
                int n, key; cin >> n >> key;
                int data[1000];
                int i = 0;
                while (i < n) { cin >> data[i]; i++; }
                int pos = search(data, n, key);
                cout << pos << endl;
                return 0;
            }
        """;
        double score = plagiarismService.calculateSimilarity(binarySearch, linearSearch, "");
        boolean flagged = plagiarismService.isFlagged(score);
        printExperimentResult(4, "Binary Search vs Linear Search", score, flagged);

        assertFalse(flagged, "Different algorithms solving the same problem must be SAFE");
    }

    @Test
    @DisplayName("Experiment 5: Two Independently Written Tiny Addition Programs -> SAFE")
    void experiment5_TinyIndependentAddition() {
        String studentA = """
            #include <iostream>
            using namespace std;
            int main() {
                int a, b;
                cin >> a >> b;
                cout << a + b << endl;
                return 0;
            }
        """;
        String studentB = """
            #include <iostream>
            using namespace std;
            int addNumbers(int x, int y) {
                int sum = x + y;
                return sum;
            }
            int main() {
                int first = 0, second = 0;
                if (cin >> first >> second) {
                    int ans = addNumbers(first, second);
                    cout << ans;
                }
                return 0;
            }
        """;
        double score = plagiarismService.calculateSimilarity(studentA, studentB, "");
        boolean flagged = plagiarismService.isFlagged(score);
        printExperimentResult(5, "Two Independently Written Addition Programs", score, flagged);

        assertFalse(flagged, "Short independent addition programs must be SAFE");
    }

    @Test
    @DisplayName("Experiment 6: Heavy Starter Code, Different Implementations -> SAFE")
    void experiment6_SameStarterCodeDifferentImplementation() {
        String starterCode = """
            #include <iostream>
            #include <vector>
            #include <algorithm>
            using namespace std;
            class Solution {
            public:
                int solve(vector<int>& nums, int k) {
                    // Write your code here
                    return 0;
                }
            };
            int main() {
                int n, k; cin >> n >> k;
                vector<int> nums(n);
                for (int i = 0; i < n; i++) cin >> nums[i];
                Solution sol;
                cout << sol.solve(nums, k) << endl;
                return 0;
            }
        """;

        String studentA = """
            #include <iostream>
            #include <vector>
            #include <algorithm>
            using namespace std;
            class Solution {
            public:
                int solve(vector<int>& nums, int k) {
                    sort(nums.begin(), nums.end());
                    int count = 0, sum = 0;
                    for (int i = 0; i < nums.size(); i++) {
                        if (sum + nums[i] <= k) {
                            sum += nums[i];
                            count++;
                        } else {
                            break;
                        }
                    }
                    return count;
                }
            };
            int main() {
                int n, k; cin >> n >> k;
                vector<int> nums(n);
                for (int i = 0; i < n; i++) cin >> nums[i];
                Solution sol;
                cout << sol.solve(nums, k) << endl;
                return 0;
            }
        """;

        String studentB = """
            #include <iostream>
            #include <vector>
            #include <algorithm>
            using namespace std;
            class Solution {
            public:
                int solve(vector<int>& nums, int k) {
                    vector<int> dp(k + 1, 0);
                    for (int x : nums) {
                        for (int w = k; w >= x; w--) {
                            dp[w] = max(dp[w], dp[w - x] + 1);
                        }
                    }
                    return dp[k];
                }
            };
            int main() {
                int n, k; cin >> n >> k;
                vector<int> nums(n);
                for (int i = 0; i < n; i++) cin >> nums[i];
                Solution sol;
                cout << sol.solve(nums, k) << endl;
                return 0;
            }
        """;

        double score = plagiarismService.calculateSimilarity(studentA, studentB, starterCode);
        boolean flagged = plagiarismService.isFlagged(score);
        printExperimentResult(6, "Same Starter Code, Different Implementations", score, flagged);

        assertFalse(flagged, "Different logic inside identical starter code must be SAFE");
    }

    @Test
    @DisplayName("Unit Test: Tokenizer maps identifiers to ID and numbers to NUM")
    void testTokenizerNormalization() {
        String raw = "int myVariable = 42;";
        List<String> tokens = plagiarismService.tokenize(raw);
        assertEquals(List.of("INT", "ID", "=", "NUM", ";"), tokens);
    }
}



