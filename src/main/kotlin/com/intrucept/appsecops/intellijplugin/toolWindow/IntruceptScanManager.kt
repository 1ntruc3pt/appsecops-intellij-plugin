package com.intrucept.appsecops.intellijplugin.toolWindow

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.swing.JTextArea
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.SwingUtilities
import java.awt.BorderLayout

// Data class for ScanResponse
@Serializable
data class ScanResponse(
    val vulnsTable: String,
    @SerialName("canProceed") val canProceed: Boolean? = null // Example additional field
)

object IntruceptScanManager {
    private var textArea: JTextArea? = null

    // Register the text area so it can be updated with scan results
    fun registerTextArea(textArea: JTextArea) {
        IntruceptScanManager.textArea = textArea
    }

    fun updateSASTScanResults(responseText: String) {
        // Parse the JSON response
        val response = parseResponse(responseText)
        val message = "SAST Scan Result:"
        val table = response.vulnsTable

        // Convert ANSI escape codes to plain text (if necessary)
        val cleanTable = stripAnsiCodes(table)

        val formattedMessage = "$message\n\n$cleanTable\n"
        SwingUtilities.invokeLater {
            textArea?.append(formattedMessage)
            textArea?.caret?.run {
                // Ensure that the caret is at the end of the document
                setDot(textArea!!.document.length)
            }
        }
    }

    fun updateSCAScanResults(responseText: String) {
        // Parse the JSON response
        val response = parseResponse(responseText)
        val message = "SCA Scan Result:"
        val table = response.vulnsTable

        // Convert ANSI escape codes to plain text (if necessary)
        val cleanTable = stripAnsiCodes(table)

        val formattedMessage = "$message\n\n$cleanTable\n"
        SwingUtilities.invokeLater {
            textArea?.append(formattedMessage)
            textArea?.caret?.run {
                // Ensure that the caret is at the end of the document
                setDot(textArea!!.document.length)
            }
        }
    }

    // Notify that SAST Scan has been initiated
    fun notifySASTScanInitiated(projectName: String) {
        val message = "SAST Scan initiated on $projectName\n"
        SwingUtilities.invokeLater {
            textArea?.append(message)
            textArea?.caret?.run {
                // Ensure that the caret is at the end of the document
                setDot(textArea!!.document.length)
            }
        }
    }

    // Notify that SCA Scan has been initiated
    fun notifySCAScanInitiated(projectName: String) {
        val message = "SCA Scan initiated on $projectName\n"
        SwingUtilities.invokeLater {
            textArea?.append(message)
            textArea?.caret?.run {
                // Ensure that the caret is at the end of the document
                setDot(textArea!!.document.length)
            }
        }
    }

    // Parse JSON response to ScanResponse
    private fun parseResponse(responseText: String): ScanResponse {
        val json = Json {
            ignoreUnknownKeys = true
        }
        return json.decodeFromString(responseText)
    }

    // Function to strip ANSI escape codes
    private fun stripAnsiCodes(text: String): String {
        return text.replace(Regex("\\x1b\\[[;\\d]*m"), "") // Removes ANSI escape codes
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // Create JFrame
        val frame = JFrame("Intrucept Scan Results")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        // Create JTextArea
        val textArea = JTextArea()
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        textArea.isEditable = false // Make JTextArea non-editable

        // Create JScrollPane with JTextArea
        val scrollPane = JScrollPane(textArea)
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS

        // Set padding (20px) around the JScrollPane
        scrollPane.border = BorderFactory.createEmptyBorder(20, 20, 20, 20)

        // Add JScrollPane directly to the frame's content pane
        frame.contentPane.add(scrollPane)

        // Set frame to full screen
        frame.extendedState = JFrame.MAXIMIZED_BOTH

        // Make sure the frame is visible
        frame.isVisible = true

        // Register the text area (assuming this is a custom function you have)
        registerTextArea(textArea)
    }


}
