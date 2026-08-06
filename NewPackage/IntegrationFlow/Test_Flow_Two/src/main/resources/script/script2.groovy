import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    message.setHeader("header one", "value one")
    message.setHeader("header two", "value two")
    return message
}