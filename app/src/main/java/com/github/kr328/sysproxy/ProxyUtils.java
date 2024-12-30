package com.github.kr328.sysproxy;

import java.util.regex.Pattern;

public final class ProxyUtils {
    private static final String EXCL_REGEX =
            "[a-zA-Z0-9*]+(-[a-zA-Z0-9*]+)*(\\.[a-zA-Z0-9*]+(-[a-zA-Z0-9*]+)*)*";
    private static final String EXCLLIST_REGEXP = "^$|^" + EXCL_REGEX + "(," + EXCL_REGEX + ")*$";

    private static final Pattern EXCLLIST_PATTERN = Pattern.compile(EXCLLIST_REGEXP);

    public static boolean isValidExcludeList(String excludeList) {
        return EXCLLIST_PATTERN.matcher(excludeList).matches();
    }
}
