package com.janhavi.githubsearcher.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gitHubSearcherOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("GitHub Repository Searcher API")
                        .version("1.0.0")
                        .description("Search GitHub repositories, store the results in PostgreSQL, "
                                + "and retrieve them with filters."));
    }
}
