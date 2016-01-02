package com.wouterbreukink.onedrive.logs;

import ch.qos.logback.core.PropertyDefinerBase;

import java.io.File;
import java.net.URISyntaxException;

/**
 * Created with IntelliJ IDEA.
 * User: Tony Tsang
 * Date: 2015-12-29
 * Time: 12:20
 */
public class LogPathDefiner extends PropertyDefinerBase {
    private String logPath;

    public LogPathDefiner() {
        try {
            // Derive log path from lib path
            logPath = new File(
                LogPathDefiner.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()
            ).getParent() + "/../logs";
        } catch (URISyntaxException e) {
            System.err.println("Unable to get program log path");
        }
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    @Override
    public String getPropertyValue() {
        return getLogPath();
    }
}
