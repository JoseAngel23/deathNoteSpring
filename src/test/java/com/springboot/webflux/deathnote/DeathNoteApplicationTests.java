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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Considera @ActiveProfiles("test") si tienes configuraciones específicas de BD para pruebas
class DeathNoteApplicationTests {

    @Autowired
    private WebTestClient webClient;

    @Autowired
    private DeathNoteRepository deathNoteRepository;

    @Autowired
    private PersonRepository personRepository;

    private String testDeathNoteId; // ID de la DeathNote creada para cada test en setUp

    @BeforeEach
    void setUp() {
        System.out.println("SETUP: Limpiando colecciones Person y DeathNote...");
        // Es crucial esperar a que se completen estas operaciones
        personRepository.deleteAll().blockOptional(Duration.ofSeconds(10));
        deathNoteRepository.deleteAll().blockOptional(Duration.ofSeconds(10));
        System.out.println("SETUP: Colecciones limpiadas.");

        DeathNote testNote = new DeathNote("test-shinigami-setup-" + System.nanoTime(), null);
        DeathNote savedNote = deathNoteRepository.save(testNote).block(Duration.ofSeconds(10));

        Assertions.assertThat(savedNote).isNotNull();
        Assertions.assertThat(savedNote.getId()).isNotNull();
        testDeathNoteId = savedNote.getId();
        System.out.println("SETUP: Test DeathNote creada con ID: " + testDeathNoteId);
    }

    @AfterEach
    void tearDown() {
        System.out.println("TEARDOWN: Limpiando colecciones Person y DeathNote...");
        personRepository.deleteAll().blockOptional(Duration.ofSeconds(10));
        deathNoteRepository.deleteAll().blockOptional(Duration.ofSeconds(10));
        System.out.println("TEARDOWN: Colecciones limpiadas.");
    }

    private Consumer<HttpHeaders> getCookieSetter(EntityExchangeResult<byte[]> sessionSetupResult) {
        List<String> cookies = sessionSetupResult.getResponseHeaders().get(HttpHeaders.SET_COOKIE);
        final String sessionCookie = (cookies != null && !cookies.isEmpty()) ? cookies.get(0).split(";", 2)[0] : null;
        Assertions.assertThat(sessionCookie).as("La cookie de sesión no debería ser nula.").isNotNull();
        return headers -> {
            if (sessionCookie != null) {
                headers.add(HttpHeaders.COOKIE, sessionCookie);
            }
        };
    }

    private EntityExchangeResult<byte[]> selectDeathNoteAndGetSession() {
        return webClient.post().uri("/processDeathNoteSelection")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("selectedDeathNoteId", testDeathNoteId))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, "/rules")
                .expectBody().returnResult();
    }

    @Test
    void listNames_shouldReturnListView_withHtmlContentType() {
        Person testPerson = new Person();
        testPerson.setName("Test List Person");
        testPerson.setDeathNoteId(testDeathNoteId);
        personRepository.save(testPerson).block(Duration.ofSeconds(5));

        Consumer<HttpHeaders> cookieSetter = getCookieSetter(selectDeathNoteAndGetSession());


        webClient.get().uri("/listNames")
                .accept(MediaType.TEXT_HTML)
                .headers(cookieSetter) // La lista podría depender de la DN activa en sesión
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .consumeWith(response -> {
                    String htmlBody = response.getResponseBody();
                    Assertions.assertThat(htmlBody).isNotNull();
                    Assertions.assertThat(htmlBody).contains("<title>Listado de Personas Anotadas</title>");
                    Assertions.assertThat(htmlBody).contains("<h1>Listado de Personas Anotadas</h1>");
                    Assertions.assertThat(htmlBody).contains("TEST LIST PERSON");
                });
    }

    @Test
    void showSelectDeathNotePage_whenDeathNotesExist_shouldReturnIndexView() {
        // @BeforeEach ya asegura que existe 'testDeathNoteId'
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
                    // Asegúrate que el título/selector en tu HTML coincida
                    Assertions.assertThat(htmlBody).contains("<h1 class=\"my-4\">Elige la Death Note que usarás:</h1>");
                    Assertions.assertThat(htmlBody).contains("value=\"" + testDeathNoteId + "\"");
                });
    }

    @Test
    void createPersonInDeathNote_withValidNameAndActiveSession_shouldCreatePersonAndRedirect() {
        Consumer<HttpHeaders> cookieSetter = getCookieSetter(selectDeathNoteAndGetSession());

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        String personName = "Kira Test Create";
        formData.add("name", personName);

        webClient.post().uri("/persons/add")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(cookieSetter)
                .body(BodyInserters.fromFormData(formData))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, "/listNames\\?success=.*")
                .expectBody(Void.class);

        Person foundPerson = personRepository.findAll()
                .filter(p -> p.getName().equals(personName))
                .next()
                .block(Duration.ofSeconds(5));

        Assertions.assertThat(foundPerson).isNotNull();
        Assertions.assertThat(foundPerson.getDeathNoteId()).isEqualTo(testDeathNoteId);
        Assertions.assertThat(foundPerson.getStatus()).isEqualTo("PENDING_HEART_ATTACK");
        Assertions.assertThat(foundPerson.getCauseOfDeath()).isEqualTo("Ataque al Corazón");
        Assertions.assertThat(foundPerson.getDeathDetails()).isEqualTo("Muerte automática por ataque al corazón a los 40 segundos (sin detalles especificados).");

        // Corrección aquí:
        Assertions.assertThat(foundPerson.getScheduledDeathTime())
                .isCloseTo(foundPerson.getEntryTime().plusSeconds(40), Assertions.within(1, ChronoUnit.SECONDS));
    }

    @Test
    void addDeathDetails_afterPersonCreation_shouldUpdatePersonAndRedirect() {
        Consumer<HttpHeaders> cookieSetter = getCookieSetter(selectDeathNoteAndGetSession());

        // --- Crear Persona ---
        String personNameToCreate = "VictimForDetails-" + System.currentTimeMillis();
        webClient.post().uri("/persons/add")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(cookieSetter)
                .body(BodyInserters.fromFormData("name", personNameToCreate))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectBody(Void.class).returnResult();

        Person createdPerson = personRepository.findAll().filter(p -> p.getName().equals(personNameToCreate)).next().block(Duration.ofSeconds(10));
        Assertions.assertThat(createdPerson).isNotNull();
        String personIdToUpdate = createdPerson.getId();

        // --- Enviar Detalles ---
        MultiValueMap<String, String> deathDetailsFormData = new LinkedMultiValueMap<>();
        deathDetailsFormData.add("id", personIdToUpdate);
        String explicitDate = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String explicitTime = LocalTime.of(10, 30).format(DateTimeFormatter.ISO_LOCAL_TIME);
        String detailsText = "Caída accidental desde un edificio.";
        String causeText = "Impacto contundente";

        deathDetailsFormData.add("explicitDeathDateStr", explicitDate);
        deathDetailsFormData.add("explicitDeathTimeStr", explicitTime);
        deathDetailsFormData.add("deathDetails", detailsText);
        deathDetailsFormData.add("causeOfDeath", causeText); // Asegúrate que este campo se envíe y procese

        webClient.post().uri("/persons/details/save")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(cookieSetter)
                .body(BodyInserters.fromFormData(deathDetailsFormData))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, "/listNames\\?success=.*Detalles\\+de\\+muerte\\+actualizados.*")
                .expectBody(Void.class);

        Person updatedPerson = personRepository.findById(personIdToUpdate).block(Duration.ofSeconds(5));
        Assertions.assertThat(updatedPerson).isNotNull();
        Assertions.assertThat(updatedPerson.getDeathDetails()).isEqualTo(detailsText);
        Assertions.assertThat(updatedPerson.getCauseOfDeath()).isEqualTo(causeText); // Verificar la causa
        Assertions.assertThat(updatedPerson.isAlive()).isTrue();
        Assertions.assertThat(updatedPerson.getStatus()).isEqualTo("DEATH_SCHEDULED_EXPLICITLY");
        Assertions.assertThat(updatedPerson.getScheduledDeathTime()).isEqualTo(LocalDateTime.parse(explicitDate + "T" + explicitTime));
        Assertions.assertThat(updatedPerson.getDeathDate()).isNull();
    }

    @Test
    void givenPersonIsPendingHeartAttack_whenAccessingDetailsPage_thenTimeIsExtendedAndStatusChanges() {
        Consumer<HttpHeaders> cookieSetter = getCookieSetter(selectDeathNoteAndGetSession());

        // --- Crear Persona (entra en PENDING_HEART_ATTACK) ---
        String personName = "VictimFor400s-" + System.currentTimeMillis();
        webClient.post().uri("/persons/add")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(cookieSetter)
                .body(BodyInserters.fromFormData("name", personName))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectBody(Void.class).returnResult();

        Person createdPerson = personRepository.findAll().filter(p -> p.getName().equals(personName)).next().block(Duration.ofSeconds(5));
        Assertions.assertThat(createdPerson).isNotNull();
        Assertions.assertThat(createdPerson.getStatus()).isEqualTo("PENDING_HEART_ATTACK");
        LocalDateTime initialEntryTime = createdPerson.getEntryTime(); // Guardar para comparación

        // --- Acceder a la página de detalles ---
        webClient.get().uri("/persons/details/{id}", createdPerson.getId())
                .accept(MediaType.TEXT_HTML)
                .headers(cookieSetter)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    String htmlBody = Objects.requireNonNull(response.getResponseBody());
                    Assertions.assertThat(htmlBody).contains("Especificar Muerte para " + personName);
                });

        // --- Verificar cambios en BD ---
        Person updatedPerson = personRepository.findById(createdPerson.getId()).block(Duration.ofSeconds(5));
        Assertions.assertThat(updatedPerson).isNotNull();
        Assertions.assertThat(updatedPerson.getStatus()).isEqualTo("AWAITING_DETAILS");
        Assertions.assertThat(updatedPerson.getDeathDetails()).isEqualTo("Esperando especificación de detalles (tiempo extendido a 400s).");
        // La causa de muerte original ("Ataque al Corazón") debe mantenerse, no se cambia al entrar a detalles.
        Assertions.assertThat(updatedPerson.getCauseOfDeath()).isEqualTo("Ataque al Corazón");

        LocalDateTime expectedExtendedDeathTime = initialEntryTime.plusSeconds(400);
        // Corrección aquí:
        Assertions.assertThat(updatedPerson.getScheduledDeathTime())
                .isCloseTo(expectedExtendedDeathTime, Assertions.within(1, ChronoUnit.SECONDS));
    }


    @Test
    void deletePerson_shouldRemovePersonAndRedirect() {
        Consumer<HttpHeaders> cookieSetter = getCookieSetter(selectDeathNoteAndGetSession());

        // --- Crear Persona ---
        String personNameToDelete = "VictimToDelete-" + System.currentTimeMillis();
        webClient.post().uri("/persons/add")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(cookieSetter)
                .body(BodyInserters.fromFormData("name", personNameToDelete))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectBody(Void.class).returnResult();

        Person personToDelete = personRepository.findAll().filter(p -> p.getName().equals(personNameToDelete)).next().block(Duration.ofSeconds(10));
        Assertions.assertThat(personToDelete).isNotNull();
        String personIdToDelete = personToDelete.getId();

        // Asegurarse que la persona esté en la DeathNote para probar la eliminación de la lista de IDs
        DeathNote dn = deathNoteRepository.findById(testDeathNoteId).block();
        Assertions.assertThat(dn).isNotNull();
        if (!dn.getPersonIds().contains(personIdToDelete)) {
            dn.addPersonId(personIdToDelete);
            deathNoteRepository.save(dn).block();
        }


        // --- Llamar al endpoint de borrado ---
        webClient.get().uri("/delete/{id}", personIdToDelete)
                .accept(MediaType.TEXT_HTML)
                .headers(cookieSetter)
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, "/listNames\\?success=.*eliminada.*")
                .expectBody(Void.class);

        // --- Verificar que fue eliminada de la BD y de la DeathNote ---
        Assertions.assertThat(personRepository.existsById(personIdToDelete).block(Duration.ofSeconds(5))).isFalse();

        DeathNote updatedDn = deathNoteRepository.findById(testDeathNoteId).block(Duration.ofSeconds(5));
        Assertions.assertThat(updatedDn).isNotNull();
        Assertions.assertThat(updatedDn.getPersonIds()).doesNotContain(personIdToDelete);
    }
}