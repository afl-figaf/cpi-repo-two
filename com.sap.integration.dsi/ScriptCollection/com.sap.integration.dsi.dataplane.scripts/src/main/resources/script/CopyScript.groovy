package script
import com.sap.gateway.ip.core.customdev.util.Message

Message configure(Message message) {
    Logger.init(messageLogFactory, message)

    try {
        Logger.logMessageDetails(message)
        Logger.custom('Step', "Configure")

        message.setProperty(Constants.Property.MESSAGE_LOG_CUSTOM_STATUS, Constants.Value.MESSAGE_LOG_CUSTOM_STATUS_FAILED)
        // parse the payload json and set message properties
        CopyConfiguration.configure(message)
        // init error flag and status
        message.setProperty(Constants.Property.ERROR_FLAG, false)
    } catch (BadRequestException ex) {
        // payload is invalid
        message.setProperty(Constants.Property.ERROR_FLAG, true)
        message.setProperty(Constants.Property.ERROR_CODE, 'PayloadInvalid')
        message.setProperty(Constants.Property.ERROR_MESSAGE, ex.getMessage())
        message.setProperty(Constants.Property.ERROR_HTTP_STATUS, 400)
        message.setProperty(Constants.Property.CAMEL_RESPONSE_CODE, 400)
    }

    return message
}

Message startCopy(Message message) {
    Logger.init(messageLogFactory, message)
    Logger.custom('Step', "Copy")
    return message
}

Message initFileCopy(Message message) {
    Logger.init(messageLogFactory, message)

    // init error flag and status
    message.setProperty(Constants.Property.ERROR_FLAG, false)
    message.setProperty(Constants.Property.MESSAGE_LOG_CUSTOM_STATUS, Constants.Value.MESSAGE_LOG_CUSTOM_STATUS_FAILED)

    // part number has to start at 0
    message.setProperty(Constants.Property.EXECUTION_CHUNK_PART_NUMBER, 0)
    message.setProperty(Constants.Property.EXECUTION_FILE_PART_SIZE, 0)
    message.setProperty(Constants.Property.EXECUTION_FILE_BYTES_READ, 0)
    // reset the payload
    message.setBody(null)

    return message
}

// store the messages payload size in property
Message saveChunkSize(Message message) {
    Logger.init(messageLogFactory, message)

    long bytesRead = message.getBodySize()
    message.setProperty(Constants.Property.EXECUTION_FILE_PART_SIZE, bytesRead)

    long copyFileBytesRead = message.getProperty(Constants.Property.EXECUTION_FILE_BYTES_READ) ?: 0 as Long
    message.setProperty(Constants.Property.EXECUTION_FILE_BYTES_READ, copyFileBytesRead + bytesRead)

    return message
}

/**
 * Convert a Camel Exception into error properties if the error flag is not set
 * @param message
 * @return message
 */
Message handleConfigException(Message message) {
    boolean errorFlagSet = ScriptUtils.getProperty(message, Constants.Property.ERROR_FLAG, false) as boolean

    // if error flag is set, we have handled the error and error properties have been set already
    if (!errorFlagSet) {
        Exception ex = message.getProperties().get(Constants.Property.CAMEL_EXCEPTION_CAUGHT)
        if (ex != null) {
            String errorCode = ex.getClass().getName()
            String errorMessage = ex.getMessage()

            message.setProperty(Constants.Property.ERROR_FLAG, true)
            message.setProperty(Constants.Property.ERROR_CODE, errorCode)
            message.setProperty(Constants.Property.ERROR_MESSAGE, errorMessage)
        }
    }

    return message
}

/**
 * decide on the error flag whether we should throw an error
 * @param message
 * @return message
 */
Message onErrorThrow(Message message) {
    boolean errorFlagSet = ScriptUtils.getProperty(message, Constants.Property.ERROR_FLAG, false) as boolean
    if (errorFlagSet) {
        String defaultMessage = '<No message available>'
        String errorMessage = ScriptUtils.getMandatoryProperty(message, Constants.Property.ERROR_MESSAGE, defaultMessage)
        throw new DsiException(errorMessage)
    }
    return message
}

/**
 * log the body of the message as the copy error result
 * call this script as the last step in an error case
 * @param message
 * @return message
 */
Message logBodyAsErrorResponse(Message message) {
    Logger.init(messageLogFactory, message)
    Logger.attach('Copy Error Response', message.getBody(String))
    String errorMessage = message.getProperty(Constants.Property.ERROR_MESSAGE)
    Logger.custom('Copy Error Message', errorMessage)

    return message
}

