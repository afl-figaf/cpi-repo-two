package script
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.util.slurpersupport.GPathResult
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException

/**
 * setup header and authorization for an AWS http call
 * @param message
 * @return message
 */
Message prepareAwsCall(Message message) {
    Logger.init(messageLogFactory, message)

    // set up properties from message
    String httpMethod = ScriptUtils.getMandatoryProperty(message, Constants.Property.Aws.HTTP_METHOD)
    String httpHost = ScriptUtils.getMandatoryProperty(message, Constants.Property.Aws.HTTP_HOST)
    String httpPath = ScriptUtils.getMandatoryProperty(message, Constants.Property.Aws.HTTP_PATH)
    String httpQuery = ScriptUtils.getMandatoryProperty(message, Constants.Property.Aws.HTTP_QUERY, '')
    String awsService = ScriptUtils.getMandatoryProperty(message, Constants.Property.Aws.HTTP_SERVICE)
    String awsRegion = ScriptUtils.getMandatoryProperty(message, Constants.Property.Aws.HTTP_REGION)
    String awsSessionToken = ScriptUtils.getProperty(message, Constants.Property.Aws.HTTP_SESSION_TOKEN, null)

    // limit allowed request headers in order not to expose internal/secret data by accident
    List<String> requestHeadersList = [
            'Authorization',
            'Range',
            'x-amz-content-sha256',
            'x-amz-date',
            'x-amz-request-payer'
    ]

    if (awsSessionToken != null) {
        message.setHeader(Constants.Header.Aws.HTTP_SESSION_TOKEN, awsSessionToken)
        requestHeadersList.add(Constants.Header.Aws.HTTP_SESSION_TOKEN)
    }

    message.setProperty(Constants.Property.Aws.HTTP_REQUEST_HEADERS, requestHeadersList.join('|'))

    // get credential
    HashMap<String, String> credentialMap = ScriptUtils.getMandatoryProperty(message, Constants.Property.Aws.HTTP_CREDENTIAL_ALIAS)
    if (credentialMap == null) {
        throw new DsiException('Internal Error. Credential property is missing.')
    }
    String accessKey = credentialMap.accessKey
    if (ScriptUtils.isNullOrBlank(accessKey)) {
        throw new DsiException('Internal Error. Access Key property has no data.')
    }

    String secretKey = credentialMap.secretKey
    if (ScriptUtils.isNullOrBlank(secretKey)) {
        throw new DsiException('Internal Error. Secret Key property has no data.')
    }

    // add aws authentication
    AwsAuthenticator.builder()
            .withHttpMethod(httpMethod)
            .withHttpHost(httpHost)
            .withHttpPath(httpPath)
            .withHttpQuery(httpQuery)
            .withService(awsService)
            .withRegion(awsRegion)
            .withAccessKey(accessKey)
            .withSecretKey(secretKey)
            .build()
            .authorizeMessage(message)

    handleContentType(message)

    // use test endpoint if optional TEST_ENDPOINT property is set
    String testEndpoint = message.getProperty(Constants.Property.TEST_ENDPOINT) as String
    if (testEndpoint != null) {
        message.setProperty(Constants.Property.Aws.HTTP_URL, testEndpoint + httpPath)
        message.setHeader(Constants.Header.HTTP_AUTHORIZATION, message.getProperty(Constants.Property.TEST_AUTHORIZATION))
    }

    return message
}

// check if we have a camel exception and setup the error properties
Message handleApiException(Message message) {
    Logger.init(messageLogFactory, message)

    boolean errorFlagSet = ScriptUtils.getProperty(message, Constants.Property.ERROR_FLAG, false) as boolean

    // if error flag is set, we have handled the error already and error properties have been set
    if (!errorFlagSet) {
        message.setProperty(Constants.Property.ERROR_FLAG, false)
        Exception ex = message.getProperties().get(Constants.Property.CAMEL_EXCEPTION_CAUGHT)
        if (ex != null) {
            message.setProperty(Constants.Property.ERROR_FLAG, true)
            message.setProperty(Constants.Property.ERROR_CODE, ex.getClass().getName())
            message.setProperty(Constants.Property.ERROR_MESSAGE, ex.getMessage())
        }
    }

    return message
}

// throw exception if error flag is true
Message onErrorThrow(Message message) {
    Logger.init(messageLogFactory, message)

    boolean errorFlagSet = ScriptUtils.getProperty(message, Constants.Property.ERROR_FLAG, false) as boolean
    if (errorFlagSet) {
        String errorMessage = ScriptUtils.getProperty(message, Constants.Property.ERROR_MESSAGE, '<No message available>') as String
        throw new DsiException(errorMessage)
    }
    return message
}

/**
 * Generic HTTP Response Handler for AWS
 * https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingRESTError.html
 * @param message
 * @return message
 */
Message handleHttpResponse(Message message) {
    Logger.init(messageLogFactory, message)

    // initialize error infos
    message.setProperty(Constants.Property.ERROR_FLAG, false)
    message.setProperty(Constants.Property.ERROR_CODE, 'NO_ERROR')
    message.setProperty(Constants.Property.ERROR_MESSAGE, '')

    Integer httpStatusCode = message.getHeader('CamelHttpResponseCode', Integer)

    // process error if not handled by iflow
    if (!hasCustomHttpStatusHandler(message)) {
        // handle error cases
        if (httpStatusCode < 200 || httpStatusCode > 299) {
            // mark as failed
            message.setProperty(Constants.Property.ERROR_FLAG, true)

            // log error location and state
            Logger.customProperty(Constants.Property.EXECUTION_STEP_NAME)
            Logger.customProperty(Constants.Property.EXECUTION_CHUNK_PART_NUMBER)

            // use http response for default error message
            String errorCode = httpStatusCode
            String errorMessage = message.getHeader('CamelHttpResponseText', String)

            // check whether we have a detailed error message in the body
            String body = message.getBody(String)
            if (ScriptUtils.isNotNullOrBlank(body)) {
                Logger.attach('AWS Response', body)

                try {
                    Node parseXML = new XmlParser().parseText(body)
                    errorCode = parseXML.Code.text() ?: httpStatusCode
                    errorMessage = parseXML.Message.text() ?: body
                    if (parseXML['Resource']) {
                        errorMessage += " (${parseXML.Resource.text()})"
                    }
                } catch (IOException | SAXException ex) {
                    Logger.debug('Error', 'Cannot read error response XML. ' + ex.getMessage())
                    errorMessage = body
                }
            }

            // prepare properties for the result json
            message.setProperty(Constants.Property.ERROR_CODE, errorCode)
            message.setProperty(Constants.Property.ERROR_MESSAGE, errorMessage)
            message.setProperty(Constants.Property.ERROR_HTTP_STATUS, httpStatusCode)

            // NOTE: do not throw an exception here, but in a following component, based on the error flag
        }
    }

    return message
}

/**
 * read and store the upload id extracted from the xml payload
 * @param message
 * @return message
 */
Message saveUploadId(Message message) {
    Logger.init(messageLogFactory, message)

    if (message.getBodySize() == 0) {
        throw new DsiException('Cannot get Upload Id, since the message payload is empty')
    }

    try {
        String body = message.getBody(String) ?: ''

        Node parseXML = new XmlParser().parseText(body)
        String uploadId = parseXML.UploadId.text()
        message.setProperty(Constants.Property.Aws.UPLOAD_ID, uploadId)
        Logger.customDebug(Constants.DisplayName.Aws.UPLOAD_ID, uploadId)
    } catch (SAXParseException ex) {
        throw new DsiException("Cannot get Upload Id. ${ex.getMessage()}")
    }

    return message
}

// add the current part id to the parts list in xml format
/**
 * After sending a part, add the etag and part number as XML to the AwsPartsIdList property
 * @param message
 * @return message
 */
Message savePartId(Message message) {
    Logger.init(messageLogFactory, message)

    String etag = message.getHeader('ETag', String)
    Long partNumber = message.getProperty(Constants.Property.EXECUTION_CHUNK_PART_NUMBER) as Long
    String formattedPartEntry = """<Part><PartNumber>$partNumber</PartNumber><ETag>$etag</ETag></Part>"""

    String currentList = message.getProperty(Constants.Property.Aws.PART_ID_LIST) ?: ''
    // empty default is important here
    message.setProperty(Constants.Property.Aws.PART_ID_LIST, currentList + formattedPartEntry)

    return message
}

/**
 * Log the final copy message from the target system as attachment
 * @param message
 * @return message
 */
Message saveFinishResponse(Message message) {
    Logger.init(messageLogFactory, message)
    String targetFilePath = message.getProperty(Constants.Property.TARGET_FILE_PATH)
    String copyResultLabel = """${Constants.DisplayName.Aws.COPY_RESULT} (${targetFilePath})"""
    Logger.attachDebug(copyResultLabel, message.getBody(String))
    return message
}

/**
 * handle certain http response situations
 * - 416 range error -> end of data on a chunkSize boundary
 * @param message
 * @return message
 */
Message handleEndOfData(Message message) {
    Logger.init(messageLogFactory, message)

    // just return if current http status has no special handling
    if (!hasCustomHttpStatusHandler(message)) {
        return message
    }

    // check whether we have a response payload
    if (message.getBodySize() == 0) {
        throw new DsiException('Read Error: cannot handle http response. Response payload is empty.')
    }

    String body = message.getBody(String)
    GPathResult result
    try {
        result = new XmlSlurper().parseText(message.getBody(java.io.Reader))
    } catch (IOException | SAXException ex) {
        throw new DsiException("Cannot parse response XML. ${ex.getMessage()}")
    }

    // if we have a range error, check if we have a EOD condition
    if (result['Code'] == 'InvalidRange') {
        String rangeRequested = result['RangeRequested'] as String
        String objectSize = result['ActualObjectSize'] as String

        String testString = 'bytes=' + objectSize + '-'

        if (rangeRequested.startsWith(testString)) {
            message.setProperty(Constants.Property.EXECUTION_FILE_PART_SIZE, 0)
            message.setBody(null)
            // we read directly on boundary, end of data reached
            return message
        }
    }

    throw new DsiException(body)
}

/**
 * calculate the next range header based on part counter and chunk size
 * @param message
 * @return message
 */
Message calculateRange(Message message) {
    Logger.init(messageLogFactory, message)

    Long partNumber = ScriptUtils.getMandatoryProperty(message, Constants.Property.EXECUTION_CHUNK_PART_NUMBER) as Long
    Long chunkSize = ScriptUtils.getMandatoryProperty(message, Constants.Property.FILE_CHUNK_SIZE) as Long

    // adjust chunk size to recommended size by aws ( minimal is 5 MIB)
    Long minimalAwsChunkSize = 8 * 1024 * 1024
    if (chunkSize < minimalAwsChunkSize) {
        chunkSize = minimalAwsChunkSize
        message.setProperty(Constants.Property.FILE_CHUNK_SIZE, chunkSize)
    }

    // calculate range header
    Long from = partNumber * chunkSize
    Long to = (partNumber + 1) * chunkSize - 1
    String range = 'bytes=' + from + '-' + to

    // set range header
    message.setHeader('Range', range)

    // increment part number
    message.setProperty(Constants.Property.EXECUTION_CHUNK_PART_NUMBER, partNumber + 1)

    // log range debug data
    Logger.debug(Constants.Property.EXECUTION_CHUNK_PART_NUMBER, partNumber)
    Logger.debug(Constants.Property.FILE_CHUNK_SIZE, chunkSize)
    Logger.debug('RangeFrom', from)
    Logger.debug('RangeTo', to)
    Logger.debug('Range', range)

    return message
}

/**
 * setup all properties as first
 * @param message
 * @return message
 */
Message prepareGetNextFileName(Message message) {
    Logger.init(messageLogFactory, message)

    // create SourceFolderPrefix property for filtering files in bucket
    String folderName = message.getProperty(Constants.Property.SOURCE_FOLDER) ?: ''
    String sourceFolderPrefix = ScriptUtils.cleanupPath(folderName, false, true)
    message.setProperty(Constants.Property.SOURCE_FOLDER_PREFIX, sourceFolderPrefix)

    Logger.logProperty(Constants.Property.SOURCE_FOLDER_PREFIX)

    return message
}

/**
 <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
 <Name>dsibucket-dev-provider</Name>
 <Prefix></Prefix>
 <NextContinuationToken>1Q4L3KNhqSk8bPuOOuXu4PJyjCzuS0jCnlASN4BS7dtJ4X3crHLmFfg==</NextContinuationToken>
 <KeyCount>1</KeyCount>
 <MaxKeys>1</MaxKeys>
 <IsTruncated>true</IsTruncated>
 <Contents>
 <Key>1g.dat</Key>
 <LastModified>2024-02-22T11:14:02.000Z</LastModified>
 <ETag>&quot;cd573cfaace07e7949bc0c46028904ff&quot;</ETag>
 <Size>1073741824</Size>
 <StorageClass>STANDARD</StorageClass>
 </Contents>
 </ListBucketResult>
 **/

Message processGetNextFileNameResponse(Message message) {
    Logger.init(messageLogFactory, message)

    String copyMode = message.getProperty(Constants.Property.COPY_MODE) ?: ''

    if (copyMode == Constants.Value.COPY_MODE_FOLDER) {
        // folder copy mode
        prepareFolderCopy(message)
    } else {
        // file copy mode
        prepareSingleFileCopy(message)
    }

    // remove aws response
    message.setBody(null)

    // log the properties for debugging
    Logger.logProperty(Constants.Property.FOLDER_COPY_CONTINUE_FLAG)
    Logger.logProperty(Constants.Property.Aws.PARAM_CONTINUATION)

    Logger.logProperty(Constants.Property.SOURCE_FILE_PATH)
    Logger.logProperty(Constants.Property.SOURCE_FILE_SIZE)
    Logger.logProperty(Constants.Property.TARGET_FILE_PATH)

    return message
}

private static void prepareSingleFileCopy(Message message) {
    String srcFolder = message.getProperty(Constants.Property.SOURCE_FOLDER) ?: ''
    String srcFile = message.getProperty(Constants.Property.SOURCE_FILE) ?: ''
    String srcPath = ScriptUtils.cleanupPath(srcFolder + '/' + srcFile)
    message.setProperty(Constants.Property.SOURCE_FILE_PATH, srcPath)

    String trgFolder = message.getProperty(Constants.Property.TARGET_FOLDER) ?: ''
    String trgFile = message.getProperty(Constants.Property.TARGET_FILE) ?: ''

    if (trgFile == '') {
        trgFile = srcFile
    }

    String trgPath = ScriptUtils.cleanupPath(trgFolder + '/' + trgFile)
    message.setProperty(Constants.Property.TARGET_FILE_PATH, trgPath)
}

private static void prepareFolderCopy(Message message) {
    // init properties
    message.setProperty(Constants.Property.FOLDER_COPY_CONTINUE_FLAG, false)
    message.setProperty(Constants.Property.Aws.PARAM_CONTINUATION, '')

    message.setProperty(Constants.Property.SOURCE_FILE_PATH, '')
    message.setProperty(Constants.Property.SOURCE_FILE_SIZE, '')
    message.setProperty(Constants.Property.TARGET_FILE_PATH, '')

    if (message.getBodySize() == 0) {
        throw new DsiException('GetFiles has no response payload')
    }

    GPathResult listBucketResult
    try {
        listBucketResult = new XmlSlurper().parseText(message.getBody(String))
    } catch (IOException | SAXException ex) {
        throw new DsiException("Cannot parse GetFiles response XML. ${ex.getMessage()}")
    }

    boolean isTruncated = Boolean.valueOf(listBucketResult.IsTruncated.text())
    int keyCount = Integer.valueOf(listBucketResult.KeyCount.text())

    if (isTruncated && keyCount > 0) {
        message.setProperty(Constants.Property.FOLDER_COPY_CONTINUE_FLAG, true)
    }

    if (keyCount > 0) {
        String continuationToken = listBucketResult.NextContinuationToken.text()
        continuationToken = URLEncoder.encode(continuationToken)
        message.setProperty(Constants.Property.Aws.PARAM_CONTINUATION, '&continuation-token=' + continuationToken)

        String srcKey = listBucketResult.Contents.Key
        message.setProperty(Constants.Property.SOURCE_FILE_PATH, ScriptUtils.cleanupPath(srcKey))
        message.setProperty(Constants.Property.SOURCE_FILE_SIZE, listBucketResult.Contents.Size)

        String srcFolder = ScriptUtils.cleanupPath(message.getProperty(Constants.Property.SOURCE_FOLDER) ?: '/', false, true)
        String trgFolder = message.getProperty(Constants.Property.TARGET_FOLDER)
        String relativeFileName = ScriptUtils.trimAtStart(srcKey, srcFolder)

        String trgPath = trgFolder + '/' + relativeFileName
        message.setProperty(Constants.Property.TARGET_FILE_PATH, ScriptUtils.cleanupPath(trgPath))
    }
}

/**
 * return whether the last component does handle the actual http status code
 * @param message
 * @param httpStatus actual http status code
 * @return result
 */
private static boolean hasCustomHttpStatusHandler(Message message) {
    // get a comma separated string of handled status codes
    String delimitedStatusCodes = message.getProperties().get(Constants.Property.Aws.HANDLED_HTTP_STATUS_LIST)
    if (ScriptUtils.isNullOrBlank(delimitedStatusCodes)) {
        return false
    }

    // get current http status code
    Integer httpStatusCode = message.getHeader('CamelHttpResponseCode', Integer)
    if (httpStatusCode == null) {
        return false
    }

    // reset status handler
    message.setProperty(Constants.Property.Aws.HANDLED_HTTP_STATUS_LIST, null)

    // create integer list from string
    List<Integer> httpStatusCodesHandled = []
    try {
        httpStatusCodesHandled = delimitedStatusCodes.split(',').collect { item -> Integer.valueOf(item.trim()) }
    } catch (NumberFormatException ex) {
        throw new DsiException("Property '${Constants.Property.Aws.HANDLED_HTTP_STATUS_LIST}' must contain comma separated integer values. ${ex.getMessage()}")
    }

    return httpStatusCode in httpStatusCodesHandled
}

/**
 * setup content type
 * - set default if content type is missing
 * - adjust body if content type is binary
 * @param message
 */
private static void handleContentType(Message message) {
    String contentType = message.getHeader('Content-Type', String)

    // set to "application/json" as default
    if (ScriptUtils.isNullOrBlank(contentType)) {
        message.setHeader('Content-Type', 'application/json')
    }

    // workaround for transfer-encoding=chunked which is not supported by aws api
    if (contentType == 'binary/octet-stream') {
        if (message.getBodySize() > 0) {
            // cast to byte[]
            byte[] data = message.getBody((byte[]).class)
            message.setBody(data)
        }
    }
}
