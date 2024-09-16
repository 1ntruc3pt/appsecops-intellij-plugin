package com.intrucept.appsecops.intellijplugin.actions

import com.intrucept.appsecops.intellijplugin.toolWindow.IntruceptScanManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.OutputStream
import com.intellij.openapi.diagnostic.Logger

class PerformSASTScanAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Ensure the IDE is fully initialized before performing the scan
        ApplicationManager.getApplication().invokeLater {
            if (ApplicationManager.getApplication().isDisposeInProgress) return@invokeLater

            // Proceed with the scan
            performSASTScan(project)
        }
    }

    private fun performSASTScan(project: Project) {
        // Define the project directory and zip file path
        val projectDir = File(project.basePath!!)
        val zipFile = File.createTempFile("project", ".zip")

        // Create a ZIP file
        zipProjectDirectory(projectDir, zipFile)

        IntruceptScanManager.notifySASTScanInitiated(project.name ?: "Unknown Project")

        // Perform SAST scan in a coroutine
        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking {
                try {
                    val response = sendToSASTAPI(zipFile, project)
                    ApplicationManager.getApplication().invokeLater {
                        IntruceptScanManager.updateSASTScanResults(response)
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        // Print the error to the terminal
                     //   logger.error("An error occurred: ${ex.message}")
                    }
                    ex.printStackTrace()
                } finally {
                    zipFile.delete() // Clean up the temp file
                }
            }
        }
    }

    private fun zipProjectDirectory(directory: File, zipFile: File) {
        ZipOutputStream(zipFile.outputStream()).use { zipOut ->
            directory.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = file.relativeTo(directory).path
                zipOut.putNextEntry(ZipEntry(entryName))
                file.inputStream().copyTo(zipOut)
                zipOut.closeEntry()
            }
        }
    }

    private suspend fun sendToSASTAPI(file: File, project: Project): String = withContext(Dispatchers.IO) {
        val config = readConfig(project)
        val url = URL("https://appsecops-api.intruceptlabs.com/api/v1/integrations/performSASTScan")
        val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"

        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "POST"
            setRequestProperty("Client-ID", config["CLIENT_ID"])
            setRequestProperty("Client-Secret", config["CLIENT_SECRET"])
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            doOutput = true

            outputStream.use { outputStream ->
                // Write the file part
                writeMultipartData(outputStream, boundary, file, config["APPLICATION_ID"] ?: "", "From Intellij Idea IDE", "java")

                // End of multipart form data
                outputStream.write("--$boundary--\r\n".toByteArray())
            }

            // Read and return the response
            return@withContext inputStream.bufferedReader().use { it.readText() }
        }
    }

    private fun readConfig(project: Project): Map<String, String> {
        val configFile = File(project.basePath, "intrucept-config.txt")
        if (!configFile.exists()) {
            throw IllegalStateException("Configuration file 'intrucept-config.txt' not found in project root.")
        }

        return configFile.readLines()
            .map { it.split("=", limit = 2) }
            .filter { it.size == 2 }
            .associate { (key, value) -> key.trim() to value.trim() }
    }


    private fun writeMultipartData(outputStream: OutputStream, boundary: String, file: File,
                                   applicationId: String,
                                   scanName: String,
                                   language: String) {
        outputStream.write("--$boundary\r\n".toByteArray())
        outputStream.write("Content-Disposition: form-data; name=\"projectZipFile\"; filename=\"${file.name}\"\r\n".toByteArray())
        outputStream.write("Content-Type: application/zip\r\n\r\n".toByteArray())
        file.inputStream().copyTo(outputStream)
        outputStream.write("\r\n".toByteArray())

        // Write the applicationId field
        outputStream.write("--$boundary\r\n".toByteArray())
        outputStream.write("Content-Disposition: form-data; name=\"applicationId\"\r\n\r\n".toByteArray())
        outputStream.write("$applicationId\r\n".toByteArray())

        // Write the scanName field
        outputStream.write("--$boundary\r\n".toByteArray())
        outputStream.write("Content-Disposition: form-data; name=\"scanName\"\r\n\r\n".toByteArray())
        outputStream.write("$scanName\r\n".toByteArray())

        // Write the language field
        outputStream.write("--$boundary\r\n".toByteArray())
        outputStream.write("Content-Disposition: form-data; name=\"language\"\r\n\r\n".toByteArray())
        outputStream.write("$language\r\n".toByteArray())

    }
}
