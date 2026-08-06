package com.figaf

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GroovyScriptsTest extends AbstractGroovyTest {

    @ParameterizedTest
    @ValueSource(strings = [
            "src/test/resources/test-data-files/script1/processData/test-data-1.json"
    ])
    void test_script1Groovy(String testDataFile) {
        String groovyScriptPath = "src/main/resources/script/script1.groovy"
        basicGroovyScriptTest(groovyScriptPath, testDataFile, "processData", getIgnoredKeysPrefixes(), getIgnoredKeys())
    }


    @Override
    List<String> getIgnoredKeys() {
        List<String> keys = super.getIgnoredKeys()
        keys.addAll(Arrays.asList("SAP_XDSR_SAPPASSPORT:exchange-property"))
        return keys
    }

}