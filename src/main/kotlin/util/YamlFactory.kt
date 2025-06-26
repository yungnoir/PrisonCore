package twizzy.tech.util

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A utility class for managing YAML configuration files.
 */
class YamlFactory {
    companion object {
        private val yaml: Yaml by lazy {
            val options = DumperOptions().apply {
                defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                isPrettyFlow = true
                indent = 2
            }
            Yaml(options)
        }

        /**
         * Creates a YAML config file from the given template if it doesn't exist.
         * @param path The path where the config file should be created.
         * @param templatePath The path to the template resource file.
         * @return The config file.
         */
        fun createConfigIfNotExists(path: String, templatePath: String? = null): File {
            val file = File(path)
            if (!file.exists()) {
                file.parentFile?.mkdirs()

                if (templatePath != null) {
                    // Copy from template resource if provided
                    val inputStream = YamlFactory::class.java.classLoader.getResourceAsStream(templatePath)
                    if (inputStream != null) {
                        Files.copy(inputStream, file.toPath())
                    } else {
                        file.createNewFile()
                    }
                } else {
                    file.createNewFile()
                }
            }
            return file
        }

        /**
         * Loads a YAML configuration from a file.
         * @param file The file to load from.
         * @return The loaded configuration as a Map.
         */
        @Suppress("UNCHECKED_CAST")
        fun loadConfig(file: File): Map<String, Any> {
            return FileReader(file).use { reader ->
                yaml.load(reader) as? Map<String, Any> ?: emptyMap()
            }
        }

        /**
         * Loads a YAML configuration from a path.
         * @param path The path to load from.
         * @return The loaded configuration as a Map.
         */
        fun loadConfig(path: String): Map<String, Any> {
            return loadConfig(File(path))
        }

        /**
         * Saves a configuration to a YAML file.
         * @param data The configuration data to save.
         * @param file The file to save to.
         */
        fun saveConfig(data: Any, file: File) {
            FileWriter(file).use { writer ->
                yaml.dump(data, writer)
            }
        }

        /**
         * Saves a configuration to a YAML file at the specified path.
         * @param data The configuration data to save.
         * @param path The path to save to.
         */
        fun saveConfig(data: Any, path: String) {
            saveConfig(data, File(path))
        }

        /**
         * Gets a value from the configuration by its path.
         * @param config The configuration to get the value from.
         * @param path The path to the value (dot notation, e.g., "server.port").
         * @param defaultValue The default value to return if the path is not found.
         * @return The value at the path or the default value.
         */
        @Suppress("UNCHECKED_CAST")
        fun <T> getValue(config: Map<String, Any>, path: String, defaultValue: T): T {
            val parts = path.split(".")
            var current: Any? = config

            for (part in parts) {
                if (current is Map<*, *>) {
                    current = (current as Map<String, Any>)[part]
                    if (current == null) {
                        return defaultValue
                    }
                } else {
                    return defaultValue
                }
            }

            return try {
                current as T
            } catch (e: ClassCastException) {
                defaultValue
            }
        }

        /**
         * Sets a value in the configuration at the specified path.
         * @param config The mutable configuration to modify.
         * @param path The path where to set the value (dot notation).
         * @param value The value to set.
         * @return True if the value was set successfully, false otherwise.
         */
        @Suppress("UNCHECKED_CAST")
        fun setValue(config: MutableMap<String, Any>, path: String, value: Any): Boolean {
            val parts = path.split(".")

            if (parts.size == 1) {
                config[parts[0]] = value
                return true
            }

            var current = config
            for (i in 0 until parts.size - 1) {
                val part = parts[i]
                var next = current[part] as? MutableMap<String, Any>

                if (next == null) {
                    next = mutableMapOf()
                    current[part] = next
                }

                current = next
            }

            current[parts.last()] = value
            return true
        }

        /**
         * Updates a configuration file with new values.
         * @param filePath The path to the configuration file.
         * @param updates A map of paths to their new values.
         * @return True if updates were successful, false otherwise.
         */
        fun updateConfigFile(filePath: String, updates: Map<String, Any>): Boolean {
            val file = File(filePath)
            if (!file.exists()) {
                return false
            }

            val config = loadConfig(file).toMutableMap()

            for ((path, value) in updates) {
                setValue(config, path, value)
            }

            saveConfig(config, file)
            return true
        }
    }
}