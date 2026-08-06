import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper

Message processData(Message message) {
    // Get the incoming JSON payload (assumed to be from Jira or similar)
    String jsonPayloadString = message.getBody(java.lang.String)

    if (jsonPayloadString == null || jsonPayloadString.isEmpty()) {
        message.setProperty("ScriptError_PayloadMissing", "Incoming JSON payload was null or empty.")
        throw new Exception("Incoming JSON payload was null or empty.")
    }

    def slurper = new JsonSlurper()
    def parsedPayload

    try {
        parsedPayload = slurper.parseText(jsonPayloadString)
    } catch (Exception e) {
        message.setProperty("ScriptError_JsonParsingFailed", "Failed to parse incoming JSON: " + e.getMessage())
        throw new Exception("Failed to parse incoming JSON: " + e.getMessage(), e)
    }

    // --- Extract Jira Issue Key ---
    def issueNode = parsedPayload.issue // Get the 'issue' object
    String issueKey = null

    if (issueNode != null && issueNode instanceof Map) { // Check if 'issue' node exists and is an object
        issueKey = issueNode.key // Get the 'key' from the 'issue' object
        if (issueKey != null && issueKey instanceof String) {
            message.setProperty("externalTicketIds", issueKey)
            message.setProperty("ScriptInfo_IssueKeyExtracted", "Extracted issue key: " + issueKey)
        } else {
            message.setProperty("ScriptWarning_IssueKeyMissingOrInvalid", "'key' field was not found or not a string within the 'issue' node.")
        }
    } else {
        message.setProperty("ScriptWarning_IssueNodeMissing", "'issue' node was not found or not an object in the payload.")
    }

    return message
}