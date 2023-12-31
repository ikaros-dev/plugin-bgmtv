package run.ikaros.plugin.bgmtv.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * @author li-guohao
 */
public class AssertUtils {

    public static void notNull(Object obj, String varName) {
        if (obj == null) {
            throw new IllegalArgumentException("'" + varName + "' must not be null");
        }
    }

    public static void notBlank(String str, String varName) {
        notNull(str, varName);
        if (StringUtils.isBlank(str)) {
            throw new IllegalArgumentException("'" + varName + "' must not be blank");
        }
    }


    public static void isPositive(long number, String varName) {
        if (number < 0) {
            throw new IllegalArgumentException("'" + varName + "' must be positive");
        }
    }

    public static void isTrue(boolean condition, String varName) {
        if (!condition) {
            throw new IllegalArgumentException("'" + varName + "' must be true");
        }
    }

    public static void isFalse(boolean condition, String varName) {
        if (condition) {
            throw new IllegalArgumentException("'" + varName + "' must be false");
        }
    }

}
