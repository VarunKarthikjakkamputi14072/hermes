package com.hermes.worker.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan("com.hermes.common.domain")
@EnableJpaRepositories("com.hermes.common.repository")
public class JpaConfig {
}
