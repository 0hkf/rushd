package com.rushd.images;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Offline operator mode, no HTTP maintenance endpoint and no scheduled deletion. */
@Component
@ConditionalOnProperty(name="rushd.images.maintenance-enabled",havingValue="true")
public class ImageMaintenanceCommand implements ApplicationRunner {
    private final PropertyImageService images;
    private final Environment environment;
    private final ObjectMapper mapper;
    public ImageMaintenanceCommand(PropertyImageService images, Environment environment, ObjectMapper mapper) {
        this.images=images; this.environment=environment; this.mapper=mapper;
    }
    @Override public void run(ApplicationArguments args) throws Exception {
        String mode=environment.getProperty("rushd.images.maintenance-mode","dry-run");
        if (!java.util.Set.of("dry-run","apply").contains(mode)
                || !"reviewed-database-and-private-root".equals(environment.getProperty("rushd.images.maintenance-ack"))
                || !"0".equals(environment.getProperty("server.port"))
                || !"127.0.0.1".equals(environment.getProperty("server.address")))
            throw new IllegalStateException("Image maintenance requires reviewed explicit settings and an ephemeral loopback listener");
        System.out.println(mapper.writeValueAsString(images.maintenance(mode.equals("apply"))));
    }
}
