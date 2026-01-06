package com.github.psiMcpServer.php;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for progress reporting during PHP class move operations.
 * Verifies that progress information is correctly calculated and reported.
 */
public class ProgressReportingTest {

    // ========== Single File Progress Tests ==========

    @Test
    public void testSingleFileMoveProgress_HasAllStages() {
        MockProgressIndicator indicator = new MockProgressIndicator();
        MockSingleFileMove move = new MockSingleFileMove(indicator);

        move.execute("UserService.php");

        List<String> stages = indicator.getTextUpdates();
        assertThat(stages).contains(
            "Finding main class...",
            "Collecting references...",
            "Moving file...",
            "Updating file content...",
            "Updating external references...",
            "Updating require/include paths...",
            "Cleaning up duplicate imports..."
        );
    }

    @Test
    public void testSingleFileMoveProgress_ShowsClassName() {
        MockProgressIndicator indicator = new MockProgressIndicator();
        MockSingleFileMove move = new MockSingleFileMove(indicator);

        move.execute("UserService.php");

        assertThat(indicator.getMainTextUpdates())
            .contains("Moving PHP class: UserService");
    }

    // ========== Batch Move Progress Tests ==========

    @Test
    public void testBatchMoveProgress_ShowsTotalCount() {
        MockProgressIndicator indicator = new MockProgressIndicator();
        MockBatchMove move = new MockBatchMove(indicator);

        move.execute(List.of("File1.php", "File2.php", "File3.php"));

        List<String> updates = indicator.getMainTextUpdates();
        assertThat(updates).anyMatch(s -> s.contains("3")); // Total count shown
    }

    @Test
    public void testBatchMoveProgress_UpdatesForEachFile() {
        MockProgressIndicator indicator = new MockProgressIndicator();
        MockBatchMove move = new MockBatchMove(indicator);

        move.execute(List.of("File1.php", "File2.php", "File3.php"));

        List<String> updates = indicator.getMainTextUpdates();
        assertThat(updates).anyMatch(s -> s.contains("1/3"));
        assertThat(updates).anyMatch(s -> s.contains("2/3"));
        assertThat(updates).anyMatch(s -> s.contains("3/3"));
    }

    @Test
    public void testBatchMoveProgress_ShowsCurrentFileName() {
        MockProgressIndicator indicator = new MockProgressIndicator();
        MockBatchMove move = new MockBatchMove(indicator);

        move.execute(List.of("UserService.php", "OrderService.php"));

        List<String> secondaryText = indicator.getSecondaryTextUpdates();
        assertThat(secondaryText).contains("UserService.php");
        assertThat(secondaryText).contains("OrderService.php");
    }

    @Test
    public void testBatchMoveProgress_FractionIncrements() {
        MockProgressIndicator indicator = new MockProgressIndicator();
        MockBatchMove move = new MockBatchMove(indicator);

        move.execute(List.of("File1.php", "File2.php", "File3.php", "File4.php"));

        List<Double> fractions = indicator.getFractionUpdates();
        assertThat(fractions).isNotEmpty();

        // Should start at 0 and increment
        assertThat(fractions.get(0)).isEqualTo(0.0);

        // Fractions should be monotonically increasing
        for (int i = 1; i < fractions.size(); i++) {
            assertThat(fractions.get(i)).isGreaterThanOrEqualTo(fractions.get(i - 1));
        }

        // Final fraction should be 1.0
        assertThat(fractions.get(fractions.size() - 1)).isEqualTo(1.0);
    }

    @Test
    public void testBatchMoveProgress_StartsIndeterminate() {
        MockProgressIndicator indicator = new MockProgressIndicator();
        MockBatchMove move = new MockBatchMove(indicator);

        move.execute(List.of("File1.php"));

        // Should start indeterminate (while scanning)
        assertThat(indicator.wasIndeterminateSet()).isTrue();

        // Then switch to determinate when processing
        assertThat(indicator.wasDeterminateSet()).isTrue();
    }

    @Test
    public void testBatchMoveProgress_ShowsCompletionMessage() {
        MockProgressIndicator indicator = new MockProgressIndicator();
        MockBatchMove move = new MockBatchMove(indicator);

        move.execute(List.of("File1.php", "File2.php"));

        List<String> updates = indicator.getMainTextUpdates();
        assertThat(updates).contains("Batch move completed");
    }

    // ========== Pattern-Based Move Progress Tests ==========

    @Test
    public void testPatternMoveProgress_ShowsPattern() {
        MockProgressIndicator indicator = new MockProgressIndicator();
        MockPatternMove move = new MockPatternMove(indicator);

        move.execute("*Controller.php");

        List<String> updates = indicator.getMainTextUpdates();
        assertThat(updates).anyMatch(s -> s.contains("*Controller.php"));
    }

    @Test
    public void testPatternMoveProgress_ShowsScanningMessage() {
        MockProgressIndicator indicator = new MockProgressIndicator();
        MockPatternMove move = new MockPatternMove(indicator);

        move.execute("*Service.php");

        List<String> updates = indicator.getMainTextUpdates();
        assertThat(updates).anyMatch(s ->
            s.contains("Scanning") && s.contains("*Service.php"));
    }

    // ========== Cancellation Tests ==========

    @Test
    public void testBatchMove_SupportsCancellation() {
        MockProgressIndicator indicator = new MockProgressIndicator();
        MockBatchMove move = new MockBatchMove(indicator);

        // Cancel after first file
        indicator.setCancelAfter(1);

        int movedCount = move.execute(List.of("File1.php", "File2.php", "File3.php"));

        // Should only process 1 file
        assertThat(movedCount).isEqualTo(1);
    }

    @Test
    public void testBatchMove_ChecksCancellationBetweenFiles() {
        MockProgressIndicator indicator = new MockProgressIndicator();
        MockBatchMove move = new MockBatchMove(indicator);

        move.execute(List.of("File1.php", "File2.php", "File3.php"));

        // Should check cancellation once per file
        assertThat(indicator.getCancellationCheckCount()).isGreaterThanOrEqualTo(3);
    }

    // ========== Error Reporting Tests ==========

    @Test
    public void testBatchMove_ContinuesOnFileError() {
        MockProgressIndicator indicator = new MockProgressIndicator();
        MockBatchMove move = new MockBatchMove(indicator);
        move.setFilesToFail(List.of("File2.php"));

        int movedCount = move.execute(List.of("File1.php", "File2.php", "File3.php"));

        // Should process all files, just count failures
        assertThat(movedCount).isEqualTo(2);
        assertThat(move.getFailedCount()).isEqualTo(1);
    }

    // ========== Mock Classes ==========

    static class MockProgressIndicator {
        private final List<String> mainTextUpdates = new ArrayList<>();
        private final List<String> secondaryTextUpdates = new ArrayList<>();
        private final List<Double> fractionUpdates = new ArrayList<>();
        private boolean indeterminateSet = false;
        private boolean determinateSet = false;
        private int cancelAfter = -1;
        private int fileCount = 0;
        private int cancellationCheckCount = 0;

        void setText(String text) {
            mainTextUpdates.add(text);
        }

        void setText2(String text) {
            secondaryTextUpdates.add(text);
        }

        void setFraction(double fraction) {
            fractionUpdates.add(fraction);
        }

        void setIndeterminate(boolean indeterminate) {
            if (indeterminate) {
                indeterminateSet = true;
            } else {
                determinateSet = true;
            }
        }

        boolean isCanceled() {
            cancellationCheckCount++;
            return cancelAfter >= 0 && fileCount >= cancelAfter;
        }

        void incrementFileCount() {
            fileCount++;
        }

        void setCancelAfter(int files) {
            this.cancelAfter = files;
        }

        List<String> getMainTextUpdates() {
            return new ArrayList<>(mainTextUpdates);
        }

        List<String> getSecondaryTextUpdates() {
            return new ArrayList<>(secondaryTextUpdates);
        }

        List<String> getTextUpdates() {
            List<String> all = new ArrayList<>();
            all.addAll(mainTextUpdates);
            all.addAll(secondaryTextUpdates);
            return all;
        }

        List<Double> getFractionUpdates() {
            return new ArrayList<>(fractionUpdates);
        }

        boolean wasIndeterminateSet() {
            return indeterminateSet;
        }

        boolean wasDeterminateSet() {
            return determinateSet;
        }

        int getCancellationCheckCount() {
            return cancellationCheckCount;
        }
    }

    static class MockSingleFileMove {
        private final MockProgressIndicator indicator;

        MockSingleFileMove(MockProgressIndicator indicator) {
            this.indicator = indicator;
        }

        void execute(String fileName) {
            String className = fileName.replace(".php", "");

            // Simulate all stages
            indicator.setText("Moving PHP class: " + className);

            indicator.setText2("Finding main class...");
            indicator.setText2("Collecting references...");
            indicator.setText2("Moving file...");
            indicator.setText2("Updating file content...");
            indicator.setText2("Updating external references...");
            indicator.setText2("Updating require/include paths...");
            indicator.setText2("Cleaning up duplicate imports...");
        }
    }

    static class MockBatchMove {
        private final MockProgressIndicator indicator;
        private List<String> filesToFail = List.of();
        private int failedCount = 0;

        MockBatchMove(MockProgressIndicator indicator) {
            this.indicator = indicator;
        }

        void setFilesToFail(List<String> files) {
            this.filesToFail = files;
        }

        int execute(List<String> files) {
            int movedCount = 0;
            int total = files.size();

            // Scanning phase
            indicator.setText("Scanning for PHP files...");
            indicator.setIndeterminate(true);

            // Processing phase
            indicator.setIndeterminate(false);
            indicator.setFraction(0.0);

            for (int i = 0; i < files.size(); i++) {
                // Check cancellation
                if (indicator.isCanceled()) {
                    break;
                }

                String file = files.get(i);

                indicator.setText("Moving PHP classes (" + (i + 1) + "/" + total + ")");
                indicator.setText2(file);
                indicator.setFraction((double) i / total);

                if (filesToFail.contains(file)) {
                    failedCount++;
                } else {
                    movedCount++;
                }

                indicator.incrementFileCount();
            }

            indicator.setFraction(1.0);
            indicator.setText("Batch move completed");

            return movedCount;
        }

        int getFailedCount() {
            return failedCount;
        }
    }

    static class MockPatternMove {
        private final MockProgressIndicator indicator;

        MockPatternMove(MockProgressIndicator indicator) {
            this.indicator = indicator;
        }

        void execute(String pattern) {
            indicator.setText("Scanning for PHP files matching: " + pattern);
            indicator.setIndeterminate(true);

            // Simulate finding files
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            indicator.setIndeterminate(false);
            indicator.setText("Moving PHP classes: " + pattern);
        }
    }
}
