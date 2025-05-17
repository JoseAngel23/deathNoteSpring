package com.springboot.webflux.deathnote;

import com.springboot.webflux.deathnote.model.DeathNote;
import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.repository.DeathNoteRepository;
import com.springboot.webflux.deathnote.repository.PersonRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Considera añadir @ActiveProfiles("test") si tienes una configuración de BD específica para pruebas
class DeathNoteApplicationTests {

    @Autowired
    private WebTestClient webClient;

    @Autowired
    private DeathNoteRepository deathNoteRepository;

    @Autowired
    private PersonRepository personRepository;

    private String testDeathNoteId; // Usado para la DeathNote creada en setUp

    @BeforeEach
    void setUp() {
        // Limpiar datos ANTES de cada prueba para asegurar un estado limpio.
        // ¡¡¡CUIDADO!!! Esto borra datos. Asegúrate de que tu configuración de prueba
        // NO apunte a una base de datos de producción o desarrollo importante.
        // Es mejor usar una base de datos en memoria o una BD de prueba dedicada.
        System.out.println("SETUP: Limpiando colecciones Person y DeathNote...");
        personRepository.deleteAll().block(Duration.ofSeconds(10));
        deathNoteRepository.deleteAll().block(Duration.ofSeconds(10));
        System.out.println("SETUP: Colecciones limpiadas.");

        DeathNote testNote = new DeathNote("test-shinigami-setup", null); // Asume constructor (shinigamiId, ownerId)
        DeathNote savedNote = deathNoteRepository.save(testNote).block(Duration.ofSeconds(10)); // .block() es aceptable en setup

        Assertions.assertThat(savedNote).isNotNull();
        Assertions.assertThat(savedNote.getId()).isNotNull();
        testDeathNoteId = savedNote.getId();
        System.out.println("SETUP: Test DeathNote creada con ID: " + testDeathNoteId);
    }

    @AfterEach
    void tearDown() {
        // Limpiar datos DESPUÉS de cada prueba también es una buena práctica para aislamiento.
        System.out.println("TEARDOWN: Limpiando colecciones Person y DeathNote...");
        personRepository.deleteAll().block(Duration.ofSeconds(10));
        deathNoteRepository.deleteAll().block(Duration.ofSeconds(10));
        System.out.println("TEARDOWN: Colecciones limpiadas.");
    }

    @Test
    void listNames_shouldReturnListView_withHtmlContentType() {
        // Crear una persona de prueba para que la lista no esté vacía
        Person testPerson = new Person();
        testPerson.setName("Test List Person");
        testPerson.setDeathNoteId(testDeathNoteId); // Asociarla a la DN de prueba
        personRepository.save(testPerson).block(Duration.ofSeconds(5));

        webClient.get().uri("/listNames")
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .consumeWith(response -> {
                    String htmlBody = response.getResponseBody();
                    Assertions.assertThat(htmlBody).isNotNull();
                    Assertions.assertThat(htmlBody).contains("<title>Listado de Personas Anotadas</title>");
                    Assertions.assertThat(htmlBody).contains("<h1>Listado de Personas Anotadas</h1>");
                    // Tu controlador convierte nombres a mayúsculas para esta vista
                    Assertions.assertThat(htmlBody).contains("TEST LIST PERSON");
                });
    }

    @Test
    void showSelectDeathNotePage_whenDeathNotesExist_shouldReturnIndexView() {
        // El @BeforeEach ya asegura que existe 'testDeathNoteId'
        webClient.get().uri("/")
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .consumeWith(response -> {
                    String htmlBody = response.getResponseBody();
                    Assertions.assertThat(htmlBody).isNotNull();
                    Assertions.assertThat(htmlBody).contains("<title>Elige tu Death Note</title>");
                    Assertions.assertThat(htmlBody).contains("<h1>Elige la Death Note</h1>");
                    // Verifica que el ID de la DeathNote de prueba esté en las opciones del select
                    Assertions.assertThat(htmlBody).contains("value=\"" + testDeathNoteId + "\"");
                });
    }

    @Test
    void createPersonInDeathNote_withValidNameAndActiveSession_shouldRedirectToListView() {
        // --- Parte 1: Simular la selección de una Death Note para establecer la sesión ---
        EntityExchangeResult<byte[]> sessionSetupResult = webClient.post().uri("/processDeathNoteSelection")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("selectedDeathNoteId", testDeathNoteId))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, "/rules")
                .expectBody().returnResult();

        List<String> cookies = sessionSetupResult.getResponseHeaders().get(HttpHeaders.SET_COOKIE);
        final String sessionCookie = (cookies != null && !cookies.isEmpty()) ? cookies.get(0).split(";", 2)[0] : null;
        Assertions.assertThat(sessionCookie).as("La cookie de sesión no debería ser nula después de seleccionar DN.").isNotNull();

        Consumer<HttpHeaders> cookieSetter = headers -> {
            if (sessionCookie != null) {
                headers.add(HttpHeaders.COOKIE, sessionCookie);
            }
        };

        // --- Parte 2: Enviar el formulario para añadir una persona ---
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        String personName = "Kira Test Create";
        formData.add("name", personName);

        webClient.post().uri("/persons/add")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(cookieSetter) // Aplicar cookie de sesión
                .body(BodyInserters.fromFormData(formData))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, "/listNames\\?success=.*")
                .expectBody(Void.class);

        // (Opcional) Verificar que la persona fue creada en la BD
        Person foundPerson = personRepository.findAll()
                .filter(p -> p.getName().equals(personName)) // Busca por el nombre original
                .next()
                .block(Duration.ofSeconds(5));
        Assertions.assertThat(foundPerson).isNotNull();
        Assertions.assertThat(foundPerson.getDeathNoteId()).isEqualTo(testDeathNoteId);
    }

    @Test
    void addDeathDetails_afterCreatePersonInDeathNote_shouldUpdatePersonAndRedirect() {
        // --- Parte 1: Seleccionar Death Note (establecer sesión) ---
        EntityExchangeResult<byte[]> sessionSetupResult = webClient.post().uri("/processDeathNoteSelection")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("selectedDeathNoteId", testDeathNoteId))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, "/rules")
                .expectBody().returnResult();

        List<String> cookies = sessionSetupResult.getResponseHeaders().get(HttpHeaders.SET_COOKIE);
        final String sessionCookie = (cookies != null && !cookies.isEmpty()) ? cookies.get(0).split(";", 2)[0] : null;
        Assertions.assertThat(sessionCookie).as("La cookie de sesión no debería ser nula.").isNotNull();
        Consumer<HttpHeaders> cookieSetter = headers -> {
            if (sessionCookie != null) headers.add(HttpHeaders.COOKIE, sessionCookie);
        };

        // --- Parte 2: Crear una Persona ---
        MultiValueMap<String, String> createPersonFormData = new LinkedMultiValueMap<>();
        String personNameToCreate = "VictimForDetails-" + System.currentTimeMillis();
        createPersonFormData.add("name", personNameToCreate);

        webClient.post().uri("/persons/add")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(cookieSetter)
                .body(BodyInserters.fromFormData(createPersonFormData))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, "/listNames\\?success=.*")
                .expectBody(Void.class).returnResult(); // Consumir para asegurar que la llamada se complete

        // --- Parte 3: Obtener el ID de la Persona recién creada ---
        String searchName = personNameToCreate; // Buscar por el nombre original
        Person createdPerson = personRepository.findAll()
                .filter(p -> p.getName().equals(searchName))
                .next()
                .block(Duration.ofSeconds(10));

        Assertions.assertThat(createdPerson).as("La persona '" + searchName + "' no se encontró después de crearla.").isNotNull();
        String personIdToUpdate = createdPerson.getId();
        System.out.println("TEST (addDeathDetails): Persona creada con ID '" + personIdToUpdate + "' y nombre '" + createdPerson.getName() + "'");

        // --- Parte 4: Enviar el formulario de detalles de muerte ---
        MultiValueMap<String, String> deathDetailsFormData = new LinkedMultiValueMap<>();
        deathDetailsFormData.add("id", personIdToUpdate); // Enviar ID para que @ModelAttribute sepa qué Person es
        // Los otros campos de Person (name, alive, etc.) se obtendrán de la BD en el controlador
        // o se poblarán en personFromForm por @ModelAttribute si los envías,
        // pero para `specifyDeath` solo necesitas el ID y los nuevos detalles.

        String explicitDate = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String explicitTime = LocalTime.of(10, 30).format(DateTimeFormatter.ISO_LOCAL_TIME);
        String detailsText = "Caída accidental desde un edificio.";

        deathDetailsFormData.add("explicitDeathDateStr", explicitDate);
        deathDetailsFormData.add("explicitDeathTimeStr", explicitTime);
        deathDetailsFormData.add("deathDetails", detailsText);
        // deathDetailsFormData.add("causeOfDeath", "Impacto"); // Si lo has eliminado, no lo envíes

        webClient.post().uri("/persons/details/save")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(cookieSetter)
                .body(BodyInserters.fromFormData(deathDetailsFormData))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, "/listNames\\?success=.*Detalles\\+de\\+muerte\\+actualizados.*")
                .expectBody(Void.class);

        // --- Parte 5: Verificar que los datos se guardaron correctamente en la BD ---
        Person updatedPerson = personRepository.findById(personIdToUpdate).block(Duration.ofSeconds(5));
        Assertions.assertThat(updatedPerson).isNotNull();
        Assertions.assertThat(updatedPerson.getDeathDetails()).isEqualTo(detailsText);
        Assertions.assertThat(updatedPerson.isAlive()).as("La persona debería seguir viva ya que la muerte es futura").isTrue();
        Assertions.assertThat(updatedPerson.getStatus()).isEqualTo("DEATH_SCHEDULED_EXPLICITLY");
        Assertions.assertThat(updatedPerson.getScheduledDeathTime()).isEqualTo(LocalDateTime.parse(explicitDate + "T" + explicitTime));
        Assertions.assertThat(updatedPerson.getDeathDate()).as("DeathDate real no debería estar seteada aún para muerte futura").isNull();
    }

    // --- PRUEBA PARA BORRAR PERSONA ---
    @Test
    void deletePerson_shouldRemovePersonAndRedirect() {
        // --- Parte 1: Establecer sesión ---
        EntityExchangeResult<byte[]> sessionSetupResult = webClient.post().uri("/processDeathNoteSelection")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("selectedDeathNoteId", testDeathNoteId))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, "/rules")
                .expectBody().returnResult();

        List<String> cookies = sessionSetupResult.getResponseHeaders().get(HttpHeaders.SET_COOKIE);
        final String sessionCookie = (cookies != null && !cookies.isEmpty()) ? cookies.get(0).split(";", 2)[0] : null;
        Assertions.assertThat(sessionCookie).as("Cookie de sesión no debería ser nula.").isNotNull();
        Consumer<HttpHeaders> cookieSetter = headers -> {
            if (sessionCookie != null) headers.add(HttpHeaders.COOKIE, sessionCookie);
        };

        // --- Parte 2: Crear una Persona para luego borrarla ---
        MultiValueMap<String, String> createPersonFormData = new LinkedMultiValueMap<>();
        String personNameToDelete = "VictimToDelete-" + System.currentTimeMillis();
        createPersonFormData.add("name", personNameToDelete);

        webClient.post().uri("/persons/add") // Llama al endpoint de creación
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(cookieSetter)
                .body(BodyInserters.fromFormData(createPersonFormData))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, "/listNames\\?success=.*")
                .expectBody(Void.class).returnResult();

        // --- Parte 3: Obtener el ID de la Persona recién creada ---
        Person personToDelete = personRepository.findAll()
                .filter(p -> p.getName().equals(personNameToDelete)) // Busca por el nombre original
                .next()
                .block(Duration.ofSeconds(10));

        Assertions.assertThat(personToDelete).as("La persona '" + personNameToDelete + "' no se encontró para borrarla.").isNotNull();
        String personIdToDelete = personToDelete.getId();
        System.out.println("TEST (deletePerson): Persona a borrar con ID '" + personIdToDelete + "' y nombre '" + personToDelete.getName() + "'");

        // --- Parte 4: Llamar al endpoint de borrado ---
        webClient.get().uri("/delete/{id}", personIdToDelete) // GET a /delete/{id}
                .accept(MediaType.TEXT_HTML) // Aunque sea redirección, el origen podría servir HTML
                .headers(cookieSetter) // Enviar cookie de sesión si el endpoint de borrado la requiere
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, "/listNames\\?success=.*eliminada.*")
                .expectBody(Void.class);

        // --- Parte 5: Verificar que la persona fue eliminada de la BD ---
        Boolean personExists = personRepository.existsById(personIdToDelete).block(Duration.ofSeconds(5));
        Assertions.assertThat(personExists).isFalse();
    }
}