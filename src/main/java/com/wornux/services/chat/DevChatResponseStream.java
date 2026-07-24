package com.wornux.services.chat;

import java.time.Duration;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@Profile("dev")
public class DevChatResponseStream {

    private static final String RESPONSE =
            """
            # Respuesta de prueba para la interfaz

            Esta respuesta es siempre la misma y existe para comprobar cómo se comporta la conversación cuando el contenido llega de forma progresiva. Incluye texto largo, **énfasis**, enlaces, listas, tablas, citas y bloques de código.

            ## Flujo recomendado

            1. Comprueba que el indicador de pensamiento permanece visible antes de recibir el primer fragmento.
            2. Observa cómo el mensaje crece y cómo la conversación sigue el borde inferior.
            3. Desplázate hacia arriba durante la transmisión para confirmar que el scroll automático deja de perseguir la respuesta.
            4. Vuelve al final y verifica el renderizado incremental de Markdown.

            > Esta salida es sintética. No se realizó ninguna llamada al modelo mientras el modo de prueba estaba activo.

            ## Ejemplo en Java

            ```java
            public record Student(String name, int solvedExercises) {
                public boolean canAttemptAdvancedTopic() {
                    return solvedExercises >= 5;
                }
            }

            var students = List.of(
                    new Student("Ada", 8),
                    new Student("Alan", 3));

            var ready = students.stream()
                    .filter(Student::canAttemptAdvancedTopic)
                    .map(Student::name)
                    .toList();
            ```

            ## Ejemplo en TypeScript

            ```typescript
            type StreamState = 'thinking' | 'streaming' | 'complete';

            function appendChunk(current: string, chunk: string): string {
              return `${current}${chunk}`;
            }

            const state: StreamState = 'streaming';
            console.info({ state, timestamp: new Date().toISOString() });
            ```

            ## Ejemplo de terminal

            ```shell
            curl --fail --silent http://localhost:3321/actuator/health | jq .status
            ```

            | Elemento | Qué validar |
            | --- | --- |
            | Encabezados | Jerarquía y espaciado vertical |
            | Código | Scroll horizontal, fuente y botón de depuración |
            | Tabla | Bordes, alineación y ancho en móvil |
            | Streaming | Actualizaciones fluidas sin saltos de layout |

            Finalmente, este párrafo añade suficiente longitud para probar una respuesta que ocupa varias pantallas. El contenido debe seguir siendo legible mientras llegan nuevos fragmentos, el indicador de carga debe desaparecer justo al comenzar el texto y el estado ocupado debe finalizar únicamente después del último fragmento.
            """;

    public Flux<String> stream() {
        return Flux.fromArray(RESPONSE.split("(?<=\\s)"))
                .delaySubscription(Duration.ofSeconds(4))
                .delayElements(Duration.ofMillis(20));
    }
}
