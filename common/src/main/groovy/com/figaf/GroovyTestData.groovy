package com.figaf

/**
 * @author Arsenii Istlentev
 */
class GroovyTestData {

    MessageTestData input
    MessageTestData output

    MessageTestData getInput() {
        return input
    }

    void setInput(MessageTestData input) {
        this.input = input
    }

    MessageTestData getOutput() {
        return output
    }

    void setOutput(MessageTestData output) {
        this.output = output
    }


    @Override
    String toString() {
        return "GroovyTestData{" +
            "input=" + input +
            ", output=" + output +
            '}';
    }
}
