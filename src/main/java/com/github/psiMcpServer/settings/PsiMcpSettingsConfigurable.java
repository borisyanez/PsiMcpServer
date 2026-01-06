package com.github.psiMcpServer.settings;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

/**
 * Settings configurable for PSI MCP Server.
 * Appears in Settings > Tools > PSI MCP Server.
 */
public final class PsiMcpSettingsConfigurable implements Configurable {

    private PsiMcpSettingsComponent component;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "PSI MCP Server";
    }

    @Override
    public @Nullable JComponent createComponent() {
        component = new PsiMcpSettingsComponent();
        return component.getPanel();
    }

    @Override
    public boolean isModified() {
        PsiMcpSettings settings = PsiMcpSettings.getInstance();
        return component.getAutoStart() != settings.isAutoStartServer()
            || component.getPort() != settings.getPort()
            || component.getEnableLogging() != settings.isEnableLogging()
            || !Objects.equals(component.getLogLevel(), settings.getLogLevel())
            || component.getSearchInComments() != settings.isSearchInComments()
            || component.getSearchInStrings() != settings.isSearchInStrings()
            || component.getApplyCodeStyleFixes() != settings.isApplyCodeStyleFixes();
    }

    @Override
    public void apply() {
        PsiMcpSettings settings = PsiMcpSettings.getInstance();
        settings.setAutoStartServer(component.getAutoStart());
        settings.setPort(component.getPort());
        settings.setEnableLogging(component.getEnableLogging());
        settings.setLogLevel(component.getLogLevel());
        settings.setSearchInComments(component.getSearchInComments());
        settings.setSearchInStrings(component.getSearchInStrings());
        settings.setApplyCodeStyleFixes(component.getApplyCodeStyleFixes());
    }

    @Override
    public void reset() {
        PsiMcpSettings settings = PsiMcpSettings.getInstance();
        component.setAutoStart(settings.isAutoStartServer());
        component.setPort(settings.getPort());
        component.setEnableLogging(settings.isEnableLogging());
        component.setLogLevel(settings.getLogLevel());
        component.setSearchInComments(settings.isSearchInComments());
        component.setSearchInStrings(settings.isSearchInStrings());
        component.setApplyCodeStyleFixes(settings.isApplyCodeStyleFixes());
    }

    @Override
    public void disposeUIResources() {
        component = null;
    }
}
