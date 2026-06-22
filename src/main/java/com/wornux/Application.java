package com.wornux;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.BodySize;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.aura.Aura;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Push
@BodySize(height = "100vh", width = "100vw")
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("styles.css")
@ConfigurationPropertiesScan
@EntityScan(basePackages = "com.wornux.data.entities")
@EnableJpaRepositories(basePackages = "com.wornux.data.repositories")
@ComponentScan(
        excludeFilters = {
            @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = {
                        "com\\.wornux\\.legacy\\..*",
                        "com\\.wornux\\.services\\.profile\\..*",
                        "com\\.wornux\\.services\\.subject\\..*",
                        "com\\.wornux\\.ai\\.profile\\..*",
                        "com\\.wornux\\.ai\\.advisor\\.SubjectContextAdvisor"
                    })
        })
@SpringBootApplication(excludeName = {
    "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
public class Application implements AppShellConfigurator {

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addFavIcon("icon", "icons/toggle.svg", "any");
    }

    static void main(String... args) {
        SpringApplication.run(Application.class, args);
    }
}
