package script

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.transform.ToString
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/*
    custom builder for setting up the default values
    set up all fields with default values here
*/

@Builder(builderStrategy = ExternalStrategy, forClass = AwsAuthenticator, prefix = 'with')
class AwsAuthenticatorBuilder {

    AwsAuthenticatorBuilder() {
        instant = Instant.now()
        doPayloadHash = false // not supported yet
        httpQuery = ''
    }

}

/**
 * Calculates the authorization string for AWS requests
 * http://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html*/
@ToString(includeNames = true)
class AwsAuthenticator {

    Instant instant
    Boolean doPayloadHash
    String httpMethod
    String httpHost
    String httpPath
    String httpQuery
    String region
    String service
    String accessKey
    String secretKey

    static AwsAuthenticatorBuilder builder() {
        return new AwsAuthenticatorBuilder()
    }

    /**
     * add the calculated AWS authorization header to the given message
     * @param message
     */
    void authorizeMessage(Message message) {
        Objects.requireNonNull(message, 'AwsAuthenticator authorizeMessage: message must not be null')

        verifySettings()

        // generate timestamps
        String amzDate = getAmzDate(instant)
        String amzTimestamp = getAmzTimeStamp(instant)

        // copy aws headers to the headers list for signing
        Map<String, Object> headers = message.getHeaders()
        Map<String, Object> awsHeaders = headers.findAll { entry -> entry.key.startsWith('x-amz-') }

        // set up mandatory aws headers
        awsHeaders.put('host', httpHost)
        awsHeaders.put('x-amz-date', amzTimestamp)
        // set flag that the requester pays
        // see also: https://docs.aws.amazon.com/AmazonS3/latest/userguide/RequesterPaysExamples.html
        awsHeaders.put('x-amz-request-payer', 'requester')

        String payloadHash = 'UNSIGNED-PAYLOAD'
        if (doPayloadHash) {
            throw new DsiException('Payload Hash not yet supported')
        }
        awsHeaders.put('x-amz-content-sha256', payloadHash)

        // generate string to sign
        String algorithm = 'AWS4-HMAC-SHA256'
        String credentialScope = "${amzDate}/${region}/${service}/aws4_request"
        String signedHeaders = awsHeaders.keySet().collect { item -> item.toLowerCase() }.sort().join(';')

        // concat all elements with a newline character
        List<String> canonicalElements = [
                httpMethod,
                httpPath,
                getCanonicalQueryString(httpQuery),
                getCanonicalHeaders(awsHeaders),
                signedHeaders,
                payloadHash,
        ]
        String canonicalRequest = canonicalElements.join('\n')

        String stringToSign = "${algorithm}\n${amzTimestamp}\n${credentialScope}\n${hash(canonicalRequest)}"

        // create signature
        byte[] kSecret = ('AWS4' + secretKey).getBytes('UTF8')
        byte[] kDate = hmacSHA256(amzDate, kSecret)
        byte[] kRegion = hmacSHA256(region, kDate)
        byte[] kService = hmacSHA256(service, kRegion)
        byte[] kSigning = hmacSHA256('aws4_request', kService)
        String signature = bytesToHex(hmacSHA256(stringToSign, kSigning))

        // create authorization string
        String authorization = "${algorithm} Credential=${accessKey}/${credentialScope}, SignedHeaders=${signedHeaders}, Signature=${signature}"

        // add aws headers to message headers
        awsHeaders.each { entry -> message.setHeader(entry.key, entry.value) }

        // set authorization header
        message.setHeader('Authorization', authorization)

        // log the intermediate values for debugging
        String debugValues = """
            timestamp        : ${instant.toEpochMilli()}
            canonicalRequest : $canonicalRequest
            stringToSign     : $stringToSign
            signature        : $signature
            authorization    : $authorization
        """
        Logger.debug('AwsAuthenticator', debugValues)
    }

    private static String hash(String value) {
        return MessageDigest.getInstance('SHA-256')
                .digest(value.getBytes('UTF-8')).encodeHex().toString()
    }

    // hash the payload
    private static String hash(byte[] value) {
        return MessageDigest.getInstance('SHA-256').digest(value).encodeHex().toString()
    }

    private static String getAmzDate(Instant instant) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern('yyyyMMdd')
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC).format(formatter)
    }

    private static String getAmzTimeStamp(Instant instant) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC).format(formatter)
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexArray = '0123456789abcdef'.toCharArray()
        char[] hexChars = new char[bytes.length * 2]
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF
            hexChars[j * 2] = hexArray[v >>> 4]
            hexChars[j * 2 + 1] = hexArray[v & 0x0F]
        }
        return new String(hexChars).toLowerCase()
    }

    private void assertNotEmptyValue(String varName) {
        Objects.requireNonNull(this[varName], "AwsAuthenticator authorizeMessage: ${varName} must not be null")
        String value = this[varName]
        assert (!value.trim().isEmpty())
    }

    /**
     * make sure all mandatory properties are set
     **/
    private void verifySettings() {
        assertNotEmptyValue('httpMethod')
        assertNotEmptyValue('httpHost')
        assertNotEmptyValue('httpPath')
        assertNotEmptyValue('httpQuery')
        assertNotEmptyValue('region')
        assertNotEmptyValue('service')
        assertNotEmptyValue('accessKey')
        assertNotEmptyValue('secretKey')
    }

    private static byte[] hmacSHA256(String data, byte[] key) {
        String algorithm = 'HmacSHA256'
        Mac mac = Mac.getInstance(algorithm)
        mac.init(new SecretKeySpec(key, algorithm))
        return mac.doFinal(data.getBytes('UTF-8'))
    }

    private static String getCanonicalHeaders(Map<String, Object> headers) {
        String canonicalHeaders = headers.collect { entry ->  "${entry.key.toLowerCase()}:${entry.value.toString().trim()}" }.sort().join('\n')
        return canonicalHeaders + '\n'
    }

    private static String getCanonicalQueryString(String query) {
        if ((query ?: '').trim().isEmpty()) {
            return ''
        }
        Map<String, String> queryParamsMap = convertQueryToMap(query)
        String canonicalQueryParams = queryParamsMap.collect { entry -> "${URLEncoder.encode(entry.key, 'UTF-8').replace('+', '%20')}=${URLEncoder.encode(entry.value, 'UTF-8').replace('+', '%20')}" }.sort().join('&')
        return canonicalQueryParams
    }

    private static Map<String, String> convertQueryToMap(String urlQueryPart) throws UnsupportedEncodingException {
        Map<String, String> parameterMap = [:]

        if (ScriptUtils.isNullOrBlank(urlQueryPart)) {
            return parameterMap
        }

        // remove ? at the beginning
        String query = ScriptUtils.trimAtStart(urlQueryPart.trim(), '?')
        // remove & at the beginning
        query = ScriptUtils.trimAtStart(query, '&')

        String[] pairs = query.split('&')
        int counter = 0
        for (String pair : pairs) {
            counter++
            int index = pair.indexOf('=')
            if (index == -1) {
                // parameter can be a single word
                String key = URLDecoder.decode(pair, 'UTF-8')
                if (ScriptUtils.isNullOrBlank(key)) {
                    throw new DsiException("Parameter #${counter} is empty or invalid")
                }
                parameterMap.put(key, '')
            } else {
                String key = URLDecoder.decode(pair.substring(0, index), 'UTF-8')
                if (ScriptUtils.isNullOrBlank(key)){
                    throw new DsiException("Parameter #${counter} has no valid name")
                }

                String value = URLDecoder.decode(pair.substring(index + 1), 'UTF-8')
                parameterMap.put(key, value)
            }
        }
        return parameterMap
    }

}
