import com.sap.gateway.ip.core.customdev.util.Message;
import groovy.json.JsonSlurper;
import groovy.json.JsonOutput;

def Message processData(Message message) {
    
    def body = message.getBody(String)
    def json = new JsonSlurper().parseText(body)
    
    def jiraUrl = message.getProperty("Jira_URL") 
    
    def stripHtml = { html ->
        html?.replaceAll(/<[^>]+>/, '')?.replaceAll('&nbsp;', ' ')?.replaceAll(/&#(\d+);/) { m ->
            Character.toChars(m[1].toInteger()).toString()
        }?.trim()
    }

    def result = json.issues.collect { issue ->
      [
        ID         : issue.key,
        URL        : "${jiraUrl}/browse/${issue.key}",
        Title      : issue.fields.summary,
        Description: stripHtml(issue.fields.description),
        Status     : issue.fields.status.name
      ]
}

message.setBody(JsonOutput.toJson(result))
return message
}



