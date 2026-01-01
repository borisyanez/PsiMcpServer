package com.github.psiMcpServer.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.psiMcpServer.psi.PsiElementResolver;
import com.github.psiMcpServer.psi.RefactoringExecutor;
import com.github.psiMcpServer.settings.PsiMcpSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiNamedElement;

import java.util.Optional;

/**
 * Tool for renaming PSI elements (classes, methods, variables, files).
 */
public class RenameElementTool extends BaseTool {

    private final PsiElementResolver resolver;
    private final RefactoringExecutor executor;

    public RenameElementTool(Project project) {
        super(project);
        this.resolver = new PsiElementResolver(project);
        this.executor = new RefactoringExecutor(project);
    }

    @Override
    public String getName() {
        return "rename_element";
    }

    @Override
    public String getDescription() {
        return "Rename a code element (class, method, variable, field, or file) and update all references.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = MAPPER.createObjectNode();

        ObjectNode filePath = MAPPER.createObjectNode();
        filePath.put("type", "string");
        filePath.put("description", "Absolute path to the file containing the element");
        properties.set("file_path", filePath);

        ObjectNode elementName = MAPPER.createObjectNode();
        elementName.put("type", "string");
        elementName.put("description", "Current name of the element to rename");
        properties.set("element_name", elementName);

        ObjectNode newName = MAPPER.createObjectNode();
        newName.put("type", "string");
        newName.put("description", "New name for the element");
        properties.set("new_name", newName);

        ObjectNode offset = MAPPER.createObjectNode();
        offset.put("type", "integer");
        offset.put("description", "Optional: Character offset in file to identify element precisely");
        properties.set("offset", offset);

        schema.set("properties", properties);

        schema.set("required", MAPPER.createArrayNode()
            .add("file_path")
            .add("element_name")
            .add("new_name"));

        return schema;
    }

    @Override
    public ToolResult execute(JsonNode arguments) {
        try {
            String filePath = getRequiredString(arguments, "file_path");
            String elementName = getRequiredString(arguments, "element_name");
            String newName = getRequiredString(arguments, "new_name");
            int offset = getOptionalInt(arguments, "offset", -1);

            // Validate new name
            if (newName.isEmpty()) {
                return error("New name cannot be empty");
            }

            if (newName.equals(elementName)) {
                return error("New name is the same as the current name");
            }

            // Find the element
            Optional<PsiNamedElement> elementOpt;
            if (offset >= 0) {
                elementOpt = resolver.findNamedElementAtOffset(filePath, offset);
            } else {
                elementOpt = resolver.findNamedElement(filePath, elementName);
            }

            if (elementOpt.isEmpty()) {
                return error("Could not find element '" + elementName + "' in " + filePath);
            }

            PsiNamedElement element = elementOpt.get();

            // Get settings for search options
            PsiMcpSettings settings = PsiMcpSettings.getInstance();
            boolean searchInComments = settings.isSearchInComments();
            boolean searchInStrings = settings.isSearchInStrings();

            // Perform the rename
            RefactoringExecutor.RefactoringResult result = executor.rename(
                element, newName, searchInComments, searchInStrings
            );

            if (result.success()) {
                ObjectNode response = MAPPER.createObjectNode();
                response.put("old_name", elementName);
                response.put("new_name", newName);
                response.put("file_path", filePath);
                return success(response);
            } else {
                return error(result.message());
            }

        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error("Rename failed: " + e.getMessage());
        }
    }
}
