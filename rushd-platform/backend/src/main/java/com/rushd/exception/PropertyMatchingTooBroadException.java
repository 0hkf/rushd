package com.rushd.exception;

public class PropertyMatchingTooBroadException extends RuntimeException {
    public PropertyMatchingTooBroadException() {
        super("البحث واسع جداً؛ ضيّق الميزانية أو اجعل نطاق المساحة شرطاً إلزامياً ثم أعد المحاولة. لم تُعرض نتائج جزئية.");
    }
}
