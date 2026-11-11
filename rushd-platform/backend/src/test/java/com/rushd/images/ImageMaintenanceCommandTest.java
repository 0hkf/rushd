package com.rushd.images;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageMaintenanceCommandTest {
    MockEnvironment environment() {return new MockEnvironment().withProperty("rushd.images.maintenance-ack","reviewed-database-and-private-root")
            .withProperty("server.port","0").withProperty("server.address","127.0.0.1");}
    @Test void operatorModeDefaultsToDryRun() throws Exception {
        boolean[] applied={true};
        var service=new PropertyImageService(null,null,null,null,new PrivateImageStorage(false,""),new ImageProcessor(),mock(PlatformTransactionManager.class)) {
            @Override public MaintenanceReport maintenance(boolean apply){applied[0]=apply;return new MaintenanceReport(List.of(),List.of());}
        };
        new ImageMaintenanceCommand(service,environment(),new ObjectMapper()).run(new DefaultApplicationArguments());
        assertFalse(applied[0]);
    }
    @ParameterizedTest @ValueSource(strings={"ack","port","address","mode"})
    void unreviewedOrPublicMaintenanceSettingsAreRejected(String field) {
        var env=environment();
        switch(field){
            case "ack"->env.setProperty("rushd.images.maintenance-ack","missing");
            case "port"->env.setProperty("server.port","8080");
            case "address"->env.setProperty("server.address","0.0.0.0");
            default->env.setProperty("rushd.images.maintenance-mode","unknown");
        }
        assertThrows(IllegalStateException.class,()->new ImageMaintenanceCommand(null,env,new ObjectMapper()).run(new DefaultApplicationArguments()));
    }
}
