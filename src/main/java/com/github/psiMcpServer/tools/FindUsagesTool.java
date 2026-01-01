package com.github.psiMcpServer.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.psiMcpServer.psi.PsiElementResolver;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;

import java.util.Collection;
import java.util.Optional;

/**
 * Tool for finding all usages of a code element.
 */
public class FindUsagesTool extends BaseTool {

    private final PsiElementResolver resolver;

    public FindUsagesTool(Project project) {
        super(project);
        this.resolver = new PsiElementResolver(project);
    }

    @Override
    public String getName() {
        return "find_usages";
    }

    @Override
    public String getDescription() {
        return "Find all usages/references of a code element in the project.";
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
        elementName.put("description", "Name of the element to find usages for");
        properties.set("element_name", elementName);

        ObjectNode offset = MAPPER.createObjectNode();
        offset.put("type", "integer");
        offset.put("description", "Optional: Character offset in file to identify element precisely");
        properties.set("offset", offset);

        ObjectNode maxResults = MAPPER.createObjectNode();
        maxResults.put("type", "integer");
        maxResults.put("description", "Maximum number of results to return (default: 100)");
        maxResults.put("default", 100);
        properties.set("max_results", maxResults);

        schema.set("properties", properties);

        schema.set("required", MAPPER.createArrayNode()
            .add("file_path")
            .add("element_name"));

        return schema;
    }

    @Override
    public ToolResult execute(JsonNode arguments) {
        try {
            String filePath = getRequiredString(arguments, "file_path");
            String elementName = getRequiredString(arguments, "element_name");
            int offset = getOptionalInt(arguments, "offset", -1);
            int maxResults = getOptionalInt(arguments, "max_results", 100);

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

            // Find usages
            ArrayNode usagesArray = MAPPER.createArrayNode();
            int[] count = {0};

            ApplicationManager.getApplication().runReadAction(() -> {
                Collection<PsiReference> references = ReferencesSearch.search(element).findAll();

                for (PsiReference reference : references) {
                    if (count[0] >= maxResults) {
                        break;
                    }

                    PsiElement refElement = reference.getElement();
                    PsiFile refFile = refElement.getContainingFile();

                    if (refFile != null && refFile.getVirtualFile() != null) {
                        ObjectNode usage = MAPPER.createObjectNode();
                        usage.put("file", refFile.getVirtualFile().getPath());
                        usage.put("offset", refElement.getTextOffset());
                        usage.put("length", refElement.getTextLength());

                        // Get line number (approximate)
                        String text = refFile.getText();
                        int lineNumber = 1;
                        int textOffset = refElement.getTextOffset();
                        for (int i = 0; i < textOffset && i < text.length(); i++) {
                            if (text.charAt(i) == '\n') {
                                lineNumber++;
                            }
                        }
                        usage.put("line", lineNumber);

                        // Get context (surrounding text)
                        int contextStart = Math.max(0, textOffset - 30);
                        int contextEnd = Math.min(text.length(), textOffset + refElement.getTextLength() + 30);
                        String context = text.substring(contextStart, contextEnd)
                            .replace("\n", " ")
                            .trim();
                        usage.put("context", context);

                        usagesArray.add(usage);
                        count[0]++;
                    }
                }
            });

            ObjectNode response = MAPPER.createObjectNode();
            response.put("element", elementName);
            response.put("file_path", filePath);
            response.put("usage_count", usagesArray.size());
            response.put("truncated", count[0] >= maxResults);
            response.set("usages", usagesArray);

            return success(response);

        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error("Find usages failed: " + e.getMessage());
        }
    }
}
