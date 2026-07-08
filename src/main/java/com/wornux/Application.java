package com.wornux;

import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.BodySize;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.aura.Aura;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@Push
@BodySize(height = "100vh", width = "100vw")
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("styles.css")
@JsModule("./ui-tuner.ts")
@EnableScheduling
@SpringBootApplication
public class Application implements AppShellConfigurator {

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addFavIcon("icon", "icons/toggle.svg", "any");
    }

    static void main(String... args) {
        SpringApplication.run(Application.class, args);
    }
}
