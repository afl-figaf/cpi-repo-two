import com.sap.gateway.ip.core.customdev.util.Message;
import java.util.HashMap;

//Remove "figafheader_" prefix (case-insensitive)
def Message processData(Message message) {
    def headers = message.getHeaders();
    def keysToModify = [];

    // Collect keys that need modification
    headers.each { key, value ->
        if (key.toLowerCase().startsWith("figafheader_")) {
            keysToModify << key;
        }
    }

    // Modify the headers directly in the original map
    keysToModify.each { key ->
        def value = headers.get(key);
        def newKey = key.replaceFirst("(?i)^figafheader_", "");
        message.setHeader(newKey, value); // Add the modified header
        headers.remove(key); // Remove the original header
    }

    return message;
}
