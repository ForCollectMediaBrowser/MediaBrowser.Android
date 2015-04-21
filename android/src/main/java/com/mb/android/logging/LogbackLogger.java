package com.mb.android.logging;

import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Iterator;

import mediabrowser.model.logging.ILogger;
import mediabrowser.model.logging.LogSeverity;

/**
 * Created by Luke on 3/12/2015.
 */
public class LogbackLogger implements ILogger {

    private org.slf4j.Logger internalLogger;
    private String loggerName;
    private Marker marker;

    public LogbackLogger(Logger internalLogger, String loggerName) {
        this.internalLogger = internalLogger;
        this.loggerName = loggerName;
        this.marker = MarkerFactory.getMarker(loggerName);
    }

    @Override
    public void Info(String message, Object... paramList) {
        internalLogger.info(marker, String.format(message, paramList));
    }


    @Override
    public void Error(String message, Object... paramList) {
        internalLogger.error(marker, String.format(message, paramList));
    }


    @Override
    public void Warn(String message, Object... paramList) {
        internalLogger.warn(marker, String.format(message, paramList));
    }


    @Override
    public void Debug(String message, Object... paramList) {
        internalLogger.debug(marker, String.format(message, paramList));
    }


    @Override
    public void Fatal(String message, Object... paramList) {
        internalLogger.error(marker, String.format(message, paramList));
    }


    @Override
    public void FatalException(String message, Exception exception, Object... paramList) {
        logException(String.format(message, paramList), exception, LogSeverity.Fatal);
    }


    @Override
    public void ErrorException(String message, Exception exception, Object... paramList) {
        logException(String.format(message, paramList), exception, LogSeverity.Error);
    }

    private void logException(String message, Exception exception, LogSeverity severity) {

        try {
            message += "\r" + stackTraceToString(exception);
            if (exception.getCause() != null) {
                message += "caused by " + stackTraceToString(exception.getCause());
            }
        } catch (Exception e) {
            Error("FileLogger", "failed to parse exception");
        }

        internalLogger.error(marker, message);
    }

    private String stackTraceToString(Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName());
        sb.append("\r");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString());
            sb.append("\r");
        }
        return sb.toString();
    }
}
