package com.github.psiMcpServer.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.psiMcpServer.php.PhpMoveHandler;
import com.github.psiMcpServer.psi.PsiElementResolver;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tool for moving PHP classes to a different namespace/directory.
 * Updates the namespace declaration and all references.
 * Only available when the PHP plugin is present.
 */
public class MovePhpClassTool extends BaseTool {

    private final PsiElementResolver resolver;
    private final PhpMoveHandler phpMoveHandler;

    public MovePhpClassTool(Project project) {
        super(project);
        this.resolver = new PsiElementResolver(project);
        this.phpMoveHandler = new PhpMoveHandler(project);
    }

    @Override
    public String getName() {
        return "move_php_class";
    }

    @Override
    public String getDescription() {
        return "Move a PHP class to a different namespace/directory. Updates the namespace declaration in the file and all references throughout the project.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = MAPPER.createObjectNode();

        ObjectNode sourcePath = MAPPER.createObjectNode();
        sourcePath.put("type", "string");
        sourcePath.put("description", "Absolute path to the PHP file containing the class to move");
        properties.set("source_path", sourcePath);

        ObjectNode targetDirectory = MAPPER.createObjectNode();
        targetDirectory.put("type", "string");
        targetDirectory.put("description", "Absolute path to the target directory");
        properties.set("target_directory", targetDirectory);

        ObjectNode newNamespace = MAPPER.createObjectNode();
        newNamespace.put("type", "string");
        newNamespace.put("description", "New namespace for the class (optional, auto-detected from directory if not provided)");
        properties.set("new_namespace", newNamespace);

        schema.set("properties", properties);

        schema.set("required", MAPPER.createArrayNode()
            .add("source_path")
            .add("target_directory"));

        return schema;
    }

    @Override
    public ToolResult execute(JsonNode arguments) {
        try {
            String sourcePath = getRequiredString(arguments, "source_path");
            String targetDirectoryPath = getRequiredString(arguments, "target_directory");
            String newNamespace = getOptionalString(arguments, "new_namespace", null);

            // Validate it's a PHP file
            if (!sourcePath.toLowerCase().endsWith(".php")) {
                return error("Source file must be a PHP file (.php extension)");
            }

            // Resolve source file
            Optional<PsiFile> sourceFileOpt = resolver.resolveFile(sourcePath);
            if (sourceFileOpt.isEmpty()) {
                return error("Source file not found: " + sourcePath);
            }
            PsiFile sourceFile = sourceFileOpt.get();

            // Resolve target directory
            Optional<PsiDirectory> targetDirOpt = resolver.findDirectory(targetDirectoryPath);
            if (targetDirOpt.isEmpty()) {
                return error("Target directory not found: " + targetDirectoryPath);
            }
            PsiDirectory targetDir = targetDirOpt.get();

            // Check if source and target are the same
            PsiDirectory sourceDir = sourceFile.getContainingDirectory();
            if (sourceDir != null && sourceDir.equals(targetDir)) {
                return error("Source file is already in the target directory");
            }

            // Extract class name for the task title
            String fileName = sourceFile.getName();
            String className = fileName.endsWith(".php") ? fileName.substring(0, fileName.length() - 4) : fileName;

            // Perform the PHP class move with progress reporting
            AtomicReference<PhpMoveHandler.MoveResult> resultRef = new AtomicReference<>();
            AtomicReference<Exception> exceptionRef = new AtomicReference<>();

            ProgressManager.getInstance().run(new Task.WithResult<Void, RuntimeException>(project, "Moving PHP Class: " + className, false) {
                @Override
                protected Void compute(@NotNull ProgressIndicator indicator) {
                    try {
                        indicator.setIndeterminate(false);
                        PhpMoveHandler.MoveResult result = phpMoveHandler.movePhpClass(
                            sourceFile,
                            targetDir,
                            newNamespace,
                            indicator
                        );
                        resultRef.set(result);
                    } catch (Exception e) {
                        exceptionRef.set(e);
                    }
                    return null;
                }
            });

            // Check for exceptions
            if (exceptionRef.get() != null) {
                return error("PHP class move failed: " + exceptionRef.get().getMessage());
            }

            PhpMoveHandler.MoveResult result = resultRef.get();
            if (result == null) {
                return error("PHP class move failed: no result returned");
            }

            if (result.success()) {
                ObjectNode response = MAPPER.createObjectNode();
                response.put("success", true);
                response.put("message", result.message());
                response.put("source_path", sourcePath);
                response.put("target_directory", targetDirectoryPath);
                response.put("new_fqn", result.newFqn());
                response.put("references_updated", result.referencesUpdated());
                return new ToolResult(true, response);
            } else {
                return error(result.message());
            }

        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error("PHP class move failed: " + e.getMessage());
        }
    }
}
