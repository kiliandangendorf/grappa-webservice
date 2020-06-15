package de.hsh.grappa.application;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.hsh.grappa.cache.RedisController;
import de.hsh.grappa.config.GrappaConfig;
import de.hsh.grappa.service.GraderPoolManager;
import de.hsh.grappa.service.GradingEnvironmentSetup;
import de.hsh.grappa.utils.ClassLoaderHelper;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.io.File;

@WebListener
public class GrappaServlet implements ServletContextListener {
    //public static RedisClient redisClient;
    public static RedisController redis;
    public static GrappaConfig CONFIG;

    private static Logger log = LoggerFactory.getLogger(GrappaServlet.class);
    public static final String CONFIG_FILENAME_PATH = "/etc/grappa/grappa-config.yaml";

    //public static GraderWorkersManager graderWorkersManager;
    private Thread t;

    @Override
    public void contextInitialized(ServletContextEvent ctxEvent) {
        try {
            log.info("Context path: {}", ctxEvent.getServletContext().getContextPath());
            readConfigFile();
            setupRedisConnection();
            loadGradingEnvironmentSetups();
            //graderWorkersManager = new GraderWorkersManager(CONFIG.getGraders());
            GraderPoolManager.getInstance().init(CONFIG.getGraders());
            //t = new Thread(graderWorkersManager);
            t = new Thread(GraderPoolManager.getInstance());
            t.start();
        } catch (Exception e) {
            log.error("Error during webservice initialization.");
            log.error(e.getMessage());
            log.error(ExceptionUtils.getStackTrace(e));
            //throw e; // make the webservice shutdown
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent ctxEvent) {
        try {
            GraderPoolManager.getInstance().stopStartingNewGradingProcesses();
            t.interrupt();
            redis.shutdown();
        } catch (Exception e) {
            log.error("Error during webservice deinitialization.");
            log.error(e.getMessage());
            log.error(ExceptionUtils.getStackTrace(e));
        }
    }

    public void setLoggingLevel(Level level) {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("de.hsh.grappa");
        root.setLevel(level);
        root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
            .getLogger("root");
        root.setLevel(level);
    }

    private void readConfigFile() {
        log.info("Loading config file '{}'...", CONFIG_FILENAME_PATH);
        try {
            var mapper = new ObjectMapper(new YAMLFactory());
            var configFile = new File(CONFIG_FILENAME_PATH);
            CONFIG = mapper.readValue(configFile, GrappaConfig.class);
            setLoggingLevel(Level.toLevel(CONFIG.getService().getLogging_level()));
            log.info("Config file loaded: {}", CONFIG.toString());
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    private void setupRedisConnection() throws Exception {
        redis = new RedisController(CONFIG.getCache());
        redis.init();
        log.info("Testing redis connection...");
        if (redis.ping()) {
            log.info("Redis connection established");
        } else {
            log.error("Redis connection could not be established.");
            // TODO: System.exit(-1) shut down service
            // but then again, the connection could be established at
            // a later time... like when someone does systemctl start redis
        }
    }

    private void loadGradingEnvironmentSetups() {
        GradingEnvironmentSetup grdEnv = null;
        try {
            grdEnv =
                new ClassLoaderHelper<GradingEnvironmentSetup>().LoadClass(CONFIG.getService().getDefault_grading_environment_setup_class_path(), CONFIG.getService().getDefault_grading_environment_setup_class_name(), GradingEnvironmentSetup.class);
        } catch (Exception e) {
            log.error("Failed to load jar file");
            log.error(e.getMessage());
            log.error(ExceptionUtils.getStackTrace(e));
        }

//        grdEnv.init(null);
//        try {
//            grdEnv.setup();
//        } catch(Exception e) {
//          log.error(e.getMessage());
//          log.error(ExceptionUtils.getStackTrace(e));
//        }

    }
}