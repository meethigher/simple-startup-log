package top.meethigher.simple.startup.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

public abstract class SimpleApplication {

    public abstract void run() throws Exception;

    public String banner() throws Exception {
        return null;
    }

    public static void runApp(Class<? extends SimpleApplication> clazz,
                              String[] args) throws Exception {
        //PID的获取，要在所有log对象生成之前
        System.setProperty("PID", pid());
        StartupInfoLogger.StopWatch stopWatch = new StartupInfoLogger.StopWatch();
        StartupInfoLogger logger = new StartupInfoLogger(clazz);
        Logger startupLogger = LoggerFactory.getLogger(clazz);
        logger.logStarting(startupLogger);
        stopWatch.start();
        SimpleApplication application = clazz.newInstance();
        application.run();
        stopWatch.stop();
        logger.logStarted(startupLogger, stopWatch);
        String banner = application.banner();
        if (banner != null && banner.length() > 0) {
            System.out.println(banner);
        }
    }


    public static String pid() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        String name = runtimeMXBean.getName();
        return name.split("@")[0];
    }

}
