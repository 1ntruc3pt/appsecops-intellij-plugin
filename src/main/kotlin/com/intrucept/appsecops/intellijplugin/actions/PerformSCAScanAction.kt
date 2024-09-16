package com.intrucept.appsecops.intellijplugin.actions

import com.intrucept.appsecops.intellijplugin.toolWindow.IntruceptScanManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PerformSCAScanAction : AnAction() {
    private val logger = Logger.getInstance(PerformSCAScanAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ApplicationManager.getApplication().invokeLater {
            if (ApplicationManager.getApplication().isDisposeInProgress) return@invokeLater
            performSCAScan(project)
        }
    }

    private fun performSCAScan(project: Project) {
        val projectDir = File(project.basePath ?: return)
        val zipFile = createTempZipFile()

        zipProjectDirectory(projectDir, zipFile)

        IntruceptScanManager.notifySCAScanInitiated(project.name ?: "Unknown Project")

        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking {
                try {
                    val response = sendToSCAAPI(zipFile, project)
                    ApplicationManager.getApplication().invokeLater {
                        IntruceptScanManager.updateSCAScanResults(response)
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        logger.error("An error occurred during SCA scan: ${ex.message}", ex)
                    }
                } finally {
                    zipFile.delete()
                }
            }
        }
    }

    private fun createTempZipFile(): File = File.createTempFile("project", ".zip")

    private fun zipProjectDirectory(directory: File, zipFile: File) {
        ZipOutputStream(zipFile.outputStream().buffered()).use { zipOut ->
            directory.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val entryName = "project/${file.relativeTo(directory).path}"
                    zipOut.putNextEntry(ZipEntry(entryName))
                    file.inputStream().buffered().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
        }
    }

    private suspend fun sendToSCAAPI(file: File, project: Project): String = withContext(Dispatchers.IO) {
        val config = readConfig(project)
        val url = URL("https://appsecops-api.intruceptlabs.com/api/v1/integrations/performSCAScan")
        val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"

        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "POST"
            setRequestProperty("Client-ID", config["CLIENT_ID"])
            setRequestProperty("Client-Secret", config["CLIENT_SECRET"])
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            doOutput = true

            outputStream.buffered().use { outputStream ->
                writeMultipartData(outputStream, boundary, file, config["APPLICATION_ID"] ?: "", "From IntelliJ IDEA IDE", "java")
                outputStream.write("--$boundary--\r\n".toByteArray())
            }

            return@withContext inputStream.bufferedReader().use { it.readText() }
        }
    }

    private fun readConfig(project: Project): Map<String, String> {
        val configFile = File(project.basePath, "intrucept-config.txt")
        if (!configFile.exists()) {
            throw IllegalStateException("Configuration file 'intrucept-config.txt' not found in project root.")
        }

        return configFile.useLines { lines ->
            lines.map { it.split("=", limit = 2) }
                .filter { it.size == 2 }
                .associate { (key, value) -> key.trim() to value.trim() }
        }
    }

    private fun writeMultipartData(
        outputStream: java.io.OutputStream,
        boundary: String,
        file: File,
        applicationId: String,
        scanName: String,
        language: String
    ) {
        fun writeFormField(name: String, value: String) {
            outputStream.write("--$boundary\r\n".toByteArray())
            outputStream.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
            outputStream.write("$value\r\n".toByteArray())
        }

        outputStream.write("--$boundary\r\n".toByteArray())
        outputStream.write("Content-Disposition: form-data; name=\"projectZipFile\"; filename=\"${file.name}\"\r\n".toByteArray())
        outputStream.write("Content-Type: application/zip\r\n\r\n".toByteArray())
        file.inputStream().buffered().use { it.copyTo(outputStream) }
        outputStream.write("\r\n".toByteArray())

        writeFormField("applicationId", applicationId)
        writeFormField("scanName", scanName)
        writeFormField("language", language)
    }
}