package script
/* Refer the link below to learn more about the use cases of script.
https://help.sap.com/viewer/368c481cd6954bdfa5d0435479fd4eaf/Cloud/en-IN/148851bf8192412cba1f9d2c17f4bd25.html

If you want to know more about the SCRIPT APIs, refer the link below
https://help.sap.com/doc/a56f52e1a58e4e2bac7f7adbf45b2e26/Cloud/en-IN/index.html */
import java.util.HashMap;
import com.sap.gateway.ip.core.customdev.util.Message;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

def Message processData(Message message) {
    Logger.init(messageLogFactory, message)
    def logger = messageLogFactory.getMessageLog(message);
    // get a map of properties
    def map = message.getProperties();
    def requestHeaders = message.getHeaders();
    Map < String, Object > messageProperties = new HashMap();

    def mplId = requestHeaders.get("SAP_MessageProcessingLogID");
    String mplIdErrorMsg = "(" + mplId + ").";

    final String GENERIC_ERROR_MESSAGE = "Data Space Integration ran into an error. Please retry by restarting the data transfer process. If you experience further issues, please contact your Data Space Integration administrator ";

    // get an exception java class instance
    def ex = map.get("CamelExceptionCaught");
    if (ex != null) {
        message.setHeaders(requestHeaders);
        String errorMsg = "Exception msg: " + ex.getMessage();
        logger.addAttachmentAsString("exception", errorMsg, "text/plain");
        messageProperties.put("SAP_MessageProcessingLogCustomStatus", "Failed");
        def httpStatusCode = retrieveStatusCodeFromException(message, ex);
        requestHeaders.put("CamelHttpResponseCode", httpStatusCode);
        // copy the http error response to the message body
        message.setBody(GENERIC_ERROR_MESSAGE + mplIdErrorMsg);
    }
    message.setProperties(messageProperties);
    return message;
}

def String retrieveStatusCodeFromException(Message message, Exception ex){
    def httpStatusCode = "500";
    def defaultStatusCode = "500";
    try{
        Pattern codePattern = Pattern.compile("Status code:(\\d+)");
        Matcher codeMatcher = codePattern.matcher(ex.getMessage());
        if (codeMatcher.find()) {
           httpStatusCode= codeMatcher.group(1);
        }
        // mapping status code
        def properties = message.getProperties();
        boolean isProxyContentTypeEnabled = properties.get("isProxyContentTypeEnabled");
        boolean isProxyPathEnabled = properties.get("isProxyPathEnabled");
        boolean isProxyMethodEnabled = properties.get("isProxyMethodEnabled");
        def overwriteList = ["401", "403", "503"] as String[];
        def returnAsIsList = ["400", "422"] as String[];
        if(httpStatusCode in overwriteList){
            return defaultStatusCode;
        }
        if(httpStatusCode in returnAsIsList){
            return httpStatusCode;
        }
        if((httpStatusCode.equals("404") && isProxyPathEnabled)|| (httpStatusCode.equals("405") && isProxyMethodEnabled) 
        || (httpStatusCode.equals("415") && isProxyContentTypeEnabled)){
            return httpStatusCode;
        }
        return defaultStatusCode;    
    }catch(Exception exception){
        return httpStatusCode;
    }
    
}