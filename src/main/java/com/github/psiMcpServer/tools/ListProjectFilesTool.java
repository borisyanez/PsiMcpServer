package com.github.psiMcpServer.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.VfsUtilCore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Tool for listing project files, optionally filtered by pattern.
 */
public class ListProjectFilesTool extends BaseTool {

    public ListProjectFilesTool(Project project) {
        super(project);
    }

    @Override
    public String getName() {
        return "list_project_files";
    }

    @Override
    public String getDescription() {
        return "List files in the project, optionally filtered by glob pattern or directory.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = MAPPER.createObjectNode();

        ObjectNode directory = MAPPER.createObjectNode();
        directory.put("type", "string");
        directory.put("description", "Optional: Absolute path to limit search to a specific directory");
        properties.set("directory", directory);

        ObjectNode pattern = MAPPER.createObjectNode();
        pattern.put("type", "string");
        pattern.put("description", "Optional: Glob pattern to filter files (e.g., '*.java', '*.php', '**/*.ts')");
        properties.set("pattern", pattern);

        ObjectNode maxResults = MAPPER.createObjectNode();
        maxResults.put("type", "integer");
        maxResults.put("description", "Maximum number of files to return (default: 500)");
        maxResults.put("default", 500);
        properties.set("max_results", maxResults);

        ObjectNode includeHidden = MAPPER.createObjectNode();
        includeHidden.put("type", "boolean");
        includeHidden.put("description", "Include hidden files and directories (default: false)");
        includeHidden.put("default", false);
        properties.set("include_hidden", includeHidden);

        schema.set("properties", properties);
        schema.set("required", MAPPER.createArrayNode());

        return schema;
    }

    @Override
    public ToolResult execute(JsonNode arguments) {
        try {
            String directory = getOptionalString(arguments, "directory", null);
            String pattern = getOptionalString(arguments, "pattern", null);
            int maxResults = getOptionalInt(arguments, "max_results", 500);
            boolean includeHidden = getOptionalBoolean(arguments, "include_hidden", false);

            // Convert glob pattern to regex
            Pattern regex = pattern != null ? globToRegex(pattern) : null;

            List<String> files = new ArrayList<>();
            ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);

            ApplicationManager.getApplication().runReadAction(() -> {
                VirtualFile baseDir;
                if (directory != null) {
                    baseDir = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                        .findFileByPath(directory);
                } else {
                    baseDir = project.getBaseDir();
                }

                if (baseDir == null) {
                    return;
                }

                VfsUtilCore.visitChildrenRecursively(baseDir, new VirtualFileVisitor<Void>() {
                    @Override
                    public boolean visitFile(VirtualFile file) {
                        if (files.size() >= maxResults) {
                            return false; // Stop visiting
                        }

                        // Skip hidden files unless requested
                        if (!includeHidden && file.getName().startsWith(".")) {
                            return false;
                        }

                        // Skip non-project files
                        if (!fileIndex.isInContent(file)) {
                            return true; // Continue to children
                        }

                        if (!file.isDirectory()) {
                            String path = file.getPath();

                            // Apply pattern filter
                            if (regex == null || regex.matcher(file.getName()).matches()
                                || regex.matcher(path).matches()) {
                                files.add(path);
                            }
                        }

                        return true;
                    }
                });
            });

            ObjectNode response = MAPPER.createObjectNode();
            ArrayNode filesArray = MAPPER.createArrayNode();
            for (String file : files) {
                filesArray.add(file);
            }
            response.set("files", filesArray);
            response.put("count", files.size());
            response.put("truncated", files.size() >= maxResults);

            if (directory != null) {
                response.put("directory", directory);
            }
            if (pattern != null) {
                response.put("pattern", pattern);
            }

            return success(response);

        } catch (Exception e) {
            return error("Failed to list files: " + e.getMessage());
        }
    }

    /**
     * Convert a glob pattern to a regex pattern.
     */
    private Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        regex.append(".*");
                        i++; // Skip next *
                        if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                            i++; // Skip /
                        }
                    } else {
                        regex.append("[^/]*");
                    }
                    break;
                case '?':
                    regex.append("[^/]");
                    break;
                case '.':
                case '(':
                case ')':
                case '[':
                case ']':
                case '{':
                case '}':
                case '+':
                case '^':
                case '$':
                case '|':
                case '\\':
                    regex.append("\\").append(c);
                    break;
                default:
                    regex.append(c);
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }
}
