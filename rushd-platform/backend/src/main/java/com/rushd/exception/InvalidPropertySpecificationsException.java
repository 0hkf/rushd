package com.rushd.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class InvalidPropertySpecificationsException extends RuntimeException {
    private final Map<String, String> errors;
    public InvalidPropertySpecificationsException(Map<String, String> errors) {
        super("مواصفات العقار غير صالحة");
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }
    public Map<String, String> getErrors() { return errors; }
}
