package com.example.agentteam.net

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import com.example.agentteam.net.ConfigUtil

/**
 * Generates JSON file with crew configuration based on the TeamConfig.
 */
class PythonCrewGenerator(private val project: Project) {

    /**
     * Generates a JSON file with crew configuration based on the given TeamConfig.
     * @param config The TeamConfig containing the team configuration.
     * @return The generated JSON file.
     */
    fun generateJsonFile(config: TeamConfig): VirtualFile {
        val jsonContent = generateJsonContent(config)
        val file = createJsonFile(jsonContent)
        openFileInEditor(file)
        return file
    }

    /**
     * Generates JSON content with crew configuration based on the given TeamConfig.
     * @param config The TeamConfig containing the team configuration.
     * @return The generated JSON content as a string.
     */
    private fun generateJsonContent(config: TeamConfig): String {
        val sb = StringBuilder()

        sb.append("[\n")

        var agentCount = 0

        // Team Leads
        for (i in 1..config.teamLeads) {
            if (agentCount > 0) sb.append(",\n")
            val rolePrompt = config.rolePrompts["teamLead"] ?: "You are a Team Lead responsible for coordinating the team and ensuring the project is completed successfully."
            sb.append("""  {
    "role": "Team Lead",
    "goal": "Coordinate the team and ensure the project is completed successfully",
    "backstory": "${rolePrompt.replace("\"", "\\\"")}",
    "number": ${agentCount + 1}
  }""")
            agentCount++
        }

        // Tech Leads
        for (i in 1..config.techLeads) {
            if (agentCount > 0) sb.append(",\n")
            val rolePrompt = config.rolePrompts["techLead"] ?: "You are a Tech Lead responsible for making technical decisions and guiding the development team."
            sb.append("""  {
    "role": "Tech Lead",
    "goal": "Make technical decisions and guide the development team",
    "backstory": "${rolePrompt.replace("\"", "\\\"")}",
    "number": ${agentCount + 1}
  }""")
            agentCount++
        }

        // Engineers
        for (i in 1..config.engineers) {
            if (agentCount > 0) sb.append(",\n")
            val rolePrompt = config.rolePrompts["engineer"] ?: "You are a Software Engineer responsible for implementing the technical solutions."
            sb.append("""  {
    "role": "Software Engineer",
    "goal": "Implement technical solutions",
    "backstory": "${rolePrompt.replace("\"", "\\\"")}",
    "number": ${agentCount + 1}
  }""")
            agentCount++
        }

        // QA Engineers
        for (i in 1..config.qaEngineers) {
            if (agentCount > 0) sb.append(",\n")
            val rolePrompt = config.rolePrompts["qaEngineer"] ?: "You are a QA Engineer responsible for testing and ensuring the quality of the software."
            sb.append("""  {
    "role": "QA Engineer",
    "goal": "Test and ensure the quality of the software",
    "backstory": "${rolePrompt.replace("\"", "\\\"")}",
    "number": ${agentCount + 1}
  }""")
            agentCount++
        }

        sb.append("\n]")

        return sb.toString()
    }

    /**
     * Creates a JSON file with the given content.
     * @param content The content of the JSON file.
     * @return The created VirtualFile.
     */
    private fun createJsonFile(content: String): VirtualFile {
        val pythonScriptsPath = ConfigUtil.getPythonScriptsPath()
        val pluginDir = File(pythonScriptsPath)
        if (!pluginDir.exists()) {
            pluginDir.mkdirs()
        }

        // Use a fixed filename
        val fileName = "crew.json"
        val file = File(pluginDir, fileName)

        // Delete the file if it already exists
        if (file.exists()) {
            file.delete()
        }

        file.writeText(content)

        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
            ?: throw IllegalStateException("Failed to create JSON file")
    }

    /**
     * Opens the given file in the editor.
     * @param file The file to open.
     */
    private fun openFileInEditor(file: VirtualFile) {
        FileEditorManager.getInstance(project).openFile(file, true)
    }
}
