package top.meethigher.simple.startup.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.jar.JarFile;

/**
 * Logs application information on startup.
 *
 * @author Phillip Webb
 * @author Dave Syer
 */
public class StartupInfoLogger {


    /**
     * Provides access to the application home directory. Attempts to pick a sensible home for
     * both Jar Files, Exploded Archives and directly running applications.
     *
     * @author Phillip Webb
     * @author Raja Kolli
     * @since 2.0.0
     */
    public static class ApplicationHome {

        private final File source;

        private final File dir;

        /**
         * Create a new {@link ApplicationHome} instance.
         */
        public ApplicationHome() {
            this(null);
        }

        /**
         * Create a new {@link ApplicationHome} instance for the specified source class.
         *
         * @param sourceClass the source class or {@code null}
         */
        public ApplicationHome(Class<?> sourceClass) {
            this.source = findSource(sourceClass);
            this.dir = findHomeDir(this.source);
        }


        private File findSource(Class<?> sourceClass) {
            try {
                ProtectionDomain domain = (sourceClass != null) ? sourceClass.getProtectionDomain() : null;
                CodeSource codeSource = (domain != null) ? domain.getCodeSource() : null;
                URL location = (codeSource != null) ? codeSource.getLocation() : null;
                File source = (location != null) ? findSource(location) : null;
                if (source != null && source.exists() && !isUnitTest()) {
                    return source.getAbsoluteFile();
                }
            } catch (Exception ex) {
            }
            return null;
        }

        private boolean isUnitTest() {
            try {
                StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                for (int i = stackTrace.length - 1; i >= 0; i--) {
                    if (stackTrace[i].getClassName().startsWith("org.junit.")) {
                        return true;
                    }
                }
            } catch (Exception ex) {
            }
            return false;
        }

        private File findSource(URL location) throws IOException, URISyntaxException {
            URLConnection connection = location.openConnection();
            if (connection instanceof JarURLConnection) {
                return getRootJarFile(((JarURLConnection) connection).getJarFile());
            }
            return new File(location.toURI());
        }

        private File getRootJarFile(JarFile jarFile) {
            String name = jarFile.getName();
            int separator = name.indexOf("!/");
            if (separator > 0) {
                name = name.substring(0, separator);
            }
            return new File(name);
        }

        private File findHomeDir(File source) {
            File homeDir = source;
            homeDir = (homeDir != null) ? homeDir : findDefaultHomeDir();
            if (homeDir.isFile()) {
                homeDir = homeDir.getParentFile();
            }
            homeDir = homeDir.exists() ? homeDir : new File(".");
            return homeDir.getAbsoluteFile();
        }

        private File findDefaultHomeDir() {
            String userDir = System.getProperty("user.dir");
            return new File(userDir != null && userDir.length() > 0 ? userDir : ".");
        }

        /**
         * Returns the underlying source used to find the home directory. This is usually the
         * jar file or a directory. Can return {@code null} if the source cannot be
         * determined.
         *
         * @return the underlying source or {@code null}
         */
        public File getSource() {
            return this.source;
        }

        /**
         * Returns the application home directory.
         *
         * @return the home directory (never {@code null})
         */
        public File getDir() {
            return this.dir;
        }

        @Override
        public String toString() {
            return getDir().toString();
        }

    }


    public static class StopWatch {

        private long start;

        private long end;

        public void start() {
            this.start = System.currentTimeMillis();
        }

        public void stop() {
            this.end = System.currentTimeMillis();
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }

        public long getTotalTimeMillis() {
            return getEnd() - getStart();
        }

    }

    private static final Logger log = LoggerFactory.getLogger(StartupInfoLogger.class);

    private static final long HOST_NAME_RESOLVE_THRESHOLD = 200;

    private final Class<?> sourceClass;

    public StartupInfoLogger(Class<?> sourceClass) {
        this.sourceClass = sourceClass;
    }

    public String getCurrentPID() {
        return System.getProperty("PID");
    }

    public void logStarting(Logger log) {
        log.info(getStartingMessage());
    }

    public void logStarted(Logger log, StopWatch stopWatch) {
        log.info(getStartedMessage(stopWatch));
    }

    private String getStartingMessage() {
        StringBuilder message = new StringBuilder();
        message.append("Starting ");
        appendApplicationName(message);
        appendVersion(message, this.sourceClass);
        appendJavaVersion(message);
        appendOn(message);
        appendPid(message);
        appendContext(message);
        return message.toString();
    }


    private String getStartedMessage(StopWatch stopWatch) {
        StringBuilder message = new StringBuilder();
        message.append("Started ");
        appendApplicationName(message);
        message.append(" in ");
        message.append(stopWatch.getTotalTimeMillis() / 1000.0);
        message.append(" seconds");
        try {
            double uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0;
            message.append(" (JVM running for ").append(uptime).append(")");
        } catch (Throwable ex) {
            // No JVM time available
        }
        return message.toString();
    }

    private void appendApplicationName(StringBuilder message) {
        String name = (this.sourceClass != null) ? this.sourceClass.getSimpleName() : "application";
        message.append(name);
    }

    private void appendVersion(StringBuilder message, Class<?> source) {
        append(message, "v", source.getPackage().getImplementationVersion());
    }

    private void appendOn(StringBuilder message) {
        long startTime = System.currentTimeMillis();
        String hostName;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostName = "localhost";
        }
        append(message, "on ", hostName);
        long resolveTime = System.currentTimeMillis() - startTime;
        if (resolveTime > HOST_NAME_RESOLVE_THRESHOLD) {
            StringBuilder warning = new StringBuilder();
            warning.append("InetAddress.getLocalHost().getHostName() took ");
            warning.append(resolveTime);
            warning.append(" milliseconds to respond.");
            warning.append(" Please verify your network configuration");
            if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                warning.append(" (macOS machines may need to add entries to /etc/hosts)");
            }
            warning.append(".");
            log.warn(warning.toString());
        }
    }


    private void appendPid(StringBuilder message) {
        append(message, "with PID ", getCurrentPID());
    }

    private void appendContext(StringBuilder message) {
        StringBuilder context = new StringBuilder();
        ApplicationHome home = new ApplicationHome(this.sourceClass);
        if (home.getSource() != null) {
            context.append(home.getSource().getAbsolutePath());
        }
        append(context, "started by ", System.getProperty("user.name"));
        append(context, "in ", System.getProperty("user.dir"));
        if (context.length() > 0) {
            message.append(" (");
            message.append(context);
            message.append(")");
        }
    }

    private void appendJavaVersion(StringBuilder message) {
        append(message, "using Java ", System.getProperty("java.version"));
    }

    private void append(StringBuilder message, String prefix, String result) {
        append(message, prefix, result, "");
    }

    private void append(StringBuilder message, String prefix, String result, String defaultValue) {
        String value = (result != null && result.length() > 0) ? result : null;
        if (value == null) {
            value = defaultValue;
        }
        if (value != null && value.length() > 0) {
            message.append((message.length() > 0) ? " " : "");
            message.append(prefix);
            message.append(value);
        }
    }
}
