package script

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper

/**
 * Utility class for parsing JWT tokens
 */
class JwtToken {

    Message message
    Object headerObject
    Object payloadObject

    JwtToken() {
        // This constructor must not be used
    }

    JwtToken(Message message) {
        if (message == null) {
            throw new DsiException('Message must not be null')
        }
        this.message = message
        parseToken()
    }

    Object getPayload() {
        return payloadObject
    }

    Object getHeaders() {
        return headerObject
    }

    // return a custom attribute value from the 'az_attr' token attribute section
    Object getCustomAttribute(String attName) {
        try {
            if (payload == null) {
                return null
            }
            // get the custom claims root element
            Object azAttrObject = payload[('az_attr')]
            if (azAttrObject == null) {
                return null
            }
            return azAttrObject[(attName)]
        } catch (Exception e) {
            throw new DsiException('Error reading custom attribute ' + attName + ' from JWT token: ' + e.getMessage())
        }
    }

    /**
     * Extract the JWT token from the Authorization header
     */
    private String getToken() {
        String authHeader = message.getHeader('Authorization', String)
        if (authHeader == null) {
            throw new DsiException('No Authorization header found')
        }
        if (!authHeader.trim().toLowerCase().startsWith('bearer ')) {
            throw new DsiException('Authorization does not contain a Bearer token')
        }
        String[] authHeaderParts = authHeader.trim().split(' ')
        if (authHeaderParts.size() < 2) {
            throw new DsiException('Authorization header does not contain a valid Bearer token')
        }
        return authHeaderParts[1]
    }

    private void parseToken() {
        String token = getToken()

        try {
            // split token into header, payload and signature
            String[] parts = token.split('\\.')
            if (parts.length != 3) {
                throw new DsiException('Invalid or missing JWT token')
            }

            // decode header
            String header = new String(Base64.getUrlDecoder().decode(parts[0]))
            JsonSlurper slurper = new JsonSlurper()
            headerObject = slurper.parseText(header)
            // check if token is a JWT token
            if (headerObject['typ'] != 'JWT') {
                throw new DsiException('Not a JWT token')
            }

            // decode payload
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]))
            payloadObject = slurper.parseText(payload)

            //TODO validate signature
        } catch (Exception e) {
            throw new DsiException("Error parsing JWT token: " + e.getMessage())
        }
    }

}
