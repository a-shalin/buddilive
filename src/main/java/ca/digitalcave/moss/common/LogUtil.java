package ca.digitalcave.moss.common;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LogUtil {

	/**
	 * Sets the Java Logger log level as specified for the given logger.  The logger name
	 * is a hierarchal name, generally which follows package naming.  Unless overridden
	 * at a lower level, the logger name will apply to all children as well; for instance,
	 * setting a given level for "org.homeunix.thecave" will also apply to logger 
	 * "org.thecave.homeunix.moss.common.LogUtil". 
	 * @param loggerName Name of the logger to set
	 * @param logLevel The log level to set; all logs at this level or higher will be shown.  
	 */
	public static void setLogLevel(String loggerName, String logLevel, Level defaultLevel){
		Level level;
		if ("SEVERE".equalsIgnoreCase(logLevel)){
			level = Level.SEVERE;
		}
		else if ("WARNING".equalsIgnoreCase(logLevel)){
			level = Level.WARNING;
		} 
		else if ("INFO".equalsIgnoreCase(logLevel)){
			level = Level.INFO;
		}
		else if ("CONFIG".equalsIgnoreCase(logLevel)){
			level = Level.CONFIG;
		} 
		else if ("FINE".equalsIgnoreCase(logLevel)){
			level = Level.FINE;
		} 
		else if ("FINER".equalsIgnoreCase(logLevel)){
			level = Level.FINER;
		} 
		else if ("FINEST".equalsIgnoreCase(logLevel)){
			level = Level.FINEST;
		}
		else if ("ALL".equalsIgnoreCase(logLevel)){
			level = Level.FINEST;
		} 
		else {
			level = defaultLevel;
		}
		
		Logger logger = Logger.getLogger(loggerName);
		logger.setLevel(level);
		setHandlerLevelRecursively(level, logger);
	}
	
	private static void setHandlerLevelRecursively(Level level, Logger logger){
		if (logger == null)
			return;
		
		for (Handler handler : logger.getHandlers()){
			handler.setLevel(level);
		}
		
		setHandlerLevelRecursively(level, logger.getParent());
	}
	
	/**
	 * Helper class which sets the log level for all thecave loggers ("org.homeunix.thecave",
	 * "ca.digitalcave", and children) to the given log value, defaulting to Level.INFO if 
	 * the logLevel string is not a valid log level.
	 * @param logLevel Log level to set; one of SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST.
	 */
	public static void setLogLevel(String logLevel){
		setLogLevel("org.homeunix.thecave", logLevel, Level.INFO);
		setLogLevel("ca.digitalcave", logLevel, Level.INFO);
	}
}
