package com.github.psiMcpServer.settings;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * UI component for PSI MCP Server settings.
 */
public class PsiMcpSettingsComponent {

    private final JPanel mainPanel;
    private final JBCheckBox autoStartCheckbox = new JBCheckBox("Auto-start MCP server on IDE launch");
    private final JBTextField portField = new JBTextField();
    private final JBCheckBox enableLoggingCheckbox = new JBCheckBox("Enable logging");
    private final JComboBox<String> logLevelCombo = new JComboBox<>(
        new String[]{"DEBUG", "INFO", "WARN", "ERROR"});
    private final JBCheckBox searchInCommentsCheckbox = new JBCheckBox("Search in comments during refactoring");
    private final JBCheckBox searchInStringsCheckbox = new JBCheckBox("Search in strings during refactoring");

    public PsiMcpSettingsComponent() {
        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(autoStartCheckbox, 1)
            .addLabeledComponent(new JBLabel("Port (for future HTTP transport):"), portField, 1, false)
            .addSeparator()
            .addComponent(enableLoggingCheckbox, 1)
            .addLabeledComponent(new JBLabel("Log level:"), logLevelCombo, 1, false)
            .addSeparator()
            .addComponent(searchInCommentsCheckbox, 1)
            .addComponent(searchInStringsCheckbox, 1)
            .addComponentFillVertically(new JPanel(), 0)
            .getPanel();
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    @NotNull
    public JComponent getPreferredFocusedComponent() {
        return autoStartCheckbox;
    }

    public boolean getAutoStart() {
        return autoStartCheckbox.isSelected();
    }

    public void setAutoStart(boolean value) {
        autoStartCheckbox.setSelected(value);
    }

    public int getPort() {
        try {
            return Integer.parseInt(portField.getText());
        } catch (NumberFormatException e) {
            return 3000;
        }
    }

    public void setPort(int value) {
        portField.setText(String.valueOf(value));
    }

    public boolean getEnableLogging() {
        return enableLoggingCheckbox.isSelected();
    }

    public void setEnableLogging(boolean value) {
        enableLoggingCheckbox.setSelected(value);
    }

    public String getLogLevel() {
        return (String) logLevelCombo.getSelectedItem();
    }

    public void setLogLevel(String value) {
        logLevelCombo.setSelectedItem(value);
    }

    public boolean getSearchInComments() {
        return searchInCommentsCheckbox.isSelected();
    }

    public void setSearchInComments(boolean value) {
        searchInCommentsCheckbox.setSelected(value);
    }

    public boolean getSearchInStrings() {
        return searchInStringsCheckbox.isSelected();
    }

    public void setSearchInStrings(boolean value) {
        searchInStringsCheckbox.setSelected(value);
    }
}
