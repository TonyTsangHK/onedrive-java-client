package com.wouterbreukink.onedrive;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.text.DecimalFormat;

public class LogUtils {

    private LogUtils() {}

    public static String readableFileSize(double size) {
        return readableFileSize((long) size);
    }

    public static String readableFileSize(long size) {
        if (size <= 0) return "0";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static String readableTime(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        } else if (ms < 60000) {
            return String.format("%.1fs", ms / 1000d);
        } else {
            long seconds = ms / 1000;
            long s = seconds % 60;
            long m = (seconds / 60) % 60;
            long h = (seconds / (60 * 60)) % 24;
            return String.format("%02d:%02d:%02d", h, m, s);
        }
    }

    public static String addFileLogger(String logFile) {
        boolean hasLog4jConfig = Main.class.getResourceAsStream("/log4j.xml") != null,
                hasLogbackConfig = Main.class.getResourceAsStream("/logback.xml") != null;

        if (hasLogbackConfig || !hasLog4jConfig) {
            return addFileToLogbackLogger(logFile);
        } else {
            return addFileToLog4jLogger(logFile);
        }
    }

    public static String addFileToLogbackLogger(String logFile) {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();

        // Create a new file appender for the given filename
        FileAppender fileAppender = new FileAppender();
        fileAppender.setContext(ctx);
        fileAppender.setFile(logFile);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        // Default log pattern layout
        encoder.setPattern("[%d{yyyy-MM-dd HH:mm:ss}] [%thread] %-5level %logger - %msg%n");
        encoder.setContext(ctx);
        encoder.start();

        fileAppender.setEncoder(encoder);
        fileAppender.start();

        Logger rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

        ((ch.qos.logback.classic.Logger)rootLogger).addAppender(fileAppender);

        return fileAppender.getFile();
    }

    public static String addFileToLog4jLogger(String logFile) {
        org.apache.logging.log4j.core.LoggerContext ctx =
                (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        // Default log layout
        Layout<? extends Serializable> layout =
                PatternLayout.createLayout("%d %p [%t] %m%n", null, null, null, true, true, null, null);

        // Create a new file appender for the given filename
        org.apache.logging.log4j.core.appender.FileAppender appender =
                org.apache.logging.log4j.core.appender.FileAppender.createAppender(
                    logFile,
                    "false",
                    "false",
                    "FileAppender",
                    "false",
                    "true",
                    "true",
                    null,
                    layout,
                    null,
                    null,
                    null,
                    config
                );

        appender.start();
        ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).addAppender(appender);

        return appender.getFileName();
    }
}
