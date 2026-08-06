package script
import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.msglog.MessageLog
import com.sap.it.api.msglog.MessageLogFactory

class Logger {

    private final static List<String> LOG_LEVEL_INFO_ERROR = ['INFO', 'ERROR']
    private final static List<String> LOG_LEVEL_DEBUG_TRACE = ['DEBUG', 'TRACE']

    private static MessageLog log
    private static String logLevel
    private static int tracePointNumber = 0
    private static Message message

    static void init(MessageLogFactory messageLogFactory, Message iflowMessage) {
        Objects.requireNonNull(messageLogFactory, 'Logger init: messageLogFactory must not be null')
        Objects.requireNonNull(iflowMessage, 'Logger init: message must not be null')

        log = messageLogFactory.getMessageLog(iflowMessage)
        Objects.requireNonNull(log, 'Logger init: cannot get logger instance')

        message = iflowMessage

        // save the current log level
        logLevel = message.getProperty(Constants.Property.MESSAGE_LOG_LOG_LEVEL) as String
        Objects.requireNonNull(log, 'Logger init: cannot get logger level')

        tracePointNumber = 0
    }

    /**
     * log message details for diagnostic purposes
     * @param message the incoming message
     */
    static void logMessageDetails(Message message) {
        // get the JWT token payload
        Map<String, Object> payload = new JwtToken(message).getPayload()
        if (payload != null) {
            // get the custom claims root element
            Object azAttrObject = payload.get('az_attr')
            if (azAttrObject != null) {
                try {
                    // log each custom claim as custom header
                    azAttrObject.each { key, value -> custom(key, value) }
                } catch (DsiException e) {
                    // ignore further claim logging, but write error to log
                    debug('Token Claim Log Exception', e.getMessage())
                }
            }
        }
    }

    /**
     * adds a tracepoint property in the form 'TP #<number> <script position>' to the message
     * @param value a value to add to the tracepoint property
     */
    static void tracepoint(String value) {
        if (hasLogLevel(LOG_LEVEL_DEBUG_TRACE)) {
            tracePointNumber++
            String pos = ScriptUtils.getScriptPosition(2)
            String tpInfo = "TP #${tracePointNumber} ${pos}"
            stringProperty(tpInfo, value)
        }
    }

    /**
     * Convenience method for logging property values in debug and trace mode
     * @param propertyName
     */
    static void logProperty(String propertyName) {
        stringProperty(propertyName, message.getProperty(propertyName))
    }

/**
 * add a custom key/value pair to the message log of the current component
 * @param name
 * @param value
 */
    static void debug(String name, Object value) {
        stringProperty(name, value.toString())
    }

    static void attach(String name, String value) {
        addAttachment(name, value)
    }

// add attachment when in DEBUG or TRACE mode
    static void attachDebug(String name, String value) {
        if (hasLogLevel(LOG_LEVEL_DEBUG_TRACE)) {
            addAttachment(name, value)
        }
    }

// add attachment when in INFO mode
    static void attachInfo(String name, String value) {
        if (hasLogLevel(LOG_LEVEL_INFO_ERROR)) {
            addAttachment(name, value)
        }
    }

    static void customProperty(String propertyName) {
        addCustomHeader(propertyName, message.getProperty(propertyName))
    }

    static void custom(String name, String value) {
        addCustomHeader(name, value)
    }

    static void customInfo(String name, Object value) {
        if (hasLogLevel(LOG_LEVEL_INFO_ERROR)) {
            addCustomHeader(name, value)
        }
    }

    static void customDebug(String name, Object value) {
        if (hasLogLevel(LOG_LEVEL_DEBUG_TRACE)) {
            addCustomHeader(name, value)
        }
    }

    static boolean hasLogLevel(List<String> logLevelList) {
        getMessageLog()
        return logLevel in logLevelList
    }

    static MessageLog getMessageLog() {
        if (log == null) {
            throw new DsiException('Logger is not initialized')
        }
        logLevel = message.getProperty(Constants.Property.MESSAGE_LOG_LOG_LEVEL) as String
        return log
    }

    private static void stringProperty(String name, Object value) {
        if (value == null) {
            value = '<null>'
        }
        getMessageLog().setStringProperty(name, value.toString())
    }

    private static void addCustomHeader(String name, Object value) {
        if (value == null) {
            value = '<null>'
        }
        getMessageLog().addCustomHeaderProperty(name, value.toString())
    }

    private static void addAttachment(String name, Object value) {
        if (value == null) {
            value = '<null>'
        }
        getMessageLog().addAttachmentAsString(name, value, 'text/plain')
    }

}

