package ru.yandex.practicum.pulsehome.infra.hubrouterstub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HubRouterStubApplication {

    public static void main(String[] args) {
        SpringApplication.run(HubRouterStubApplication.class, args);
    }
}
