package ScriptsExport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// La anotación @SpringBootApplication combina @Configuration, @EnableAutoConfiguration y @ComponentScan.
// @ComponentScan asegura que Spring encuentre tu @Service, @RestController y @Configuration.
@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        // Inicializa y ejecuta la aplicación Spring Boot
        SpringApplication.run(Main.class, args);

    }
}