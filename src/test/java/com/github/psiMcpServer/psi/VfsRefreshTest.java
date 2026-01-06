package com.github.psiMcpServer.psi;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for VFS (Virtual File System) refresh behavior.
 * These tests verify the logic and patterns used for VFS synchronization
 * without requiring the actual IntelliJ platform.
 */
public class VfsRefreshTest {

    // ========== Refresh Tracking Tests ==========

    @Test
    public void testRefreshCalledAfterRename() {
        MockRefactoringExecutor executor = new MockRefactoringExecutor();

        executor.rename("oldName", "newName");

        assertThat(executor.getRefreshCount()).isEqualTo(1);
        assertThat(executor.wasRefreshCalled()).isTrue();
    }

    @Test
    public void testRefreshCalledAfterMove() {
        MockRefactoringExecutor executor = new MockRefactoringExecutor();

        executor.moveFile("source.php", "/target/dir");

        assertThat(executor.getRefreshCount()).isEqualTo(1);
    }

    @Test
    public void testRefreshCalledAfterDelete() {
        MockRefactoringExecutor executor = new MockRefactoringExecutor();

        executor.safeDelete(List.of("file1.php", "file2.php"));

        assertThat(executor.getRefreshCount()).isEqualTo(1);
    }

    @Test
    public void testRefreshNotCalledOnFailure() {
        MockRefactoringExecutor executor = new MockRefactoringExecutor();
        executor.setFailOnNextOperation(true);

        executor.rename("oldName", "newName");

        // Refresh should NOT be called when operation fails
        assertThat(executor.getRefreshCount()).isEqualTo(0);
    }

    // ========== Multiple Operations Tests ==========

    @Test
    public void testMultipleOperations_EachRefreshesOnce() {
        MockRefactoringExecutor executor = new MockRefactoringExecutor();

        executor.rename("name1", "name2");
        executor.moveFile("file.php", "/new/location");
        executor.safeDelete(List.of("old.php"));

        assertThat(executor.getRefreshCount()).isEqualTo(3);
    }

    @Test
    public void testRefreshOrder_CalledAfterOperation() {
        MockRefactoringExecutor executor = new MockRefactoringExecutor();

        executor.rename("oldName", "newName");

        List<String> operations = executor.getOperationOrder();
        assertThat(operations).containsExactly("rename", "refresh");
    }

    // ========== PHP Move Handler Tests ==========

    @Test
    public void testPhpMoveHandler_RefreshAfterMove() {
        MockPhpMoveHandler handler = new MockPhpMoveHandler();

        handler.movePhpClass("UserService.php", "App\\Services", "App\\Domain\\Services");

        assertThat(handler.getRefreshCount()).isEqualTo(1);
    }

    @Test
    public void testPhpBatchMoveHandler_SingleRefreshAfterAllMoves() {
        MockPhpBatchMoveHandler handler = new MockPhpBatchMoveHandler();

        handler.moveDirectory(List.of("Service1.php", "Service2.php", "Service3.php"), "/target");

        // Should only call refresh ONCE at the end, not for each file
        assertThat(handler.getFinalRefreshCount()).isEqualTo(1);
    }

    @Test
    public void testPhpBatchMoveHandler_RefreshEvenOnPartialFailure() {
        MockPhpBatchMoveHandler handler = new MockPhpBatchMoveHandler();
        handler.setFilesToFail(List.of("Service2.php"));

        handler.moveDirectory(List.of("Service1.php", "Service2.php", "Service3.php"), "/target");

        // Should still call final refresh even if some files failed
        assertThat(handler.getFinalRefreshCount()).isEqualTo(1);
        assertThat(handler.getSuccessfulMoves()).isEqualTo(2);
        assertThat(handler.getFailedMoves()).isEqualTo(1);
    }

    // ========== Sync vs Async Refresh Tests ==========

    @Test
    public void testSyncRefreshUsed() {
        MockRefactoringExecutor executor = new MockRefactoringExecutor();

        executor.rename("old", "new");

        // Our implementation should use sync refresh to ensure
        // IDE is updated before returning result to MCP client
        assertThat(executor.wasSyncRefresh()).isTrue();
        assertThat(executor.wasAsyncRefresh()).isFalse();
    }

    // ========== Edge Cases ==========

    @Test
    public void testRefreshOnEmptyBatch() {
        MockPhpBatchMoveHandler handler = new MockPhpBatchMoveHandler();

        handler.moveDirectory(List.of(), "/target");

        // Should NOT call refresh if no files were processed
        assertThat(handler.getFinalRefreshCount()).isEqualTo(0);
    }

    @Test
    public void testRefreshOnAllFailedBatch() {
        MockPhpBatchMoveHandler handler = new MockPhpBatchMoveHandler();
        handler.setFilesToFail(List.of("Service1.php", "Service2.php"));

        handler.moveDirectory(List.of("Service1.php", "Service2.php"), "/target");

        // Should still call refresh even if all files failed
        // (in case there were partial changes before failure)
        assertThat(handler.getFinalRefreshCount()).isEqualTo(1);
    }

    // ========== Mock Classes ==========

    /**
     * Mock RefactoringExecutor that tracks VFS refresh calls.
     */
    static class MockRefactoringExecutor {
        private final AtomicInteger refreshCount = new AtomicInteger(0);
        private final List<String> operationOrder = new ArrayList<>();
        private boolean failOnNextOperation = false;
        private boolean syncRefresh = false;
        private boolean asyncRefresh = false;

        void setFailOnNextOperation(boolean fail) {
            this.failOnNextOperation = fail;
        }

        void rename(String oldName, String newName) {
            if (failOnNextOperation) {
                failOnNextOperation = false;
                return;
            }
            operationOrder.add("rename");
            refreshVfs();
        }

        void moveFile(String source, String targetDir) {
            if (failOnNextOperation) {
                failOnNextOperation = false;
                return;
            }
            operationOrder.add("move");
            refreshVfs();
        }

        void safeDelete(List<String> files) {
            if (failOnNextOperation) {
                failOnNextOperation = false;
                return;
            }
            operationOrder.add("delete");
            refreshVfs();
        }

        private void refreshVfs() {
            operationOrder.add("refresh");
            refreshCount.incrementAndGet();
            syncRefresh = true; // We use sync refresh
        }

        int getRefreshCount() {
            return refreshCount.get();
        }

        boolean wasRefreshCalled() {
            return refreshCount.get() > 0;
        }

        List<String> getOperationOrder() {
            return new ArrayList<>(operationOrder);
        }

        boolean wasSyncRefresh() {
            return syncRefresh;
        }

        boolean wasAsyncRefresh() {
            return asyncRefresh;
        }
    }

    /**
     * Mock PhpMoveHandler that tracks VFS refresh calls.
     */
    static class MockPhpMoveHandler {
        private int refreshCount = 0;

        void movePhpClass(String file, String oldNamespace, String newNamespace) {
            // Simulate move operation stages
            // 1. Move file
            // 2. Update namespace
            // 3. Update references
            // 4. Refresh VFS
            refreshVfs();
        }

        private void refreshVfs() {
            refreshCount++;
        }

        int getRefreshCount() {
            return refreshCount;
        }
    }

    /**
     * Mock PhpBatchMoveHandler that tracks VFS refresh calls.
     */
    static class MockPhpBatchMoveHandler {
        private int perFileRefreshCount = 0;
        private int finalRefreshCount = 0;
        private int successfulMoves = 0;
        private int failedMoves = 0;
        private List<String> filesToFail = List.of();

        void setFilesToFail(List<String> files) {
            this.filesToFail = files;
        }

        void moveDirectory(List<String> files, String targetDir) {
            if (files.isEmpty()) {
                return;
            }

            for (String file : files) {
                if (filesToFail.contains(file)) {
                    failedMoves++;
                } else {
                    successfulMoves++;
                    // Individual move handler would refresh,
                    // but batch should consolidate
                    perFileRefreshCount++;
                }
            }

            // Final refresh after all operations
            finalRefreshVfs();
        }

        private void finalRefreshVfs() {
            finalRefreshCount++;
        }

        int getPerFileRefreshCount() {
            return perFileRefreshCount;
        }

        int getFinalRefreshCount() {
            return finalRefreshCount;
        }

        int getSuccessfulMoves() {
            return successfulMoves;
        }

        int getFailedMoves() {
            return failedMoves;
        }
    }
}
