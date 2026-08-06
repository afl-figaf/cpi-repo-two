import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper

Message processData(Message message) {
    // Get the incoming JSON payload (which is an array)
    String jsonPayloadString = message.getBody(java.lang.String)

    if (jsonPayloadString == null || jsonPayloadString.isEmpty()) {
        message.setProperty("ScriptError_PayloadMissing", "Incoming JSON payload (transport array) was null or empty.")
        // Decide on error handling
        throw new Exception("Incoming JSON payload (transport array) was null or empty.")
    }

    def slurper = new JsonSlurper()
    def parsedPayload // This will be a List of Maps

    try {
        parsedPayload = slurper.parseText(jsonPayloadString)
    } catch (Exception e) {
        message.setProperty("ScriptError_JsonParsingFailed", "Failed to parse incoming JSON (transport array): " + e.getMessage())
        throw new Exception("Failed to parse incoming JSON (transport array): " + e.getMessage(), e)
    }

    // Check if the parsed payload is a List and is not empty
    if (parsedPayload instanceof List && !parsedPayload.isEmpty()) {
        // Get the first object (transport) from the list
        def firstTransportObject = parsedPayload[0] // Or parsedPayload.getAt(0)

        if (firstTransportObject != null && firstTransportObject instanceof Map) {
            // Extract the technicalTransportId from the first transport object
            String technicalTransportIdValue = firstTransportObject.technicalTransportId

            if (technicalTransportIdValue != null && technicalTransportIdValue instanceof String) {
                message.setProperty("technicalTransportId", technicalTransportIdValue)
                // Optional: Log success
                // message.setProperty("ScriptInfo_TransportIdExtracted", "Extracted technicalTransportId: " + technicalTransportIdValue)
            } else {
                message.setProperty("ScriptWarning_TechnicalTransportIdMissingOrInvalid", "'technicalTransportId' field was not found or not a string in the first transport object.")
                // Depending on requirements, you might want to throw an exception here if the ID is mandatory.
            }
        } else {
            message.setProperty("ScriptWarning_FirstTransportObjectInvalid", "The first element in the JSON array is not a valid object.")
            // Depending on requirements, you might want to throw an exception.
        }
    } else {
        message.setProperty("ScriptWarning_PayloadNotListOrEmpty", "Parsed JSON payload is not a list or is empty.")
        // Depending on requirements, you might want to throw an exception.
    }

    // The message body remains the original JSON array payload unless explicitly changed.
    return message
}