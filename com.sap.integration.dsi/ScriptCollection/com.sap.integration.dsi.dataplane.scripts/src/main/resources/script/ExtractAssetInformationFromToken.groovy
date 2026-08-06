package script

/* Refer the link below to learn more about the use cases of script.
https://help.sap.com/viewer/368c481cd6954bdfa5d0435479fd4eaf/Cloud/en-US/148851bf8192412cba1f9d2c17f4bd25.html

If you want to know more about the SCRIPT APIs, refer the link below
https://help.sap.com/doc/a56f52e1a58e4e2bac7f7adbf45b2e26/Cloud/en-US/index.html */

import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.msglog.MessageLog
import groovy.json.JsonException
import groovy.json.JsonSlurper

Message processMessage(Message message) {
    Logger.init(messageLogFactory, message)
    Logger.logMessageDetails(message)

    final String DEFAULT_CUSTOM_STATUS = "Successful"
    final String EDC_NAMESPACE = "https://w3id.org/edc/v0.0.1/ns/"
    final String HEADER_CONTENT_TYPE = "content-type"
    final String HEADER_AUTHORIZATION = "authorization"

    def logger = messageLogFactory.getMessageLog(message)
    def requestHeaders = message.getHeaders()
    def mplId = requestHeaders.get("SAP_MessageProcessingLogID")
    def authHeader = requestHeaders.get("authorization")
    def authHeaderParts = authHeader.split()
    def jwtToken = authHeaderParts[1]
    def supportedAuthMech = ["basic", "oauth", "implicit", "keypair"]

    String mplIdErrorMsg = "(" + mplId + ")."
    String[] chunks = jwtToken.split("\\.")
    Base64.Decoder decoder = Base64.getUrlDecoder()

    String jwtPayload = new String(decoder.decode(chunks[1]))
    jwtPayload = jwtPayload.replaceAll(EDC_NAMESPACE, "")
    Map<String, Object> messageHeaders = new HashMap()
    Map<String, Object> messageProperties = new HashMap()

    messageProperties.put("SAP_MessageProcessingLogCustomStatus", DEFAULT_CUSTOM_STATUS)

    def slurper = new JsonSlurper()
    try {
        def jsonPayload = slurper.parseText(jwtPayload)
        def az_attr = jsonPayload.az_attr

        // if no az_attr key present, then throw Exception
        if (!az_attr) {
            String errorMsg = "Missing embedded Token"
            configureException(logger, messageHeaders, message, messageProperties, errorMsg, mplIdErrorMsg)
            throw new Exception(errorMsg)
        } else {

            def jsonDad = slurper.parseText(jsonPayload.az_attr.dad)
            def dataAddress = jsonDad.properties
            if(dataAddress.participant_id){
                logger.addCustomHeaderProperty("ConsumerId",new String(dataAddress.participant_id))
            }
            if(dataAddress.provider_id){
                logger.addCustomHeaderProperty("ProviderId",new String(dataAddress.provider_id))    
            }
            if(dataAddress.connector_name){
                String connectorName = new String(dataAddress.connector_name);
                logger.addCustomHeaderProperty("ConnectorName", connectorName);
                String secretName = new String(dataAddress.secretName);
                if(secretName && !secretName.toLowerCase().startsWith(("SAP_DataSpaceIntegration_" + connectorName).toLowerCase())) {
                    throw new Exception("Unauthorized access for connector '" + connectorName + "'");
                }
                
            }   
            if(dataAddress.agreement_id){
                logger.addCustomHeaderProperty("AgreementId",new String(dataAddress.agreement_id))
            }
            if(dataAddress.asset_id){
                logger.addCustomHeaderProperty("AssetId",new String(dataAddress.asset_id))
            }
            // handle additional requestHeaders
            List<String> headerNames = processAdditionalHeaders(messageHeaders, dataAddress, requestHeaders)

            // content-type is always set by processContentType — always allow it
            headerNames.add(HEADER_CONTENT_TYPE)

            // handle content-type
            processContentType(messageHeaders, requestHeaders, dataAddress, messageProperties)

            // handle http method
            processHttpMethod(messageProperties, requestHeaders, dataAddress, logger)


            // handle path
            String targetURL = dataAddress.baseUrl
            processHttpPath(messageProperties, requestHeaders, dataAddress, targetURL)

            // handle query param
            processQueryParams(messageProperties, requestHeaders, dataAddress)

            // handle message body
            processMessageBody(message, dataAddress)


            if (dataAddress.authCode) {
                // when the payload already contains an authCode, use it as is
                targetURL = new String(dataAddress.baseUrl)
                String targetAuthCode = new String(dataAddress.authCode)

                messageHeaders.put(HEADER_AUTHORIZATION, targetAuthCode)
                headerNames.add(HEADER_AUTHORIZATION)

                logger.addCustomHeaderProperty("backendurl", targetURL)
                logger.addCustomHeaderProperty("AuthType", "implicit")
                messageProperties.put("backendurl", targetURL)
                messageProperties.put("authtype", "implicit")
            } else {
                // when the payload does not contains an authCode
                String authType
                if (dataAddress.headers instanceof List) {
                    authType = extractAuthTypeFromHeaders(dataAddress.headers)
                } else {
                    authType = new String(dataAddress["header:authType"])
                }

                if (supportedAuthMech.contains(authType) || authType.isEmpty()) {
                    logger.addCustomHeaderProperty("backendurl", targetURL)
                    logger.addCustomHeaderProperty("AuthType", authType)

                    messageProperties.put("backendurl", targetURL)
                    messageProperties.put("authtype", authType)

                    if (dataAddress.secretName) {
                        String secretName = new String(dataAddress.secretName)
                        messageProperties.put("secretname", secretName)
                        logger.addCustomHeaderProperty("secretname", secretName)
                    }
                } else {
                    throw new Exception("Auth type: " + authType + " not suported")
                }
            }

            // Set allowedHeaders centrally — token-array headers + content-type + authorization (when authCode)
            String allowedHeaders = headerNames.join("|")
            messageProperties.put("allowedHeaders", allowedHeaders)
        }
    } catch (JsonException e) {
        String errorMsg = "Error in parsing embedded token. Exception msg: " + e.getMessage()
        configureException(logger, messageHeaders, message, messageProperties, errorMsg, mplIdErrorMsg)
    } catch (Exception e) {
        String errorMsg = "Exception msg: " + e.getMessage()
        configureException(logger, messageHeaders, message, messageProperties, errorMsg, mplIdErrorMsg)
    }

    message.setProperties(messageProperties)
    message.setHeaders(messageHeaders)

    return message
}

List<String> processAdditionalHeaders(Map<String, Object> messageHeaders, Map<Object, Object> dataAddress,
                                       Map<Object, Object> requestHeaders) {
    List<String> headerNames = []
    Map<String, Object> additionalHeaders = new HashMap<>()

    if (dataAddress.headers instanceof List) {
        dataAddress.headers.each { header ->
            String headerName = header.name
            boolean isProxy = header.proxy ?: false
            String value
            if (isProxy) {
                value = requestHeaders.get(headerName)
                if (value == null) {
                    value = header.defaultValue
                }
            } else {
                value = header.defaultValue
            }
            if (value != null) {
                additionalHeaders.put(headerName, value)
                headerNames.add(headerName)
            }
        }
    } else {
        dataAddress.each { key, value ->
            if (key.startsWith("header:")) {
                def updatedHeader = key.replace("header:", "")
                additionalHeaders.put(updatedHeader, value)
                headerNames.add(updatedHeader)
            }
        }
    }

    messageHeaders.putAll(additionalHeaders)
    return headerNames
}


void processContentType(Map<String, Object> messageHeaders, Map<Object, Object> requestHeaders, Map<Object, Object> dataAddress, Map<String, Object> messageProperties) {
    String contentType = dataAddress.contentType
    //1.0 logic
    boolean isContentTypeEmpty = contentType == null || "".equals(contentType)
    contentType = isContentTypeEmpty ? "application/octet-stream" : contentType;
    boolean isProxyContentTypeEnabled = false;
    if(dataAddress.proxyContentType){
        //2.0 logic
        isProxyContentTypeEnabled = dataAddress.proxyContentType;
    }
    boolean isProxyContentTypeEnabledOverall = (isContentTypeEmpty && requestHeaders.get("content-type") != null) || isProxyContentTypeEnabled;
    messageProperties.put("isProxyContentTypeEnabled", isProxyContentTypeEnabledOverall);
    if (isProxyContentTypeEnabledOverall) {
        contentType = requestHeaders.get("content-type")
    }
    messageHeaders.put("content-type", contentType)
}


void processHttpMethod(Map<String, Object> messageProperties, Map<Object, Object> requestHeaders, Map<Object, Object> dataAddress, MessageLog logger) {
    String httpMethod = dataAddress.method != null ? dataAddress.method : "GET"
    boolean isProxyMethodEnabled = dataAddress.proxyMethod instanceof Boolean ? dataAddress.proxyMethod : Boolean.parseBoolean(dataAddress.proxyMethod?.toString());
    messageProperties.put("isProxyMethodEnabled", isProxyMethodEnabled);
    if (isProxyMethodEnabled) {
        logger.addCustomHeaderProperty("httpMethod", requestHeaders.get("CamelHttpMethod"))
        httpMethod = requestHeaders.get("CamelHttpMethod")
    }
    messageProperties.put("httpMethod", httpMethod)
}

void processHttpPath(Map<String, Object> messageProperties, Map<Object, Object> requestHeaders, Map<Object, Object> dataAddress, String targetURL) {
    String path = dataAddress.path != null ? dataAddress.path : ""
    boolean isProxyPathEnabled = dataAddress.proxyPath instanceof Boolean ? dataAddress.proxyPath : Boolean.parseBoolean(dataAddress.proxyPath?.toString());
    messageProperties.put("isProxyPathEnabled",isProxyPathEnabled);
    if (isProxyPathEnabled) {
        path = requestHeaders.get("CamelHttpPath")
        if (path == null || "/dataspaceintegration/httpdataplane".equals(path) || targetURL.endsWith(path)) {
            path = ""
        }
    }
    if (!path.isEmpty() || isProxyPathEnabled) {
        if (!targetURL.endsWith("/") && (!path.startsWith("/")) && !path.isEmpty()) {
            path = "/" + path
        }
        if (targetURL.endsWith("/") && (path.startsWith("/"))) {
            path = path.substring(1)
        }
        messageProperties.put("path", path)
    }
}

void processQueryParams(Map<String, Object> messageProperties, Map<Object, Object> requestHeaders, Map<Object, Object> dataAddress) {
    String query = dataAddress.queryParams != null ? dataAddress.queryParams : ""
    boolean isProxyQuery = false;
    if (dataAddress.proxyQueryParams instanceof String) {
        String proxyQueryParams = new String(dataAddress.proxyQueryParams);
        isProxyQuery = "true".equals(proxyQueryParams);
    }
     //2.0 logic
    isProxyQuery = dataAddress.proxyQueryParameters !=null ? dataAddress.proxyQueryParameters : isProxyQuery;
    if (isProxyQuery) {
        query = requestHeaders.get("CamelHttpQuery")
        if (!query) {
            query = ""
        }
    }
    messageProperties.put("query", query)
}

void processMessageBody(Message message, Map<Object, Object> dataAddress) {
    if (dataAddress.proxyBody instanceof String) {
        String proxyBody = new String(dataAddress.proxyBody)
        if (("false".equals(proxyBody)) || (!proxyBody)) {
            message.setBody("")
        }
    }
}

String extractAuthTypeFromHeaders(List headers) {
    def authTypeHeader = headers.find { it.name == "authType" }
    return authTypeHeader?.defaultValue ?: ""
}

void configureException(MessageLog logger, Map<String, Object> messageHeaders, Message message, Map<String, Object> messageProperties, String errorMsg, String mplIdErrorMsg) {

    final String GENERIC_ERROR_MESSAGE = "Data Space Integration ran into an error. Please retry by restarting the data transfer process. If you experience further issues, please contact your Data Space Integration administrator "
    logger.addAttachmentAsString("exception", errorMsg, "text/plain")

    messageHeaders.put("CamelHttpResponseCode", "500")
    message.setBody(GENERIC_ERROR_MESSAGE + mplIdErrorMsg)
    messageProperties.put("SAP_MessageProcessingLogCustomStatus", "Failed")
}
