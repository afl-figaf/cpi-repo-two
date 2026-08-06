package com.figaf

import com.sap.gateway.ip.core.customdev.util.AttachmentWrapper
import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.gateway.ip.core.customdev.util.SoapHeader
import org.apache.camel.Attachment

import javax.activation.DataHandler

class MessageImpl implements Message {

    Map<String, Object> headers = new HashMap<>()
    Map<String, Object> properties = new HashMap<>()
    Map<String, DataHandler> dataHandlerMap = new HashMap<>()
    List<SoapHeader> soapHeaders = new ArrayList<>()
    Object body;


    @Override
    def <T> T getBody(Class<T> aClass) {
        return (T) body
    }

    @Override
    Object getBody() {
        return body
    }

    @Override
    void setBody(Object o) {
        this.body = o
    }

    @Override
    Map<String, DataHandler> getAttachments() {
        return dataHandlerMap
    }

    @Override
    void setAttachments(Map<String, DataHandler> map) {
        this.dataHandlerMap = map
    }

    @Override
    Map<String, Object> getHeaders() {
        return this.headers
    }

    @Override
    def <T> T getHeader(String s, Class<T> aClass) {
        return (T) this.dataHandlerMap.get(s)
    }

    @Override
    void setHeaders(Map<String, Object> map) {
        this.headers = map
    }

    @Override
    void setHeader(String s, Object o) {
        this.headers.put(s, o)
    }

    @Override
    Map<String, Object> getProperties() {
        return this.properties
    }

    @Override
    void setProperties(Map<String, Object> map) {
        this.properties = map
    }

    //TODO
    @Override
    long getBodySize() {
        return 0
    }

    //TODO
    @Override
    long getAttachmentsSize() {
        return 0
    }

    @Override
    void addAttachmentHeader(String s, String s1, AttachmentWrapper attachmentWrapper) {
        attachmentWrapper.addHeader(s, s1)
    }

    @Override
    void setAttachmentHeader(String s, String s1, AttachmentWrapper attachmentWrapper) {
        attachmentWrapper.setHeader(s, s1)
    }

    @Override
    String getAttachmentHeader(String s, AttachmentWrapper attachmentWrapper) {
        return attachmentWrapper.getHeader(s)
    }

    @Override
    void removeAttachmentHeader(String s, AttachmentWrapper attachmentWrapper) {
        attachmentWrapper.removeHeader(s)
    }

    @Override
    Map<String, AttachmentWrapper> getAttachmentWrapperObjects() {
        Map<String, AttachmentWrapper> attachmentWrapperMap = new HashMap<>();
        for (Map.Entry<String, DataHandler> entry : this.dataHandlerMap.entrySet()) {
            attachmentWrapperMap.put(entry.getKey(), new AttachmentWrapper(entry.getValue()));
        }
        return attachmentWrapperMap;
    }

    @Override
    void setAttachmentWrapperObjects(Map<String, AttachmentWrapper> map) {
        this.dataHandlerMap = new HashMap<>()
        for (Map.Entry<String, AttachmentWrapper> entry: map.entrySet()) {
            this.dataHandlerMap.put(entry.getKey(), entry.getValue().getDataHandler())
        }
    }

    @Override
    void addAttachmentObject(String s, AttachmentWrapper attachmentWrapper) {
        this.dataHandlerMap.put(s, attachmentWrapper.getDataHandler())
    }

    @Override
    void addAttachmentHeader(String s, String s1, Attachment attachment) {
        attachment.addHeader(s, s1)
    }

    @Override
    void setAttachmentHeader(String s, String s1, Attachment attachment) {
        attachment.setHeader(s, s1)
    }

    @Override
    String getAttachmentHeader(String s, Attachment attachment) {
        return attachment.getHeader(s)
    }

    @Override
    void removeAttachmentHeader(String s, Attachment attachment) {
        attachment.removeHeader(s)
    }

    @Override
    Map<String, Attachment> getAttachmentObjects() {
        Map<String, Attachment> attachmentMap = new HashMap<>();
        for (Map.Entry<String, DataHandler> entry : this.dataHandlerMap.entrySet()) {
            attachmentMap.put(entry.getKey(), new AttachmentWrapper(entry.getValue()));
        }
        return attachmentMap;
    }

    @Override
    void setAttachmentObjects(Map<String, Attachment> map) {
        this.dataHandlerMap = new HashMap<>()
        for (Map.Entry<String, Attachment> entry: map.entrySet()) {
            this.dataHandlerMap.put(entry.getKey(), entry.getValue().getDataHandler())
        }
    }

    @Override
    void addAttachmentObject(String s, Attachment attachment) {
        this.dataHandlerMap.put(s, attachment.getDataHandler())
    }

    @Override
    Object getProperty(String propertyName) {
        return this.properties.get(propertyName)
    }

    @Override
    void setProperty(String propertyName, Object newValue) {
        this.properties.put(propertyName, newValue)
    }

    @Override
    List<SoapHeader> getSoapHeaders() {
        return this.soapHeaders
    }

    @Override
    void setSoapHeaders(List<SoapHeader> soapHeaders) {
        this.soapHeaders = soapHeaders
    }

    @Override
    void clearSoapHeaders() {
        this.soapHeaders.clear()
    }
}