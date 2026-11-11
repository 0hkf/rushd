package com.rushd.images;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;

@Configuration
public class ImageWebConfiguration {
    @Bean FilterRegistrationBean<ImageUploadGuard> imageGuardRegistration(ImageUploadGuard guard) {
        var registration = new FilterRegistrationBean<>(guard);
        registration.setEnabled(false); // security chain only: no duplicate, pre-auth invocation
        return registration;
    }
    @Bean MultipartConfigElement multipartConfigElement() {
        // Bounded in-memory source; never persist an original into a shared servlet temp directory.
        return new MultipartConfigElement("", ImageProcessor.MAX_SOURCE, 9 * PrivateImageStorage.MIB,
                (int) (9 * PrivateImageStorage.MIB));
    }
}
