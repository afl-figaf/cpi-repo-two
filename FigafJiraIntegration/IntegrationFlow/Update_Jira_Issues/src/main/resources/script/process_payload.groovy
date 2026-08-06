import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

def resolveTemplate(String template, def data) {
    if (template == null || data == null) {
        return template
    }
    return template.replaceAll(/\[\[(.+?)\]\]/) { fullMatch, placeholderPath ->
        try {
            def value = placeholderPath.tokenize('.').inject(data) { current, part ->
                if (current == null) return null
                // Handle array access like webhookTicketDtoList[0] or simpleArray[1]
                if (part.contains('[') && part.endsWith(']')) {
                    def arrayName = part.substring(0, part.indexOf('['))
                    def indexStr = part.substring(part.indexOf('[') + 1, part.length() - 1)
                    if (indexStr.isNumber()) {
                        def index = indexStr.toInteger()
                        if (arrayName.isEmpty() && current instanceof List) { // e.g. {{[0].name}} if data is a list
                             return current.getAt(index)
                        } else if (!arrayName.isEmpty() && current."$arrayName" instanceof List) { // e.g. {{webhookTicketDtoList[0].name}}
                            return current."$arrayName"?.getAt(index)
                        } else {
                            return null // Array or index not valid
                        }
                    } else {
                         return null // Index not a number
                    }
                }
                return current."$part" // Standard property access
            }
            return value != null ? value.toString() : fullMatch // If value is null, return the original placeholder
        } catch (Exception e) {
            // If placeholder resolution fails, return the original placeholder
            // For debugging: message.setProperty("TemplateError_${placeholderPath}", e.toString())
            return fullMatch
        }
    }
}

def Message processData(Message message) {

    def props = message.getProperties()
    def slurper = new JsonSlurper()

    // 1. Get Configuration JSON from MESSAGE BODY
    String configJsonString = message.getBody(java.lang.String)
    if (configJsonString == null || configJsonString.isEmpty()) {
        message.setProperty("ProcessingError", "Configuration JSON in message body is null or empty.")
        message.setProperty("ShouldProcess", false)
        // throw new IllegalStateException("Configuration JSON in message body is null or empty.")
        return message
    }
    def config = slurper.parseText(configJsonString) // Parse the configuration JSON
    
    // 2. Get Figaf Event Type and Entity Type from incoming payload
    // 2. Get Incoming Figaf Payload from Exchange Property
    String figafJsonStringFromProperty = props.get("incomingFigafJsonBody") // Name of your property
    if (figafJsonStringFromProperty == null || figafJsonStringFromProperty.isEmpty()) {
        message.setProperty("ProcessingError", "Exchange property 'incomingFigafJsonBody' (Figaf payload) is not set or is empty.")
        message.setProperty("ShouldProcess", false)
        // throw new IllegalStateException("Exchange property 'incomingFigafJsonBody' (Figaf payload) is not set or is empty.")
        return message
    }
    def figafPayload = slurper.parseText(figafJsonStringFromProperty) // Parse incoming Figaf message

    //Get Figaf Event Type and Entity Type from Figaf payload   
    String figafEventType = figafPayload.eventType
    String figafEntityType = figafPayload.entityType
    message.setProperty("FigafEventType", figafEventType)
    message.setProperty("FigafEntityType", figafEntityType)
    message.setProperty("FigafEntityID", figafPayload.figafEntityId) // For logging/tracing

    // 3. Find Event-Specific Configuration
    def eventConfig = config.eventMappings[figafEventType]
    if (eventConfig == null) {
        // Optional: Check for a default fallback configuration if you implement one in your JSON
        // def fallbackConfig = config.eventMappings["DEFAULT_EVENT_FALLBACK"]
        // if (fallbackConfig != null) { eventConfig = fallbackConfig } else { ... }
        message.setProperty("ProcessingError", "No configuration found in 'configJSON' for eventType: ${figafEventType}")
        message.setProperty("ShouldProcess", false)
        throw new IllegalStateException("Externalized parameter 'configJSON' is not configured or is empty.")
        return message
    }

    boolean isEnabled = eventConfig.enabled instanceof Boolean ? eventConfig.enabled : (eventConfig.enabled.toString().toLowerCase() == 'true')
    //message.setProperty("IsEnabled", isEnabled)
    if (!isEnabled) {
        message.setProperty("ShouldProcess", false)
        log.info("Processing skipped for eventType '${figafEventType}' as it is disabled in configuration.")
        return message
    }
    message.setProperty("ShouldProcess", true) // Indicates that processing should continue

    String actionType = eventConfig.actionType
    message.setProperty("JiraActionType", actionType)

    // 4. Extract Jira Issue Key to Update (from incoming Figaf payload)
    String jiraIssueKeyToUpdate = null
    if (figafEntityType == "TICKET") {
        jiraIssueKeyToUpdate = figafPayload.externalTicketId
    } else if (figafEntityType == "TRANSPORT") {
        if (figafPayload.webhookTicketDtoList && !figafPayload.webhookTicketDtoList.isEmpty()) {
            // Assuming the first ticket in the list is the relevant one
            jiraIssueKeyToUpdate = figafPayload.webhookTicketDtoList[0].externalTicketId
        }
    }

    // Check if issue key is needed based on action type and if it was found
    if (actionType != "createIssue") { // Actions like "transitionAndComment", "commentOnly" need an issue key
        if (jiraIssueKeyToUpdate == null || jiraIssueKeyToUpdate.isEmpty()) {
            message.setProperty("ProcessingError", "Could not determine Jira Issue Key from Figaf payload for entity ${figafPayload.figafEntityId}. Required for actionType '${actionType}'.")
            message.setProperty("ShouldProcess", false)
            return message
        }
    }
    message.setProperty("JiraIssueKeyToUpdate", jiraIssueKeyToUpdate)

    //-----------------------------------------------------------------------------------------------------------------------------
    //  Prepare Payloads for Api Calls
    //-----------------------------------------------------------------------------------------------------------------------------

    // 5. Prepare Jira Transition Payload (if applicable)
    String jiraTransitionPayloadJson = null
    if (eventConfig.transitionDetails && eventConfig.transitionDetails.id) {
        def transitionPayloadMap = [transition: [id: eventConfig.transitionDetails.id.toString()]] // Ensure ID is a string

        // Handle fields for the transition, resolving placeholders if necessary
        /*if (eventConfig.transitionDetails.fields && !eventConfig.transitionDetails.fields.isEmpty()) {
            def resolvedFields = [:]
            eventConfig.transitionDetails.fields.each { key, value ->
                if (value instanceof String) {
                    resolvedFields[key] = resolveTemplate(value, figafPayload)
                } else if (value instanceof Map || value instanceof List) { // For complex field structures like resolution: {name: "Done"}
                    // If the value itself is a map/list (e.g. for resolution), serialize it and then resolve placeholders within its string values
                    def tempJsonStructure = new JsonBuilder(value).toString()
                    def resolvedJsonStructure = resolveTemplate(tempJsonStructure, figafPayload)
                    resolvedFields[key] = slurper.parseText(resolvedJsonStructure) // Parse back to map/list
                } else {
                    resolvedFields[key] = value // Use as is if not a string (e.g. boolean, number)
                }
            }
            if (!resolvedFields.isEmpty()) {
                 transitionPayloadMap.fields = resolvedFields
            }
        }*/
        jiraTransitionPayloadJson = new JsonBuilder(transitionPayloadMap).toString()
    }
    message.setProperty("JiraTransitionPayload", jiraTransitionPayloadJson)
    //message.setProperty("JiraTransitionIdConfigured", eventConfig.transitionDetails?.id?.toString())


    // 6. Prepare Jira Comment Payload (if applicable)
    String jiraCommentPayloadJson = null
    String rawCommentTextForProperty = null // For storing the fully resolved comment text
    if (eventConfig.comment && eventConfig.comment instanceof String) {
        String rawCommentTemplate = eventConfig.comment
        String resolvedCommentText = resolveTemplate(rawCommentTemplate, figafPayload)
        
        // Optionally prepend global default header from configJSON
        String defaultHeader = config.globalJiraConfig?.defaultCommentHeader ?: ""
        if (!defaultHeader.isEmpty() && !resolvedCommentText.startsWith(defaultHeader)) {
            resolvedCommentText = defaultHeader + " " + resolvedCommentText
        }
        rawCommentTextForProperty = resolvedCommentText // Save for property

        def commentPayloadMap = [body: resolvedCommentText]
        // Example: Add visibility if defined in global config
        // if (config.globalJiraConfig?.defaultCommentVisibility) {
        //    commentPayloadMap.visibility = config.globalJiraConfig.defaultCommentVisibility
        // }
        jiraCommentPayloadJson = new JsonBuilder(commentPayloadMap).toString()
    }
    message.setProperty("JiraCommentPayload", jiraCommentPayloadJson)
    //message.setProperty("JiraResolvedCommentText", rawCommentTextForProperty) // Store resolved text
    
    return message;
}