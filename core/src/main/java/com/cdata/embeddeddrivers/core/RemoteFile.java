package com.cdata.embeddeddrivers.core;

/** An object in the OEM builds bucket. */
public record RemoteFile(String key, long size) {

    public String filename() {
        return key.substring(key.lastIndexOf('/') + 1);
    }
}
