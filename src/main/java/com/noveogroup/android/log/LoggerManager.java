/*
 * Copyright (c) 2013 Noveo Group
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Except as contained in this notice, the name(s) of the above copyright holders
 * shall not be used in advertising or otherwise to promote the sale, use or
 * other dealings in this Software without prior written authorization.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.noveogroup.android.log;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * THe logger manager.
 * <p/>
 * To configure this logger manager you can include an
 * {@code android-logger.properties} file in src directory.
 * The format of configuration file is:
 * <pre>
 * # root logger configuration
 * root=&lt;level&gt;:&lt;tag&gt;
 * # package / class logger configuration
 * logger.&lt;package or class name&gt;=&lt;level&gt;:&lt;tag&gt;
 * </pre>
 * You can use values of {@link Logger.Level} enum as level constants.
 * For example, the following configuration will
 * log all ERROR messages with tag "MyApplication" and all
 * messages from classes {@code com.example.server.*} with
 * tag "MyApplication-server":
 * <pre>
 * root=ERROR:MyApplication
 * logger.com.example.server=DEBUG:MyApplication-server
 * </pre>
 * <p/>
 */
public final class LoggerManager {

    private LoggerManager() {
        throw new UnsupportedOperationException();
    }

    private static final Logger DEFAULT_LOGGER = new SimpleLogger("XXX", Logger.Level.VERBOSE);

    private static final String PROPERTIES_NAME = "android-logger.properties";
    private static final String CONF_ROOT = "root";
    private static final String CONF_LOGGER = "logger.";
    private static final String CONF_LOGCAT = "check-logcat-filter";
    private static final Pattern CONF_LOGGER_REGEX = Pattern.compile("(.*?):(.*)");
    private static final Logger.Level CONF_DEFAULT_LEVEL = Logger.Level.VERBOSE;
    private static final Map<String, Logger> loggerMap;

    private static boolean checkIsLoggable = true;

    private static void loadProperties(Properties properties) throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = LoggerManager.class.getClassLoader().getResourceAsStream(PROPERTIES_NAME);
            if (inputStream != null) {
                properties.load(inputStream);
            } else {
                inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(PROPERTIES_NAME);
                if (inputStream != null) {
                    properties.load(inputStream);
                }
            }
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private static Logger decodeLogger(String loggerString) {
        Matcher matcher = CONF_LOGGER_REGEX.matcher(loggerString);
        if (matcher.matches()) {
            String levelString = matcher.group(1);
            String tag = matcher.group(2);
            try {
                return new SimpleLogger(tag, Logger.Level.valueOf(levelString), checkIsLoggable);
            } catch (IllegalArgumentException e) {
                DEFAULT_LOGGER.w(String.format("Cannot parse %s as logging level. Only %s are allowed",
                        levelString, Arrays.toString(Logger.Level.values())));
                return new SimpleLogger(loggerString, CONF_DEFAULT_LEVEL, checkIsLoggable);
            }
        } else {
            return new SimpleLogger(loggerString, CONF_DEFAULT_LEVEL, checkIsLoggable);
        }
    }

    private static Map<String, Logger> loadConfiguration() {
        Map<String, Logger> loggerMap = new HashMap<String, Logger>();

        Properties properties = new Properties();
        try {
            loadProperties(properties);
        } catch (IOException e) {
            DEFAULT_LOGGER.e(String.format("Cannot configure logger from %s. Default configuration will be used", PROPERTIES_NAME), e);
            loggerMap.put(null, DEFAULT_LOGGER);
            return loggerMap;
        }

        if (properties.stringPropertyNames().isEmpty()) {
            DEFAULT_LOGGER.e("Logger configuration file is empty. Default configuration will be used");
            loggerMap.put(null, DEFAULT_LOGGER);
            return loggerMap;
        }

        // load configuration properties first, they needed to configure loggers
        if (properties.getProperty(CONF_LOGCAT) != null) {
            checkIsLoggable = Boolean.valueOf(properties.getProperty(CONF_LOGCAT));
        }

        for (String propertyName : properties.stringPropertyNames()) {
            String propertyValue = properties.getProperty(propertyName);

            if (propertyName.equals(CONF_ROOT)) {
                loggerMap.put(null, decodeLogger(propertyValue));
            }
            if (propertyName.startsWith(CONF_LOGGER)) {
                String loggerName = propertyName.substring(CONF_LOGGER.length());
                loggerMap.put(loggerName, decodeLogger(propertyValue));
            }
        }
        return loggerMap;
    }


    static {
        loggerMap = Collections.unmodifiableMap(loadConfiguration());
    }

    private static Logger findLogger(String name) {
        String currentKey = null;
        if (name != null) {
            for (String key : loggerMap.keySet()) {
                if (key != null && name.startsWith(key)) {
                    if (currentKey == null || currentKey.length() < key.length()) {
                        currentKey = key;
                    }
                }
            }
        }
        Logger logger = loggerMap.get(currentKey);
        return logger != null ? logger : new SimpleLogger("", Logger.Level.VERBOSE, checkIsLoggable);
    }

    /**
     * Root logger that has {@code null} as a name.
     */
    public static final Logger ROOT = getLogger((String) null);

    /**
     * Returns logger corresponding to the specified class.
     *
     * @param aClass the class.
     * @return the {@link Logger} implementation.
     */
    public static Logger getLogger(Class<?> aClass) {
        return findLogger(aClass == null ? null : aClass.getName());
    }

    /**
     * Returns logger corresponding to the specified name.
     *
     * @param name the name.
     * @return the {@link Logger} implementation.
     */
    public static Logger getLogger(String name) {
        return findLogger(name);
    }

    /**
     * Returns logger corresponding to the caller class.
     *
     * @return the {@link Logger} implementation.
     */
    public static Logger getLogger() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = 1; i < stackTrace.length; i++) {
            StackTraceElement stackTraceElement = stackTrace[i];
            if (!stackTraceElement.getClassName().startsWith("com.noveogroup.android.log")) {
                return findLogger(stackTraceElement.getClassName());
            }
        }
        return findLogger(null);
    }

}
