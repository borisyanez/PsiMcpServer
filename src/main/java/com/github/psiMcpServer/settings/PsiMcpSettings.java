package com.github.psiMcpServer.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Persists PSI MCP Server settings across IDE restarts.
 */
@State(
    name = "com.github.psiMcpServer.settings.PsiMcpSettings",
    storages = @Storage("PsiMcpSettings.xml")
)
public final class PsiMcpSettings implements PersistentStateComponent<PsiMcpSettings.State> {

    /**
     * Settings state that gets serialized to XML.
     */
    public static class State {
        public boolean autoStartServer = false;
        public int port = 3000;  // Reserved for future HTTP transport
        public boolean enableLogging = true;
        public String logLevel = "INFO";
        public boolean searchInComments = true;
        public boolean searchInStrings = true;
    }

    private State myState = new State();

    public static PsiMcpSettings getInstance() {
        return ApplicationManager.getApplication().getService(PsiMcpSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    // Convenience accessors
    public boolean isAutoStartServer() {
        return myState.autoStartServer;
    }

    public void setAutoStartServer(boolean value) {
        myState.autoStartServer = value;
    }

    public int getPort() {
        return myState.port;
    }

    public void setPort(int value) {
        myState.port = value;
    }

    public boolean isEnableLogging() {
        return myState.enableLogging;
    }

    public void setEnableLogging(boolean value) {
        myState.enableLogging = value;
    }

    public String getLogLevel() {
        return myState.logLevel;
    }

    public void setLogLevel(String value) {
        myState.logLevel = value;
    }

    public boolean isSearchInComments() {
        return myState.searchInComments;
    }

    public void setSearchInComments(boolean value) {
        myState.searchInComments = value;
    }

    public boolean isSearchInStrings() {
        return myState.searchInStrings;
    }

    public void setSearchInStrings(boolean value) {
        myState.searchInStrings = value;
    }
}
