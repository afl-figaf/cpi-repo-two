package script
//TODO mark strings that reference properties in IFlows for automatic checking
class Constants {

    final static String DUMMY_ENTRY = "Just ignore me. I'm just a dummy entry."

    static class Property {

        // a caught camel exception
        final static String CAMEL_EXCEPTION_CAUGHT = 'CamelExceptionCaught'
        final static String CAMEL_RESPONSE_CODE = 'CamelHttpResponseCode'

        // set the custom status for the message
        final static String MESSAGE_LOG_CUSTOM_STATUS = 'SAP_MessageProcessingLogCustomStatus'
        // current log level
        final static String MESSAGE_LOG_LOG_LEVEL = 'SAP_MPL_LogLevel_Overall'

        // boolean flag indicating an error condition
        final static String ERROR_FLAG = 'ErrorCopyFlag'
        // http response status code or exception name
        final static String ERROR_CODE = 'ErrorCopyCode'
        // the detailed error message
        final static String ERROR_MESSAGE = 'ErrorCopyMessage'
        // the http response status code to be returned to the dataplane endpoint caller
        final static String ERROR_HTTP_STATUS = 'ErrorCopyResponseStatus'
        // name of the current execution step
        final static String EXECUTION_STEP_NAME = 'CopyStepName'
        // looping flag for more files in folder copy
        final static String FOLDER_COPY_CONTINUE_FLAG = 'CopyFolderContinue'

        final static String SOURCE_CREDENTIAL = "SourceCredentialAlias"
        final static String SOURCE_SESSION_TOKEN = "SourceSessionToken"

        final static String TARGET_CREDENTIAL = "TargetCredentialAlias"
        final static String TARGET_SESSION_TOKEN = "TargetSessionToken"

        final static String SOURCE_FOLDER = 'SourceFolder'
        final static String SOURCE_FILE = 'SourceFile'
        final static String TARGET_FOLDER = 'TargetFolder'
        final static String TARGET_FILE = 'TargetFile'

        final static String SOURCE_FILE_PATH = 'SourceFilePath'
        final static String SOURCE_FILE_SIZE = 'SourceFileSize'

        final static String TARGET_FILE_PATH = 'TargetFilePath'

        // the copy mode, file or folder copy
        final static String COPY_MODE = 'CopyMode'

        // number of bytes to read for each chunk
        final static String FILE_CHUNK_SIZE = 'CopyChunkSize'

        // endpoint for automated testing
        final static String TEST_ENDPOINT = 'TestEndpoint'
        final static String TEST_AUTHORIZATION = 'TestAuthorization'

        // beginning part of the path that has to be stripped
        final static String SOURCE_FOLDER_PREFIX = 'SourceFolderPrefix'

        // current chunk part number
        final static String EXECUTION_CHUNK_PART_NUMBER = 'CopyPartNumber'
        // file part size
        final static String EXECUTION_FILE_PART_SIZE = 'CopyPartBytesRead'
        // number if bytes read for the current file
        final static String EXECUTION_FILE_BYTES_READ = 'CopyFileBytesRead'

        // mode of execution SYNC or ASYNC
        final static String EXEC_MODE = 'executionMode'
        // the callback URL for the status update
        final static String CALLBACK_URL = 'callbackUrl'
        // the transfer process ID for the status update
        final static String TRANSFER_PROCESS_ID = 'transferProcessId'
        // the url for the status update
        final static String DYNAMIC_CALLBACK_URL = 'dynamicCallbackUrl'
        // the credential alias for the status update
        final static String DYNAMIC_CALLBACK_CREDENTIAL_ALIAS = 'dynamicCallbackCredentialAlias'

        static class Aws {

            final static String HTTP_METHOD = 'AwsHttpMethod'
            final static String HTTP_HOST = 'AwsHttpHost'
            final static String HTTP_PATH = 'AwsHttpPath'
            final static String HTTP_QUERY = 'AwsHttpQuery'
            final static String HTTP_SERVICE = 'AwsService'
            final static String HTTP_REGION = 'AwsRegion'
            final static String HTTP_CREDENTIAL_ALIAS = 'AwsCredentialAlias'
            final static String HTTP_REQUEST_HEADERS = 'AwsRequestHeaders'
            final static String HTTP_URL = 'AwsHttpUrl'
            final static String HTTP_SESSION_TOKEN = 'AwsSessionToken'

            // upload id for multi part upload
            final static String UPLOAD_ID = 'UploadId'
            // xml list of part IDs
            final static String PART_ID_LIST = 'AwsPartsIdList'
            // comma separated list of http status numbers that should not be handled as error
            final static String HANDLED_HTTP_STATUS_LIST = 'AwsCanHandleHttpStatus'

            final static String PARAM_CONTINUATION = 'CopyFolderContinuationParameter'

        }

    }

    static class DisplayName {
        //private constructor
        private DisplayName(){}

        static class Aws {

            final static String ACCESS_KEY = 'AWS AccessKey'
            final static String UPLOAD_ID = 'AWS Upload Id'
            final static String COPY_RESULT = 'Copy Result'

        }

    }

    static class Value {

        final static String COPY_MODE_FILE = 'FILE'
        final static String COPY_MODE_FOLDER = 'FOLDER'

        final static String MESSAGE_LOG_CUSTOM_STATUS_FAILED = 'FAILED'

    }

    static class Header {

        final static String HTTP_AUTHORIZATION = 'Authorization'

        static class Aws {

            final static String HTTP_SESSION_TOKEN = 'x-amz-security-token'

        }

    }

}
