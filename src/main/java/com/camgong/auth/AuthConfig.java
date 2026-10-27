package com.camgong.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableAsync
@EnableConfigurationProperties(AuthProperties.class)
class AuthConfig {

    @Bean
    PasswordEncoder passwordEncoder(AuthProperties props) {
        return new BCryptPasswordEncoder(props.bcryptCost());
    }
}
