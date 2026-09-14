package io.github.johneliud.identity_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "identity.admin")
@Getter
@Setter
public class AdminProperties {

    private String email;
    private String password;
    private String firstName;
    private String lastName;
}
