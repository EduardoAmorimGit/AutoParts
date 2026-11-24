package com.autoparts_maven.autoparts_maven.returns;

/** Published inside the transaction that changed the return request (drives customer e-mails). */
public record ReturnEvent(Type type, Long returnRequestId) {

    public enum Type {
        REQUESTED,
        APPROVED,
        RECEIVED,
        REPLACED,
        REJECTED
    }
}
