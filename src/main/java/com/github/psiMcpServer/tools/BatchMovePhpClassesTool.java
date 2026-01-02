package com.github.psiMcpServer.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.psiMcpServer.php.PhpBatchMoveHandler;
import com.github.psiMcpServer.psi.PsiElementResolver;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;

import java.util.Optional;

/**
 * Tool for batch moving PHP classes to a different namespace/directory.
 * Supports moving all files in a directory or files matching a pattern.
 * Only available when the PHP plugin is present.
 */
public class BatchMovePhpClassesTool extends BaseTool {

    private final PsiElementResolver resolver;
    private final PhpBatchMoveHandler batchHandler;

    public BatchMovePhpClassesTool(Project project) {
        super(project);
        this.resolver = new PsiElementResolver(project);
        this.batchHandler = new PhpBatchMoveHandler(project);
    }

    @Override
    public String getName() {
        return "batch_move_php_classes";
    }

    @Override
    public String getDescription() {
        return "Move multiple PHP classes to a different namespace/directory. " +
               "Supports moving all files in a directory or files matching a glob pattern. " +
               "Updates namespace declarations and all references across the project.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = MAPPER.createObjectNode();

        ObjectNode sourceDir = MAPPER.createObjectNode();
        sourceDir.put("type", "string");
        sourceDir.put("description", "Absolute path to the source directory containing PHP files to move");
        properties.set("source_directory", sourceDir);

        ObjectNode targetDir = MAPPER.createObjectNode();
        targetDir.put("type", "string");
        targetDir.put("description", "Absolute path to the target directory");
        properties.set("target_directory", targetDir);

        ObjectNode pattern = MAPPER.createObjectNode();
        pattern.put("type", "string");
        pattern.put("description", "Optional glob pattern to filter files (e.g., '*Controller.php', 'Service*.php'). If not provided, all PHP files are moved.");
        properties.set("pattern", pattern);

        ObjectNode newNamespace = MAPPER.createObjectNode();
        newNamespace.put("type", "string");
        newNamespace.put("description", "Base namespace for moved classes (optional, auto-detected from directory if not provided)");
        properties.set("new_namespace", newNamespace);

        ObjectNode recursive = MAPPER.createObjectNode();
        recursive.put("type", "boolean");
        recursive.put("description", "Whether to include subdirectories (default: true)");
        recursive.put("default", true);
        properties.set("recursive", recursive);

        ObjectNode preserveStructure = MAPPER.createObjectNode();
        preserveStructure.put("type", "boolean");
        preserveStructure.put("description", "Whether to preserve directory structure when moving (default: true)");
        preserveStructure.put("default", true);
        properties.set("preserve_structure", preserveStructure);

        schema.set("properties", properties);

        schema.set("required", MAPPER.createArrayNode()
            .add("source_directory")
            .add("target_directory"));

        return schema;
    }

    @Override
    public ToolResult execute(JsonNode arguments) {
        try {
            String sourceDirPath = getRequiredString(arguments, "source_directory");
            String targetDirPath = getRequiredString(arguments, "target_directory");
            String pattern = getOptionalString(arguments, "pattern", null);
            String newNamespace = getOptionalString(arguments, "new_namespace", null);
            boolean recursive = getOptionalBoolean(arguments, "recursive", true);
            boolean preserveStructure = getOptionalBoolean(arguments, "preserve_structure", true);

            // Resolve source directory
            Optional<PsiDirectory> sourceDirOpt = resolver.findDirectory(sourceDirPath);
            if (sourceDirOpt.isEmpty()) {
                return error("Source directory not found: " + sourceDirPath);
            }
            PsiDirectory sourceDir = sourceDirOpt.get();

            // Resolve target directory
            Optional<PsiDirectory> targetDirOpt = resolver.findDirectory(targetDirPath);
            if (targetDirOpt.isEmpty()) {
                return error("Target directory not found: " + targetDirPath);
            }
            PsiDirectory targetDir = targetDirOpt.get();

            // Check if source and target are the same
            if (sourceDir.equals(targetDir)) {
                return error("Source and target directories are the same");
            }

            // Perform the batch move
            PhpBatchMoveHandler.BatchMoveResult result;

            if (pattern != null && !pattern.isEmpty()) {
                result = batchHandler.moveByPattern(
                    sourceDir,
                    pattern,
                    targetDir,
                    newNamespace,
                    recursive
                );
            } else {
                result = batchHandler.moveDirectory(
                    sourceDir,
                    targetDir,
                    newNamespace,
                    recursive
                );
            }

            if (result.success()) {
                ObjectNode response = MAPPER.createObjectNode();
                response.put("success", true);
                response.put("message", result.message());
                response.put("total_files", result.totalFiles());
                response.put("moved_files", result.movedFiles());
                response.put("failed_files", result.failedFiles());

                // Add details for each file
                ArrayNode detailsArray = MAPPER.createArrayNode();
                for (PhpBatchMoveHandler.FileMoveResult fileResult : result.details()) {
                    ObjectNode fileNode = MAPPER.createObjectNode();
                    fileNode.put("original_path", fileResult.originalPath());
                    fileNode.put("success", fileResult.success());
                    fileNode.put("message", fileResult.message());
                    if (fileResult.success()) {
                        fileNode.put("new_path", fileResult.newPath());
                        fileNode.put("new_fqn", fileResult.newFqn());
                        fileNode.put("references_updated", fileResult.referencesUpdated());
                    }
                    detailsArray.add(fileNode);
                }
                response.set("details", detailsArray);

                return new ToolResult(true, response);
            } else {
                return error(result.message());
            }

        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error("Batch move failed: " + e.getMessage());
        }
    }
}
