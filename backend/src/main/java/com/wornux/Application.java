package com.wornux;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.BodySize;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.aura.Aura;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@Push
@BodySize(height = "100vh", width = "100vw")
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("styles.css")
@SpringBootApplication
public class Application implements AppShellConfigurator {

    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    ApplicationRunner runner(VectorStore store){
        return (_) -> store.similaritySearch("""
            Instruct: Given a student question about C programming,
            retrieve the most relevant educational passages that answer the question.
            Query: que es un bucle""");
    }

}
