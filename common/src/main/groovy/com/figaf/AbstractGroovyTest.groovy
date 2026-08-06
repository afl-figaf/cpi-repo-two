package com.figaf

import com.sap.it.api.msglog.MessageLog
import com.sap.it.api.msglog.MessageLogFactory
import groovy.json.JsonSlurper
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.function.Executable
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

import static org.assertj.core.api.Assertions.assertThat
import static org.junit.jupiter.api.Assertions.assertAll
import static org.mockito.ArgumentMatchers.any
import static org.mockito.BDDMockito.given

/**
 * @author Arsenii Istlentev
 */
@ExtendWith(MockitoExtension)
abstract class AbstractGroovyTest {

    public static final List<String> DEFAULT_IGNORED_KEYS_PREFIXES = [
        "http://sap.com/xi/XI/Message/30" ,
        "http://sap.com/xi/XI/System" ,
        "SAP_TRACE_HEADER_" ,
        "Camel" ,
        "StatusProvider_ID" ,
        "SAP_Auth" ,
        "SAP_MessageProcessing" ,
        "SAP_Monitoring"
    ]

    public static final List<String> DEFAULT_IGNORED_KEYS = [
        "breadcrumbId",
        "SAP_MessageProcessingLogID",
        "IRTSAVE",
        "SAP_MplCorrelationId",
        "SAP_PregeneratedMplId",
        "SAP-PASSPORT"
    ]

    static GroovyShell shell = null
    static jsonSlurper = null

    @Mock(lenient = true)
    MessageLogFactory messageLogFactory

    @Mock
    MessageLog messageLog

    @BeforeAll
    static void setUp() {
        shell = new GroovyShell()
        jsonSlurper = new JsonSlurper()
    }

    static boolean checkIfKeyNeedsToBeIgnored(String key, List<String> ignoredPropertyPrefixes, List<String> ignoredProperties) {
        boolean needsToBeIgnored = ignoredPropertyPrefixes.any { ignoredPrefix ->
            key.startsWith(ignoredPrefix)
        }
        needsToBeIgnored = needsToBeIgnored || ignoredProperties.contains(key)
        return needsToBeIgnored
    }

    List<String> getIgnoredKeysPrefixes() {
        return DEFAULT_IGNORED_KEYS_PREFIXES
    }

    List<String> getIgnoredKeys() {
        return DEFAULT_IGNORED_KEYS
    }

    void setExternalPropertiesToScriptBeforeRunning(Script script) {
        script.setProperty("messageLogFactory", messageLogFactory)
    }

    void initMessageLogFactoryMocks() {

        given(messageLogFactory.getMessageLog(any()))
            .willReturn(messageLog)
    }

    void basicGroovyScriptTest(String groovyScriptPath, String testDataFilePath, String methodName, List<String> ignoredKeysPrefixes, List<String> ignoredKeys) {
        initMessageLogFactoryMocks()
        def (MessageTestData messageDataExpected, MessageTestData messageDataActual) = processMessageData(groovyScriptPath, testDataFilePath, methodName)
        assertMessages(messageDataExpected, messageDataActual, ignoredKeysPrefixes, ignoredKeys)
    }

    List processMessageData(String groovyScriptPath, String testDataFilePath, String methodName) {
        GroovyTestData groovyTestData = jsonSlurper.parseText(new File(testDataFilePath).text)
        MessageTestData messageDataInput = groovyTestData.getInput()
        MessageTestData messageDataExpected = groovyTestData.getOutput()

        MessageImpl message = new MessageImpl()
        message.setHeaders(messageDataInput.getHeaders())
        message.setProperties(messageDataInput.getProperties())
        message.setBody(mapBody(messageDataInput.getBody()))

        Script script = shell.parse(new File(groovyScriptPath))
        setExternalPropertiesToScriptBeforeRunning(script)
        script."$methodName"(message)

        MessageTestData messageDataActual = new MessageTestData()
        messageDataActual.setHeaders(message.getHeaders())
        messageDataActual.setProperties(message.getProperties())
        messageDataActual.setBody(message.getBody())
        [messageDataExpected, messageDataActual]
    }

    void assertMessages(MessageTestData messageDataExpected, MessageTestData messageDataActual, List<String> ignoredPropertyPrefixes, List<String> ignoredProperties) {
        List<Executable> assertions = new ArrayList<>()

        Set<String> headersKeySet = new HashSet<>()
        headersKeySet.addAll(messageDataExpected.getHeaders().keySet())
        headersKeySet.addAll(messageDataActual.getHeaders().keySet())
        headersKeySet.each { String headerKey ->
            boolean needsToBeIgnored = checkIfKeyNeedsToBeIgnored(headerKey, ignoredPropertyPrefixes, ignoredProperties)
            if (!needsToBeIgnored) {
                assertions.add({
                    String expectedValue = messageDataExpected.getHeaders().get(headerKey)
                    String actualValue = messageDataActual.getHeaders().get(headerKey)
                    assertThat(actualValue)
                        .overridingErrorMessage("Header with key '%s' has value '%s', but '%s' was expected", headerKey, actualValue, expectedValue)
                        .isEqualTo(expectedValue)
                } as Executable)
            }
        }

        Set<String> propertiesKeySet = new HashSet<>()
        propertiesKeySet.addAll(messageDataExpected.getProperties().keySet())
        propertiesKeySet.addAll(messageDataActual.getProperties().keySet())
        propertiesKeySet.each { String propertyKey ->
            boolean needsToBeIgnored = checkIfKeyNeedsToBeIgnored(propertyKey, ignoredPropertyPrefixes, ignoredProperties)
            if (!needsToBeIgnored) {
                assertions.add({
                    String expectedValue = messageDataExpected.getProperties().get(propertyKey)
                    String actualValue = messageDataActual.getProperties().get(propertyKey)
                    assertThat(actualValue)
                        .overridingErrorMessage("Property with key '%s' has value '%s', but '%s' was expected", propertyKey, actualValue, expectedValue)
                        .isEqualTo(expectedValue)
                } as Executable)
            }
        }

        assertions.add({
            assertThat(messageDataActual.getBody()).isEqualTo(messageDataExpected.getBody())
        } as Executable)

        assertAll(assertions)
    }

    Object mapBody(Object stringifiedBody) {
        return stringifiedBody;
    }
}
