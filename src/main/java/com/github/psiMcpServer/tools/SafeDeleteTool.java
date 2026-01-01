package com.github.psiMcpServer.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.psiMcpServer.psi.PsiElementResolver;
import com.github.psiMcpServer.psi.RefactoringExecutor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;

import java.util.Optional;

/**
 * Tool for safely deleting elements after checking for usages.
 */
public class SafeDeleteTool extends BaseTool {

    private final PsiElementResolver resolver;
    private final RefactoringExecutor executor;

    public SafeDeleteTool(Project project) {
        super(project);
        this.resolver = new PsiElementResolver(project);
        this.executor = new RefactoringExecutor(project);
    }

    @Override
    public String getName() {
        return "safe_delete";
    }

    @Override
    public String getDescription() {
        return "Safely delete a code element or file after checking for usages.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = MAPPER.createObjectNode();

        ObjectNode filePath = MAPPER.createObjectNode();
        filePath.put("type", "string");
        filePath.put("description", "Absolute path to the file containing the element (or the file itself to delete)");
        properties.set("file_path", filePath);

        ObjectNode elementName = MAPPER.createObjectNode();
        elementName.put("type", "string");
        elementName.put("description", "Name of the element to delete (omit to delete the entire file)");
        properties.set("element_name", elementName);

        ObjectNode checkUsages = MAPPER.createObjectNode();
        checkUsages.put("type", "boolean");
        checkUsages.put("description", "Whether to check for usages before deleting (default: true)");
        checkUsages.put("default", true);
        properties.set("check_usages", checkUsages);

        schema.set("properties", properties);

        schema.set("required", MAPPER.createArrayNode()
            .add("file_path"));

        return schema;
    }

    @Override
    public ToolResult execute(JsonNode arguments) {
        try {
            String filePath = getRequiredString(arguments, "file_path");
            String elementName = getOptionalString(arguments, "element_name", null);
            boolean checkUsages = getOptionalBoolean(arguments, "check_usages", true);

            PsiElement elementToDelete;

            if (elementName == null || elementName.isEmpty()) {
                // Delete the entire file
                Optional<PsiFile> fileOpt = resolver.resolveFile(filePath);
                if (fileOpt.isEmpty()) {
                    return error("File not found: " + filePath);
                }
                elementToDelete = fileOpt.get();
            } else {
                // Delete a specific element
                Optional<PsiNamedElement> elementOpt = resolver.findNamedElement(filePath, elementName);
                if (elementOpt.isEmpty()) {
                    return error("Element '" + elementName + "' not found in " + filePath);
                }
                elementToDelete = elementOpt.get();
            }

            // Perform the delete
            RefactoringExecutor.RefactoringResult result = executor.safeDelete(
                new PsiElement[]{elementToDelete}, checkUsages
            );

            if (result.success()) {
                ObjectNode response = MAPPER.createObjectNode();
                response.put("file_path", filePath);
                if (elementName != null) {
                    response.put("element_name", elementName);
                }
                response.put("deleted", true);
                return success(response);
            } else {
                return error(result.message());
            }

        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error("Delete failed: " + e.getMessage());
        }
    }
}
