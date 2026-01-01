package com.github.psiMcpServer.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.psiMcpServer.psi.PsiElementResolver;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;

import java.util.List;

/**
 * Tool for getting detailed information about PSI elements in a file.
 */
public class GetElementInfoTool extends BaseTool {

    private final PsiElementResolver resolver;

    public GetElementInfoTool(Project project) {
        super(project);
        this.resolver = new PsiElementResolver(project);
    }

    @Override
    public String getName() {
        return "get_element_info";
    }

    @Override
    public String getDescription() {
        return "Get detailed information about code elements (classes, methods, variables) in a file.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = MAPPER.createObjectNode();

        ObjectNode filePath = MAPPER.createObjectNode();
        filePath.put("type", "string");
        filePath.put("description", "Absolute path to the file");
        properties.set("file_path", filePath);

        ObjectNode elementName = MAPPER.createObjectNode();
        elementName.put("type", "string");
        elementName.put("description", "Optional: Name of a specific element to get info for (omit to list all elements)");
        properties.set("element_name", elementName);

        ObjectNode offset = MAPPER.createObjectNode();
        offset.put("type", "integer");
        offset.put("description", "Optional: Character offset to find element at a specific position");
        properties.set("offset", offset);

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
            int offset = getOptionalInt(arguments, "offset", -1);

            ObjectNode response = MAPPER.createObjectNode();
            response.put("file_path", filePath);

            if (elementName != null || offset >= 0) {
                // Get info for a specific element
                PsiNamedElement element;
                if (offset >= 0) {
                    element = resolver.findNamedElementAtOffset(filePath, offset).orElse(null);
                } else {
                    element = resolver.findNamedElement(filePath, elementName).orElse(null);
                }

                if (element == null) {
                    return error("Element not found");
                }

                ObjectNode elementInfo = getElementInfo(element);
                response.set("element", elementInfo);
            } else {
                // List all named elements in the file
                List<PsiNamedElement> elements = resolver.getAllNamedElements(filePath);

                ArrayNode elementsArray = MAPPER.createArrayNode();
                for (PsiNamedElement element : elements) {
                    // Filter to significant elements (skip whitespace, etc.)
                    if (element.getName() != null && !element.getName().isEmpty()) {
                        ObjectNode info = getElementInfo(element);
                        elementsArray.add(info);
                    }
                }
                response.set("elements", elementsArray);
                response.put("count", elementsArray.size());
            }

            return success(response);

        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error("Failed to get element info: " + e.getMessage());
        }
    }

    /**
     * Get detailed information about a PSI element.
     */
    private ObjectNode getElementInfo(PsiNamedElement element) {
        return ApplicationManager.getApplication().runReadAction((Computable<ObjectNode>) () -> {
            ObjectNode info = MAPPER.createObjectNode();

            info.put("name", element.getName());
            info.put("type", getElementType(element));
            info.put("offset", element.getTextOffset());
            info.put("length", element.getTextLength());

            // Get line number
            PsiFile file = element.getContainingFile();
            if (file != null) {
                String text = file.getText();
                int lineNumber = 1;
                int textOffset = element.getTextOffset();
                for (int i = 0; i < textOffset && i < text.length(); i++) {
                    if (text.charAt(i) == '\n') {
                        lineNumber++;
                    }
                }
                info.put("line", lineNumber);
            }

            // Get parent info
            PsiElement parent = element.getParent();
            if (parent instanceof PsiNamedElement namedParent && namedParent.getName() != null) {
                ObjectNode parentInfo = MAPPER.createObjectNode();
                parentInfo.put("name", namedParent.getName());
                parentInfo.put("type", getElementType(namedParent));
                info.set("parent", parentInfo);
            }

            return info;
        });
    }

    /**
     * Get a human-readable type name for an element.
     */
    private String getElementType(PsiElement element) {
        String className = element.getClass().getSimpleName();

        // Map common PSI class names to readable types
        if (className.contains("Class")) {
            return "class";
        } else if (className.contains("Method") || className.contains("Function")) {
            return "method";
        } else if (className.contains("Field")) {
            return "field";
        } else if (className.contains("Variable") || className.contains("Parameter")) {
            return "variable";
        } else if (className.contains("Property")) {
            return "property";
        } else if (className.contains("Constant")) {
            return "constant";
        } else if (className.contains("Interface")) {
            return "interface";
        } else if (className.contains("Trait")) {
            return "trait";
        } else if (className.contains("Enum")) {
            return "enum";
        } else if (className.contains("Namespace") || className.contains("Package")) {
            return "namespace";
        } else {
            return className.replaceAll("Psi|Impl", "").toLowerCase();
        }
    }
}
