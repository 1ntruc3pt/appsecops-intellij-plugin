package com.intrucept.appsecops.intellijplugin.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.components.JBScrollPane
import javax.swing.*
import java.awt.BorderLayout

class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Create a panel with BorderLayout
        val panel = JPanel(BorderLayout())

        // Create a text area to display scan results
        val textArea = JTextArea()
        textArea.isEditable = false // Make it read-only

        // Create a JBScrollPane and add the text area to it
        val scrollPane = JBScrollPane(textArea)
        scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED

        // Add padding of 20px around the scroll pane
        scrollPane.border = BorderFactory.createEmptyBorder(20, 20, 20, 20)

        // Add the scroll pane to the panel, filling the entire space
        panel.add(scrollPane, BorderLayout.CENTER)

        // Add the panel to the tool window
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)

        // Register the text area so that it can display scan results
        IntruceptScanManager.registerTextArea(textArea)
    }
}