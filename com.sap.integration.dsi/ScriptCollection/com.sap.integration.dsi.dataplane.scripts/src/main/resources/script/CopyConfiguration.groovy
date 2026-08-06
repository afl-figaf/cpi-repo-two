package script

import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.securestore.UserCredential
import groovy.json.JsonException
import groovy.json.JsonSlurper

class CopyConfiguration {

    final static String MSG_ERROR_MISSING_PAYLOAD = 'The request payload is empty'
    final static String MSG_ERROR_INVALID_PAYLOAD = 'The payload does not contain a valid json structure. '
    final static String PATH_DELIMITER = '/'
    final static String TYPE_AWS = 'AmazonS3'

    static void configure(Message message) {
        // log the request payload
        Logger.attach("Request Payload", message.getBody(String))

        Object config = parsePayload(message)
        List<String> features = []

        // feature flags be can used for dynamically enabling/disabling features
        Closure saveFeatureFlagList = { List<String> featureFlags ->
            features = featureFlags
            return featureFlags
        }
        configureOptionalProperty(message, config, 'options.featureFlags', 'FeatureFlags', [], saveFeatureFlagList)

        // set up provider properties
        ScriptUtils.getMandatoryValueByPath(config, 'provider.type', [TYPE_AWS])
        configureProperty(message, config, 'provider.type', 'SourceType')

        configureProperty(message, config, 'provider.bucketRegion', 'SourceRegion')
        configureProperty(message, config, 'provider.bucketName', 'SourceBucket')
        configureProperty(message, config, 'provider.folderName', Constants.Property.SOURCE_FOLDER,
                getFolderNameAdjuster())
        configureOptionalProperty(message, config, 'provider.fileName', Constants.Property.SOURCE_FILE, null,
                getFileNameAdjuster())

        configureOptionalProperty(message, config, "provider.sessionToken", Constants.Property.SOURCE_SESSION_TOKEN, null)

        // determine copy mode
        String copyMode = Constants.Value.COPY_MODE_FILE
        if (message.getProperty(Constants.Property.SOURCE_FILE) == '*') {
            copyMode = Constants.Value.COPY_MODE_FOLDER
        }
        message.setProperty(Constants.Property.COPY_MODE, copyMode)

        // set up consumer properties
        ScriptUtils.getMandatoryValueByPath(config, 'consumer.type', [TYPE_AWS])
        configureProperty(message, config, 'consumer.type', 'TargetType')
        configureProperty(message, config, 'consumer.bucketRegion', 'TargetRegion')
        configureProperty(message, config, 'consumer.bucketName', 'TargetBucket')
        configureProperty(message, config, 'consumer.folderName', Constants.Property.TARGET_FOLDER)
        if (copyMode == Constants.Value.COPY_MODE_FILE) {
            // use target filename or if missing the source file name
            configureOptionalProperty(message, config, 'consumer.fileName', Constants.Property.TARGET_FILE, message.getProperty(Constants.Property.SOURCE_FILE))
        } else {
            configureOptionalProperty(message, config, 'consumer.fileName', Constants.Property.TARGET_FILE, "")
        }

        configureOptionalProperty(message, config, "consumer.sessionToken", Constants.Property.TARGET_SESSION_TOKEN, null)

        // configure the credentials as last step
        configureCredentials(message, config, 'provider', Constants.Property.SOURCE_CREDENTIAL)
        configureCredentials(message, config, 'consumer', Constants.Property.TARGET_CREDENTIAL)

        // setup callback properties
        configureOptionalProperty(message, config, "callbackUrl", Constants.Property.CALLBACK_URL, null)
        String callbackUrl = message.getProperty(Constants.Property.CALLBACK_URL)

        if (callbackUrl != null) {
            message.setProperty(Constants.Property.EXEC_MODE, "ASYNC")
            // make sure it looks like a valid URL
            ScriptUtils.validateUrl("callbackUrl", callbackUrl)

            // when we have a callback, we also need the Transfer Process Id
            String processId = new JwtToken(message).getCustomAttribute('transferProcessId')
            if (processId == null && config.transferProcessId) {
                // for testing purposes allow  Transfer Process Id passed as optional parameter in the paylaod
                processId = config.transferProcessId
            }

            if (ScriptUtils.isNullOrBlank(processId)) {
                throw new BadRequestException('Missing transferProcessId in JWT token')
            }
            message.setProperty(Constants.Property.TRANSFER_PROCESS_ID, processId)

            // set the optional credential alias or use the default
            configureOptionalProperty(message, config, "callbackCredentialAlias", Constants.Property.DYNAMIC_CALLBACK_CREDENTIAL_ALIAS, 'SAP_DataspaceIntegrationCallback')

            // verify that credential alias does exist
            String credentialName = message.getProperty(Constants.Property.DYNAMIC_CALLBACK_CREDENTIAL_ALIAS)
            try {
                UserCredential credential = ScriptUtils.getUserCredential(credentialName)
                if (credential == null) {
                    throw new BadRequestException('Credential alias ' + credentialName + ' does not exist')
                }
            } catch (Exception ex) {
                throw new BadRequestException('Credential alias ' + credentialName + ' does not exist')
            }
        }

        // init properties
        message.setProperty(Constants.Property.FOLDER_COPY_CONTINUE_FLAG, false)

        // conf optional property chunk size
        Long chunkSize = ScriptUtils.getValueByPathOrDefault(config, 'options.chunkSize', 100 * 1024 * 1024) as Long
        message.setProperty(Constants.Property.FILE_CHUNK_SIZE, chunkSize)

        // conf optional INTERNAL property test endpoint
        configureOptionalProperty(message, config, 'options.testEndpoint', Constants.Property.TEST_ENDPOINT, null)
        if (message.getProperty(Constants.Property.TEST_ENDPOINT) != null) {
            // save authorization
            message.setProperty(Constants.Property.TEST_AUTHORIZATION, message.getHeader(Constants.Header.HTTP_AUTHORIZATION, String))
        }

        // log properties
        if (features.size() > 0) {
            Logger.logProperty('FeatureFlags')
        }
    }

    private static Closure getFolderNameAdjuster() {
        return { folderName ->
            if (ScriptUtils.isNullOrBlank(folderName)) {
                throw new BadRequestException("Missing value for property 'provider.folderName'")
            }

            String newPath = PATH_DELIMITER + folderName
                    .split(PATH_DELIMITER)
                    .findAll { item -> item != null && !item.trim().isEmpty() }
                    .join(PATH_DELIMITER)

            if (newPath != '/') {
                newPath += '/'
            }
            return newPath
        }
    }

    private static Closure getFileNameAdjuster() {
        return { fileName ->
            if (ScriptUtils.isNotNullOrBlank(fileName)) {
                return fileName.split(PATH_DELIMITER).join(PATH_DELIMITER)
            }
            return '*' // folder mode
        }
    }

    // parse the payload and return it as instantiated object
    /**
     * Parse the paylaod into groovy object tree
     * @param message
     * @return object tree
     */
    private static Object parsePayload(Message message) {
        if (message.getBodySize() == 0) {
            throw new BadRequestException(MSG_ERROR_MISSING_PAYLOAD)
        }

        try {
            String body = message.getBody(String)
            JsonSlurper slurper = new JsonSlurper()
            return slurper.parseText(body)
        } catch (IllegalArgumentException | JsonException ex) {
            throw new BadRequestException(MSG_ERROR_INVALID_PAYLOAD + ex.getMessage())
        }
    }

    /**
     * Copy a validated config value into a message property
     * @param message
     * @param root
     * @param path
     * @param propertyName
     * @param allowedValues string list of allowed values
     */
    private static void configureProperty(Message message, Object config, String path, String propertyName, List<String> allowedValues = []) {
        Object value = ScriptUtils.getMandatoryValueByPath(config, path, allowedValues)
        message.setProperty(propertyName, value)
    }

    private static void configureProperty(Message message, Object config, String path, String propertyName, List<String> allowedValues = [], Closure closure) {
        Object value = ScriptUtils.getMandatoryValueByPath(config, path, allowedValues)
        // allow adjustment of value
        value = closure.call(value)
        message.setProperty(propertyName, value)
    }

    private static void configureOptionalProperty(Message message, Object config, String path, String propertyName, Object defaultValue) {
        Object value = ScriptUtils.getValueByPathOrDefault(config, path, defaultValue)
        message.setProperty(propertyName, value)
    }

    private static void configureOptionalProperty(Message message, Object config, String path, String propertyName, Object defaultValue, Closure closure) {
        Object value = ScriptUtils.getValueByPathOrDefault(config, path, defaultValue)
        // allow adjustment of value
        value = closure.call(value)
        message.setProperty(propertyName, value)
    }

    private static void configureCredentials(Message message, Object config, String root, String credentialsPropertyName) {
        String accessKey
        String secretKey

        String credentialAlias = ScriptUtils.getValueByPathOrDefault(config, root + '.credentialAlias', null)

        if (ScriptUtils.isNotNullOrBlank(credentialAlias)) {
            // get credential
            UserCredential credential = ScriptUtils.getUserCredential(credentialAlias)
            accessKey = credential.getUsername()
            secretKey = credential.getPassword() as String // cast is important!
        } else {
            accessKey = ScriptUtils.getMandatoryValueByPath(config, root + '.accessKeyId')
            secretKey = ScriptUtils.getMandatoryValueByPath(config, root + '.secretKeyId')
        }

        Map<String, String> credentials = [accessKey: accessKey, secretKey: secretKey]
        message.setProperty(credentialsPropertyName, credentials)

        Logger.debug(Constants.DisplayName.Aws.ACCESS_KEY + ' ' + root.capitalize(), accessKey)
    }

}
