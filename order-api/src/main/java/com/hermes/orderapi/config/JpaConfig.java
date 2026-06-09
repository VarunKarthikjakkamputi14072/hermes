package com.hermes.orderapi.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA wiring lives here (not on the application class) so web-slice tests
 * ({@code @WebMvcTest}) can stand up the controller without bootstrapping
 * Hibernate.
 */
@Configuration
@EntityScan("com.hermes.common.domain")
@EnableJpaRepositories("com.hermes.common.repository")
public class JpaConfig {
}
