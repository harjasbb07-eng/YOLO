package com.yolo.portal.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PlagiarismService {

    // Calibrated conservative threshold based on 6 benchmark C++ experiments
    public static final double FLAG_THRESHOLD = 75.0;

    // Minimum meaningful tokens required after boilerplate removal to avoid tiny-program false positives
    private static final int MIN_MEANINGFUL_TOKENS = 20;

    private static final Set<String> CPP_KEYWORDS = Set.of(
            "auto", "break", "case", "char", "const", "continue", "default", "do",
            "double", "else", "enum", "extern", "float", "for", "goto", "if",
            "int", "long", "register", "return", "short", "signed", "sizeof", "static",
            "struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while",
            "class", "public", "private", "protected", "template", "typename", "vector",
            "string", "cin", "cout", "endl", "pair", "map", "set", "bool", "true", "false"
    );

    private static final int K_GRAM = 5;
    private static final int WINDOW_SIZE = 4;

    public boolean isFlagged(double similarityScore) {
        return similarityScore >= FLAG_THRESHOLD;
    }

    public double calculateSimilarity(String codeA, String codeB, String starterCode) {
        if (codeA == null || codeB == null || codeA.isBlank() || codeB.isBlank()) {
            return 0.0;
        }

        // Step 1: Remove starter code tokens if provided
        List<String> tokensA = tokenize(codeA);
        List<String> tokensB = tokenize(codeB);

        Set<Long> starterFp = new HashSet<>();
        if (starterCode != null && !starterCode.isBlank()) {
            List<String> starterTokens = tokenize(starterCode);
            if (starterTokens.size() >= K_GRAM) {
                starterFp = winnow(starterTokens);
            }
            tokensA = subtractStarterTokens(tokensA, starterTokens);
            tokensB = subtractStarterTokens(tokensB, starterTokens);
        }

        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 0.0;
        }

        // Step 2: Compute K-Gram Winnowing Dice Score
        double diceScore = 0.0;
        if (tokensA.size() >= K_GRAM && tokensB.size() >= K_GRAM) {
            Set<Long> fpA = winnow(tokensA);
            Set<Long> fpB = winnow(tokensB);

            fpA.removeAll(starterFp);
            fpB.removeAll(starterFp);

            if (!fpA.isEmpty() && !fpB.isEmpty()) {
                Set<Long> intersection = new HashSet<>(fpA);
                intersection.retainAll(fpB);
                diceScore = (2.0 * intersection.size()) / (fpA.size() + fpB.size()) * 100.0;
            }
        }

        // Step 3: Compute Structural Token-LCS Score
        double lcsScore = computeLcsRatio(tokensA, tokensB);

        // Blended score (65% Winnowing Fingerprints + 35% Structural LCS)
        double rawScore = (0.65 * diceScore) + (0.35 * lcsScore);

        // Step 4: Tiny-program confidence dampening (prevents 5-line A+B programs from falsely flagging)
        int minLen = Math.min(tokensA.size(), tokensB.size());
        if (minLen < MIN_MEANINGFUL_TOKENS) {
            double confidenceFactor = (double) minLen / MIN_MEANINGFUL_TOKENS;
            rawScore *= confidenceFactor;
        }

        return Math.min(100.0, Math.round(rawScore * 10.0) / 10.0);
    }

    public List<String> tokenize(String rawCode) {
        // Strip comments, preprocessor headers, standard namespace boilerplate, and main return boilerplate
        String clean = rawCode
                .replaceAll("//.*", "")
                .replaceAll("/\\*[\\s\\S]*?\\*/", "")
                .replaceAll("#\\s*include\\s*[<\"][^>\"]*[>\"]", "")
                .replaceAll("using\\s+namespace\\s+std\\s*;", "")
                .replaceAll("return\\s+0\\s*;", "");

        List<String> tokens = new ArrayList<>();
        Pattern pattern = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*|\\d+|==|!=|<=|>=|&&|\\|\\||\\+\\+|--|<<|>>|[+\\-*/%=<>{}\\[\\]();,]");
        Matcher matcher = pattern.matcher(clean);

        while (matcher.find()) {
            String val = matcher.group();
            if ( val.equals("main") ) {
                continue; // Ignore main identifier so every C++ program doesn't share main()
            } else if (CPP_KEYWORDS.contains(val)) {
                tokens.add(val.toUpperCase());
            } else if (val.matches("\\d+")) {
                tokens.add("NUM");
            } else if (val.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                tokens.add("ID");
            } else {
                tokens.add(val);
            }
        }
        return tokens;
    }

    private List<String> subtractStarterTokens(List<String> studentTokens, List<String> starterTokens) {
        if (starterTokens.isEmpty()) return studentTokens;
        List<String> filtered = new ArrayList<>(studentTokens);
        int sIdx = 0;
        Iterator<String> it = filtered.iterator();
        while (it.hasNext() && sIdx < starterTokens.size()) {
            if (it.next().equals(starterTokens.get(sIdx))) {
                it.remove();
                sIdx++;
            }
        }
        return filtered;
    }

    private Set<Long> winnow(List<String> tokens) {
        List<Long> hashes = new ArrayList<>();
        long base = 31;
        long mod = 1_000_000_007L;

        for (int i = 0; i <= tokens.size() - K_GRAM; i++) {
            long h = 0;
            for (int j = 0; j < K_GRAM; j++) {
                h = (h * base + Math.abs(tokens.get(i + j).hashCode())) % mod;
            }
            hashes.add(h);
        }

        if (hashes.size() < WINDOW_SIZE) {
            return new HashSet<>(hashes);
        }

        Set<Long> fingerprints = new HashSet<>();
        for (int i = 0; i <= hashes.size() - WINDOW_SIZE; i++) {
            long minHash = Long.MAX_VALUE;
            for (int j = 0; j < WINDOW_SIZE; j++) {
                minHash = Math.min(minHash, hashes.get(i + j));
            }
            fingerprints.add(minHash);
        }
        return fingerprints;
    }

    private double computeLcsRatio(List<String> a, List<String> b) {
        int m = a.size();
        int n = b.size();
        if (m == 0 || n == 0) return 0.0;

        int[] dp = new int[n + 1];
        for (int i = 1; i <= m; i++) {
            int prev = 0;
            for (int j = 1; j <= n; j++) {
                int temp = dp[j];
                if (a.get(i - 1).equals(b.get(j - 1))) {
                    dp[j] = prev + 1;
                } else {
                    dp[j] = Math.max(dp[j], dp[j - 1]);
                }
                prev = temp;
            }
        }
        // Use union/average length denominator so a tiny file inside a huge file doesn't skew to 100%
        double avgLen = (m + n) / 2.0;
        return ((double) dp[n] / avgLen) * 100.0;
    }
}