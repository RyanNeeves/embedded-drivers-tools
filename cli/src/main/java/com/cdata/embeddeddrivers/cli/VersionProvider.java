package com.cdata.embeddeddrivers.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import picocli.CommandLine.IVersionProvider;

/**
 * Reads the version from version.properties, which Maven filters at build
 * time so the jar always reports its true artifact version.
 */
public class VersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() {
        Properties props = new Properties();
        try (InputStream in = VersionProvider.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            // Fall through to defaults.
        }
        String version = props.getProperty("version", "dev");
        return new String[] {
                "cdrm " + version,
                "Java " + System.getProperty("java.version")
                        + " (" + System.getProperty("os.name")
                        + "/" + System.getProperty("os.arch") + ")"
        };
    }
}
