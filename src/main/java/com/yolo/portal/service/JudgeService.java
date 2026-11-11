package com.yolo.portal.service;

import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class JudgeService {

    private static final int COMPILE_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_TIME_LIMIT_SECONDS = 2;

    /**
     * Returns one of: "AC", "WA", "TLE", "CE", "RE".
     * Compilation timeout is reported as "CE" to keep the API contract simple.
     */
    public String evaluateCppCode(String sourceCode, String testInput, String expectedOutput, int timeLimitSeconds) {
        String uniqueId = UUID.randomUUID().toString().replace("-", "");
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path srcFile = tempDir.resolve("sub_" + uniqueId + ".cpp");
        Path binFile = tempDir.resolve("sub_" + uniqueId + ".out");
        Path outFile = tempDir.resolve("sub_" + uniqueId + "_out.txt"); // New temp file for stdout

        try {
            Files.writeString(srcFile, sourceCode != null ? sourceCode : "");

            // 1. Compile
            ProcessBuilder compilePb = new ProcessBuilder("g++", "-O2", srcFile.toString(), "-o", binFile.toString());
            compilePb.redirectErrorStream(true);
            compilePb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process compileProc = compilePb.start();

            boolean compiledInTime = compileProc.waitFor(COMPILE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!compiledInTime) {
                compileProc.destroyForcibly();
                compileProc.waitFor();
                return "CE";
            }
            if (compileProc.exitValue() != 0) {
                return "CE";
            }

            // 2. Execute compiled binary
            ProcessBuilder execPb = new ProcessBuilder(binFile.toString());
            execPb.redirectError(ProcessBuilder.Redirect.DISCARD);
            execPb.redirectOutput(ProcessBuilder.Redirect.to(outFile.toFile())); // Redirect stdout to file to prevent buffer lock
            Process execProc = execPb.start();

            // Feed stdin
            if (testInput != null && !testInput.isEmpty()) {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(execProc.getOutputStream()))) {
                    writer.write(testInput);
                    writer.flush();
                }
            } else {
                execProc.getOutputStream().close();
            }

            // 3. Enforce time limit
            int limit = timeLimitSeconds > 0 ? timeLimitSeconds : DEFAULT_TIME_LIMIT_SECONDS;
            boolean finishedInTime = execProc.waitFor(limit, TimeUnit.SECONDS);
            
            if (!finishedInTime) {
                execProc.destroyForcibly();
                execProc.waitFor(); // Confirm termination
                return "TLE";
            }

            if (execProc.exitValue() != 0) {
                return "RE";
            }

            // 4. Capture and normalize stdout from file
            String actualOutput = Files.exists(outFile) ? Files.readString(outFile).trim() : "";
            String expectedNormalized = expectedOutput != null ? expectedOutput.replace("\r\n", "\n").trim() : "";

            return actualOutput.equals(expectedNormalized) ? "AC" : "WA";

        } catch (Exception e) {
            return "RE";
        } finally {
            try {
                Files.deleteIfExists(srcFile);
                Files.deleteIfExists(binFile);
                Files.deleteIfExists(outFile); // Cleanup output file
            } catch (IOException ignored) {
            }
        }
    }
}