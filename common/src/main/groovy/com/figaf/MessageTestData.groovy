package com.figaf

/**
 * @author Arsenii Istlentev
 */
class MessageTestData {

    Map<String, Object> headers
    Map<String, Object> properties
    Object body

    Map<String, Object> getHeaders() {
        return headers
    }

    void setHeaders(Map<String, Object> headers) {
        this.headers = headers
    }

    Map<String, Object> getProperties() {
        return properties
    }

    void setProperties(Map<String, Object> properties) {
        this.properties = properties
    }

    Object getBody() {
        return body
    }

    void setBody(Object body) {
        this.body = body
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        MessageTestData that = (MessageTestData) o

        if (body != that.body) return false
        if (headers != that.headers) return false
        if (properties != that.properties) return false

        return true
    }

    int hashCode() {
        int result
        result = (headers != null ? headers.hashCode() : 0)
        result = 31 * result + (properties != null ? properties.hashCode() : 0)
        result = 31 * result + (body != null ? body.hashCode() : 0)
        return result
    }

    @Override
    String toString() {
        return "MessageData{" +
            "headers=" + headers +
            ", properties=" + properties +
            ", body=" + body +
            '}';
    }
}
