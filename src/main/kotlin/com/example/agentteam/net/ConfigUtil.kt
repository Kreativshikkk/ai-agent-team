package com.example.agentteam.net

import java.util.Properties
import java.io.FileInputStream
import java.io.InputStream

/**
 * Utility class for reading configuration properties.
 */
object ConfigUtil {
    private val properties = Properties()

    init {
        try {
            val inputStream: InputStream = javaClass.classLoader.getResourceAsStream("config.properties")
                ?: throw IllegalStateException("Could not find config.properties file")
            
            properties.load(inputStream)
            inputStream.close()
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load configuration: ${e.message}", e)
        }
    }

    /**
     * Gets the base path from the configuration.
     * @return The base path as a string.
     */
    fun getBasePath(): String {
        return properties.getProperty("base_path")
            ?: throw IllegalStateException("base_path property not found in configuration")
    }

    /**
     * Gets the Python scripts path from the configuration.
     * @return The Python scripts path as a string.
     */
    fun getPythonScriptsPath(): String {
        return "${getBasePath()}/src/main/python"
    }
}