package com.rushd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RushdApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(RushdApplication.class, args);
        // An explicitly requested offline image maintenance run exits after startup/runners finish.
        if (context.getEnvironment().getProperty("rushd.images.maintenance-enabled", Boolean.class, false)) {
            context.close();
        }
    }
}
