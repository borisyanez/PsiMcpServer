package com.github.psiMcpServer.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.psiMcpServer.psi.PsiElementResolver;
import com.github.psiMcpServer.psi.RefactoringExecutor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;

import java.util.Optional;

/**
 * Tool for moving files to a different directory.
 */
public class MoveElementTool extends BaseTool {

    private final PsiElementResolver resolver;
    private final RefactoringExecutor executor;

    public MoveElementTool(Project project) {
        super(project);
        this.resolver = new PsiElementResolver(project);
        this.executor = new RefactoringExecutor(project);
    }

    @Override
    public String getName() {
        return "move_element";
    }

    @Override
    public String getDescription() {
        return "Move a file to a different directory and update all references.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = MAPPER.createObjectNode();

        ObjectNode sourcePath = MAPPER.createObjectNode();
        sourcePath.put("type", "string");
        sourcePath.put("description", "Absolute path to the file to move");
        properties.set("source_path", sourcePath);

        ObjectNode targetDirectory = MAPPER.createObjectNode();
        targetDirectory.put("type", "string");
        targetDirectory.put("description", "Absolute path to the target directory");
        properties.set("target_directory", targetDirectory);

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

            // Perform the move
            RefactoringExecutor.RefactoringResult result = executor.moveFile(sourceFile, targetDir);

            if (result.success()) {
                ObjectNode response = MAPPER.createObjectNode();
                response.put("source_path", sourcePath);
                response.put("target_directory", targetDirectoryPath);
                response.put("new_path", targetDirectoryPath + "/" + sourceFile.getName());
                return success(response);
            } else {
                return error(result.message());
            }

        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error("Move failed: " + e.getMessage());
        }
    }
}
