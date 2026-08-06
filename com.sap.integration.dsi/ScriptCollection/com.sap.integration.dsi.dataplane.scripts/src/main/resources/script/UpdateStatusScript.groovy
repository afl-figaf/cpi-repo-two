package script

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper

// create the status json payload to be sent to jms
Message prepareStatusMessage(Message message) {
    Logger.init(messageLogFactory, message)
    Logger.custom('Step', 'Update Status')

    final String FORMAT_URL_COMPLETE = '%s/transferprocess/%s/complete'
    final String FORMAT_URL_FAIL = '%s/transferprocess/%s/fail'

    // we can assume all properties are mandatory and have been validated during config phase
    String callbackUrl = ScriptUtils.getMandatoryProperty(message, Constants.Property.CALLBACK_URL)
    String transferProcessId = ScriptUtils.getMandatoryProperty(message, Constants.Property.TRANSFER_PROCESS_ID)
    String credentialAlias = ScriptUtils.getMandatoryProperty(message, Constants.Property.DYNAMIC_CALLBACK_CREDENTIAL_ALIAS)

    String endpoint = String.format(FORMAT_URL_COMPLETE, callbackUrl, transferProcessId)
    String errorMessage = ''

    boolean hasError = ScriptUtils.getMandatoryProperty(message, Constants.Property.ERROR_FLAG)
    if (hasError) {
        endpoint = String.format(FORMAT_URL_FAIL, callbackUrl, transferProcessId)
        errorMessage = message.getProperty(Constants.Property.ERROR_MESSAGE)
    }

    String messageBody = """
    {
        "callbackUrl": "${endpoint}",
        "credentialAlias": "${credentialAlias}",
        "errorMessage": "${errorMessage}"
    }
    """

    Logger.customInfo('Status Message Body', messageBody)
    message.setBody(messageBody)

    return message
}

// prepare the http call from the jms status message
Message prepareStatusCallback(Message message) {
    Logger.init(messageLogFactory, message)

    try {
        String payload = message.getBody(String)
        Logger.custom("Request Payload", payload)

        // Parse the JSON payload.
        def jsonObject = new JsonSlurper().parseText(payload)

        String callbackUrl = jsonObject?.callbackUrl
        ScriptUtils.validateNotNullOrBlank("callbackUrl", callbackUrl)

        String credentialAlias = jsonObject?.credentialAlias
        ScriptUtils.validateNotNullOrBlank("credentialAlias", credentialAlias)

        String errorMessage = jsonObject?.errorMessage
        String errorMessageJson = String.format('{"errorMessage": "%s"}', errorMessage)

        // log properties for debugging
        Logger.custom('callbackUrl', callbackUrl)
        Logger.custom('credentialAlias', credentialAlias)
        Logger.custom('errorMessageJson', errorMessageJson)

        // set http headers
        message.setHeaders([
                'Content-Type': 'application/json'
        ])

        // set http properties
        message.setProperty(Constants.Property.DYNAMIC_CALLBACK_URL, callbackUrl)
        message.setProperty(Constants.Property.DYNAMIC_CALLBACK_CREDENTIAL_ALIAS, credentialAlias)
        message.setBody(null)
        // only send error error message in error condition
        if (callbackUrl.toLowerCase().endsWith('/fail')) {
            message.setBody(errorMessageJson)
        }

        return message
    }
    catch (Exception e) {
        throw new DsiException('Error preparing status callback: ' + e.getMessage())
    }
}

String getErrorMessageFromJson(String json) {
    try {
        if (ScriptUtils.isNullOrBlank(json)) {
            return json
        }
        // Parse the JSON payload.
        def jsonObject = new JsonSlurper().parseText(json)
        def result = jsonObject?.errorMessage

        if (ScriptUtils.isNullOrBlank(result)) {
            throw new BadRequestException('Error message is missing in payload')
        }

        return result
    } catch (Exception e) {
        throw new BadRequestException('Error parsing payload: ' + e.getMessage())
    }
}

// configure the test callback properties - used by the TestS3Callback iflow
Message configureTestCallbackProperties(Message message) {
    Logger.init(messageLogFactory, message)
    Logger.custom('Step', 'Configure Test Callback')

    String path = message.getHeader('CamelHttpPath', String)
    Logger.custom('Path', path)
    def parts = path.split('/')
    if (parts.length == 0) {
        throw new BadRequestException('Invalid path: ' + path)
    }

    String method = message.getHeader('CamelHttpMethod', String)
    Logger.custom('Method', method)

    String transferProcessId = parts[0]
    String status = ''
    String varName = 'DSI_' + transferProcessId

    if (method == "POST") {
        if (parts.length != 2) {
            throw new BadRequestException('Invalid path: ' + path)
        }
        status = parts[1]
        Logger.custom('Status', status)

        String body = message.getBody(String)
        Logger.custom('Message Body', body)

        String errorMessage = ''
        if (status == 'fail') {
            errorMessage = getErrorMessageFromJson(body)
        }

        String varValue = """
        {
            "status": "${status}",
            "errorMessage": "${errorMessage}"
        }
        """

        varValue = varValue.replace("\n", "").replace("\r", "")

        message.setProperty('varValue', varValue)
        message.setBody(null)

        Logger.custom('status', status)
        Logger.custom('errorMessage', errorMessage)
        Logger.custom('varValue', varValue)
    } else {
        if (parts.length != 1) {
            throw new BadRequestException("Invalid path: " + path)
        }
    }

    message.setProperty('varName', varName)

    // just for debugging purposes
    Logger.custom('transferProcessId', transferProcessId)
    Logger.custom('varName', varName)

    return message
}

Message beautifyMessageBodyJson(Message message) {
    Logger.init(messageLogFactory, message)
    Logger.custom('Step', 'Message Beautify')

    String body = message.getBody(String)
    String json = ScriptUtils.prettyPrintJson(body)
    message.setBody(json)

    return message
}