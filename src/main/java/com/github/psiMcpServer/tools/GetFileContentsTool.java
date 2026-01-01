package com.github.psiMcpServer.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.psiMcpServer.psi.PsiElementResolver;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

import java.util.Optional;

/**
 * Tool for reading the contents of a file.
 */
public class GetFileContentsTool extends BaseTool {

    private final PsiElementResolver resolver;

    public GetFileContentsTool(Project project) {
        super(project);
        this.resolver = new PsiElementResolver(project);
    }

    @Override
    public String getName() {
        return "get_file_contents";
    }

    @Override
    public String getDescription() {
        return "Read the contents of a file, optionally specifying a line range.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = MAPPER.createObjectNode();

        ObjectNode filePath = MAPPER.createObjectNode();
        filePath.put("type", "string");
        filePath.put("description", "Absolute path to the file to read");
        properties.set("file_path", filePath);

        ObjectNode startLine = MAPPER.createObjectNode();
        startLine.put("type", "integer");
        startLine.put("description", "Optional: Start line number (1-based)");
        properties.set("start_line", startLine);

        ObjectNode endLine = MAPPER.createObjectNode();
        endLine.put("type", "integer");
        endLine.put("description", "Optional: End line number (1-based, inclusive)");
        properties.set("end_line", endLine);

        schema.set("properties", properties);

        schema.set("required", MAPPER.createArrayNode()
            .add("file_path"));

        return schema;
    }

    @Override
    public ToolResult execute(JsonNode arguments) {
        try {
            String filePath = getRequiredString(arguments, "file_path");
            int startLine = getOptionalInt(arguments, "start_line", 1);
            int endLine = getOptionalInt(arguments, "end_line", -1);

            // Resolve the file
            Optional<PsiFile> fileOpt = resolver.resolveFile(filePath);
            if (fileOpt.isEmpty()) {
                return error("File not found: " + filePath);
            }

            PsiFile psiFile = fileOpt.get();

            String[] result = new String[1];
            int[] lineCount = new int[1];
            String[] language = new String[1];

            ApplicationManager.getApplication().runReadAction(() -> {
                String fullText = psiFile.getText();
                String[] lines = fullText.split("\n", -1);
                lineCount[0] = lines.length;

                // Get language
                language[0] = psiFile.getLanguage().getDisplayName();

                // Apply line range
                if (startLine > 1 || endLine > 0) {
                    int start = Math.max(1, startLine) - 1; // Convert to 0-based
                    int end = endLine > 0 ? Math.min(endLine, lines.length) : lines.length;

                    if (start >= lines.length) {
                        result[0] = "";
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (int i = start; i < end; i++) {
                            if (sb.length() > 0) {
                                sb.append("\n");
                            }
                            sb.append(lines[i]);
                        }
                        result[0] = sb.toString();
                    }
                } else {
                    result[0] = fullText;
                }
            });

            ObjectNode response = MAPPER.createObjectNode();
            response.put("file_path", filePath);
            response.put("content", result[0]);
            response.put("line_count", lineCount[0]);
            response.put("language", language[0]);

            if (startLine > 1 || endLine > 0) {
                response.put("start_line", startLine);
                response.put("end_line", endLine > 0 ? endLine : lineCount[0]);
            }

            return success(response);

        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error("Failed to read file: " + e.getMessage());
        }
    }
}
