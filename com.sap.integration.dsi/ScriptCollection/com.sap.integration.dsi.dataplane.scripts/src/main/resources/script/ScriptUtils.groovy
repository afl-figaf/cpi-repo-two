package script

import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.ITApiFactory
import com.sap.it.api.exception.InvalidContextException
import com.sap.it.api.securestore.SecureStoreService
import com.sap.it.api.securestore.UserCredential
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.security.MessageDigest

/**
 * This class contains generic static util functions
 **/

class ScriptUtils {

    // global mocks, used by unit tests
    private static SecureStoreService secureStoreServiceMock = null

    static void setupMocks(SecureStoreService secureStoreService) {
        Objects.requireNonNull(secureStoreService)
        secureStoreServiceMock = secureStoreService
    }

    static UserCredential getUserCredential(String credentialName) {
        SecureStoreService secureStoreService = secureStoreServiceMock
        try {
            secureStoreService = ITApiFactory.getService(SecureStoreService, null) ?: secureStoreServiceMock
        } catch (InvalidContextException ex) {
            // just log exception
            Logger.debug('SecureStoreService', ex.getMessage())
        }
        Objects.requireNonNull(secureStoreService, 'Cannot access the SecureStoreService')

        UserCredential credential = secureStoreService.getUserCredential(credentialName)
        if (credential == null) {
            throw new IllegalArgumentException("No credential with name '$credentialName' found")
        }
        return credential
    }

    /**
     * test that the given string has NO content
     * @param value
     * @return true if the given string is null or blank
     */
    static boolean isNullOrBlank(Object object) {
        if (object == null) {
            return true
        }

        String value = object
        return value == null || value.trim().isEmpty()
    }

    /**
     * test that the given string has some content
     * @param value
     * @return true if the given string is NOT null or blank
     */
    static boolean isNotNullOrBlank(Object object) {
        return !isNullOrBlank(object)
    }

    /**
     * clean up the given path with the specified or default delimiter
     * specify whether begin and end must contain the delimiter
     * @param path
     * @param delimAtStart
     * @param delimAtEnd
     * @param delimiter
     * @return the new path
     */
    static String cleanupPath(String path, boolean delimiterAtStart, boolean delimiterAtEnd, String delimiter = '/') {
        Objects.requireNonNull(path, 'Path must not be null')
        Objects.requireNonNull(delimiter, 'Delimiter must not be null')

        String startDelimiter = delimiterAtStart ? delimiter : ''
        String endDelimiter = delimiterAtEnd ? delimiter : ''
        String newPath = path.split(delimiter).findAll { item -> item != null && !item.trim().isEmpty()
        }.join(delimiter)
        String result = startDelimiter + newPath + endDelimiter

        // handling extra case where newPath is empty and both delimiters are enabled
        if (result == delimiter + delimiter) {
            return delimiter
        }

        return result
    }

    /**
     * clean up the given path with the specified or default delimiter
     * @param path
     * @param delimiter
     * @return the new path
     */
    static String cleanupPath(String path, String delimiter = '/') {
        return cleanupPath(path, true, false, delimiter)
    }

    /**
     * Get the value of a given message property or the default value
     * @param message
     * @param propertyName
     * @param defaultValue
     * @return property value
     */
    static Object getProperty(Message message, String propertyName, Object defaultValue) throws IllegalArgumentException {
        Objects.requireNonNull(message, 'message must not be null')
        Objects.requireNonNull(propertyName, 'propertyName must not be null')

        if (!message.getProperties().containsKey(propertyName)) {
            return defaultValue
        }

        String value = message.getProperties().get(propertyName)
        if (isNullOrBlank(value)) {
            if (isNotNullOrBlank(defaultValue)) {
                return defaultValue
            }
            throw new IllegalArgumentException("Missing value for property '" + propertyName + "'")
        }

        return message.getProperties().get(propertyName)
    }

    /**
     * Get the value of a given message property
     * @param message
     * @param propertyName
     * @param defaultValue
     * @return property value
     */
    static Object getMandatoryProperty(Message message, String propertyName, String defaultValue) throws IllegalArgumentException {
        if (!message.getProperties().containsKey(propertyName)) {
            throw new IllegalArgumentException("Missing property '" + propertyName + "'")
        }

        String value = message.getProperties().get(propertyName)
        if (isNullOrBlank(value)) {
            if (isNotNullOrBlank(defaultValue)) {
                return defaultValue
            }
            throw new IllegalArgumentException("Missing value for property '" + propertyName + "'")
        }

        return message.getProperties().get(propertyName)
    }

    /**
     * Get the value of a given message property
     * @param message
     * @param propertyName
     * @return property value
     */
    static Object getMandatoryProperty(Message message, String propertyName) throws IllegalArgumentException {
        return getMandatoryProperty(message, propertyName, null)
    }

    /**
     * get the sha-256 hash result for the given value*/
    static String sha256Hash(String value) {
        return MessageDigest.getInstance('SHA-256').digest(value.getBytes('UTF-8')).encodeHex().toString()
    }

    /**
     * get a value from a parsed json doc, specifying path and an optional list of allowed values
     * @param root
     * @param path
     * @param allowedValues
     * @return
     */
    static Object getMandatoryValueByPath(Object root, String path, List<Object> allowedValues = []) {
        Object value = getNestedProperty(root, path)

        if (allowedValues != null && allowedValues.size() > 0) {
            if (value in allowedValues) {
                return value
            }

            throw new BadRequestException("Invalid value for property '${path}'. Allowed values: ${allowedValues.join(',')}")
        }

        if (isNullOrBlank(value)) {
            throw new BadRequestException("Missing value for property '${path}'")
        }

        return value
    }

    static Object getValueByPathOrDefault(Object root, String path, Object defaultValue) {
        assert (root != null)
        assert (isNotNullOrBlank(path))

        try {
            Object value = getNestedProperty(root, path)

            if (value == null) {
                return defaultValue
            }

            return value
        } catch (BadRequestException ex) {
            return defaultValue
        }
    }

    /**
     * trim value at begin of string
     * @param stringToTrim
     * @param trimValue
     * @return
     */
    static String trimAtStart(String stringToTrim, String trimValue) {
        if (stringToTrim == null || trimValue == null || trimValue.isEmpty()) {
            return stringToTrim
        }

        String trimmed = stringToTrim
        while (trimmed.startsWith(trimValue)) {
            trimmed = trimmed.substring(trimValue.size())
        }

        return trimmed
    }

    static String getFileName(String path, String delimiter = '/') {
        if (path == null) {
            return null
        }
        Collection parts = path.split(delimiter).findAll { item -> item != null && !item.trim().isEmpty() }
        if (parts.size() > 0) {
            return parts.last()
        }
        return ''
    }

    static String getFolderPath(String path, String delimiter = '/') {
        if (path == null) {
            return null
        }
        Collection<String> parts = path.split(delimiter).findAll { item -> item != null && !item.trim().isEmpty() }
        if (parts.size() > 0) {
            parts.remove(parts.size() - 1)
        }
        return parts.join(delimiter)
    }

    /**
     * Return the current Script name and line number in the form "<Script>:<Linenumber>"
     * @return script position
     */
    static String getScriptPosition(int offset) {
        StackTraceElement element = myFunkyAndVeryUniqueMethodName(offset)
        return formatScriptPosition(element)
    }

    /**
     * Return the current Script name and line number in the form "<Script>:<Linenumber>"
     * @return script position
     */
    static String getScriptPosition() {
        return getScriptPosition(2)
    }

    static int getLineNumber() {
        StackTraceElement element = myFunkyAndVeryUniqueMethodName(1)
        return element.getLineNumber()
    }

    static String prettyPrintJson(String json) {
        try {
            if (isNullOrBlank(json)) {
                return json
            }

            // Parse the JSON payload.
            def jsonObject = new JsonSlurper().parseText(json)
            // Convert back to a string.
            def jsonPretty = new JsonBuilder(jsonObject).toPrettyString()
            return jsonPretty
        } catch (Exception e) {
            return json
        }
    }

    static String prettyPrintJsonFlat(String json) {
        try {
            if (isNullOrBlank(json)) {
                return json
            }

            // Parse the JSON payload.
            def jsonObject = new JsonSlurper().parseText(json)
            // Convert back to a string.
            def jsonPretty = JsonOutput.toJson(jsonObject)
            return jsonPretty
        } catch (Exception e) {
            return json
        }
    }

    static void validateNotNullOrBlank(String propertyName, String valueToTest) {
        if (isNullOrBlank(valueToTest)) {
            throw new BadRequestException(propertyName + ' must not be null or blank')
        }
    }

    static void validateUrl(String propertyName, String valueToTest) {
        if (isNullOrBlank(valueToTest)) {
            throw new BadRequestException(propertyName + ': URL must not be null or blank')
        }

        try {
            URI uri = new URI(valueToTest)
            // check for full URL
            URL url = uri.toURL()
            // validate protocol
            if (!url.protocol.equalsIgnoreCase('http') && !url.protocol.equalsIgnoreCase('https')) {
                throw new BadRequestException(propertyName + ': Only HTTP or HTTPS URLs are allowed')
            }
            // validate host name
            if (isNullOrBlank(url.getHost())) {
                throw new BadRequestException(propertyName + ': The host name is missing')
            }
        } catch (URISyntaxException | MalformedURLException ex) {
            throw new BadRequestException(propertyName + ': ' + ex.getMessage())
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(propertyName + ': The URL is not valid')
        }
    }

    private static String formatScriptPosition(StackTraceElement element) {
        String fileName = element.getFileName()
        String methodName = element.getMethodName()
        String lineNumber = element.getLineNumber()

        // remove extension from file name
        List<String> partList = fileName.split('\\.') as List
        // remove last element
        partList.remove(partList.size() - 1)
        // create filename without extension
        String fileNameWithoutExtension = partList.join('\\.')

        return "${fileNameWithoutExtension}:${methodName}:${lineNumber}"
    }

    private static Object getNestedProperty(Object root, String path) {
        assert (root != null)
        assert (isNotNullOrBlank(path))

        Object result = root
        path.split(/\./).each { item ->
            if (result == null) {
                throw new BadRequestException("Missing property '$path'")
            }
            result = result."${item}"
        }

        return result
    }

    private static Object getNestedProperty(Node root, String path) {
        assert (root != null)
        assert (isNotNullOrBlank(path))

        Object rootNode = root
        path.split(/\./).each { item ->
            if (rootNode == null) {
                throw new BadRequestException("Missing property '$path'")
            }
            rootNode = rootNode."${item}"
        }

        if (rootNode.getClass() == NodeList) {
            if (rootNode.size() == 0) {
                throw new BadRequestException("Missing property '$path'")
            }
        }

        return rootNode
    }


    /**
     * Utility method used by getScriptPosition()
     * Walk the Stack until this method is found and then return the script position N stept higher
     * @return script position
     */
    private static StackTraceElement myFunkyAndVeryUniqueMethodName(int offset) {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace()
        boolean foundMe = false

        for (StackTraceElement element : elements) {
            // skip the groovy internal entries
            if (element.getClassName().startsWith('org.codehaus.groovy.runtime')) {
                continue
            }
            // skip entries that are not related to a script
            if (element.getFileName() == null) {
                continue
            }

            if (foundMe && (offset == 0)) {
                return element
            } else if (foundMe) {
                offset--
            }

            // check if we reached ourself in the stack
            if (element.getMethodName() == 'myFunkyAndVeryUniqueMethodName') {
                foundMe = true
            }
        }
        return ''
    }
}
