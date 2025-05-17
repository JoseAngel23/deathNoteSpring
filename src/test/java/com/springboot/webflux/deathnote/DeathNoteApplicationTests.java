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
class DeathNoteApplicationTests {

    @Autowired
    private WebTestClient webClient;

    @Autowired
    private DeathNoteRepository deathNoteRepository;

    @Autowired
    private PersonRepository personRepository;

    private String testDeathNoteId;

    @BeforeEach
    void setUp() {
        System.out.println("SETUP: Limpiando colecciones Person y DeathNote...");
        personRepository.deleteAll().block(Duration.ofSeconds(10));
        deathNoteRepository.deleteAll().block(Duration.ofSeconds(10));
        System.out.println("SETUP: Colecciones limpiadas.");

        DeathNote testNote = new DeathNote("test-shinigami-setup", null);
        DeathNote savedNote = deathNoteRepository.save(testNote).block(Duration.ofSeconds(10));

        Assertions.assertThat(savedNote).isNotNull();
        Assertions.assertThat(savedNote.getId()).isNotNull();
        testDeathNoteId = savedNote.getId();
        System.out.println("SETUP: Test DeathNote creada con ID: " + testDeathNoteId);
    }

    @AfterEach
    void tearDown() {
        System.out.println("TEARDOWN: Limpiando colecciones Person y DeathNote...");
        personRepository.deleteAll().block(Duration.ofSeconds(10));
        deathNoteRepository.deleteAll().block(Duration.ofSeconds(10));
        System.out.println("TEARDOWN: Colecciones limpiadas.");
    }

    @Test
    void listNames_shouldReturnListView_withHtmlContentType() {
        Person testPerson = new Person();
        testPerson.setName("Test List Person");
        testPerson.setDeathNoteId(testDeathNoteId);
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
                    Assertions.assertThat(htmlBody).contains("TEST LIST PERSON");
                });
    }

    @Test
    void showSelectDeathNotePage_whenDeathNotesExist_shouldReturnIndexView() {
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
                    Assertions.assertThat(htmlBody).contains("value=\"" + testDeathNoteId + "\"");
                });
    }

    @Test
    void createPersonInDeathNote_withValidNameAndActiveSession_shouldRedirectToListView() {
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
    }

    @Test
    void addDeathDetails_afterCreatePersonInDeathNote_shouldUpdatePersonAndRedirect() {
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
                .expectBody(Void.class).returnResult();


        String searchName = personNameToCreate;
        Person createdPerson = personRepository.findAll()
                .filter(p -> p.getName().equals(searchName))
                .next()
                .block(Duration.ofSeconds(10));

        Assertions.assertThat(createdPerson).as("La persona '" + searchName + "' no se encontró después de crearla.").isNotNull();
        String personIdToUpdate = createdPerson.getId();
        System.out.println("TEST (addDeathDetails): Persona creada con ID '" + personIdToUpdate + "' y nombre '" + createdPerson.getName() + "'");

        MultiValueMap<String, String> deathDetailsFormData = new LinkedMultiValueMap<>();
        deathDetailsFormData.add("id", personIdToUpdate);

        String explicitDate = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String explicitTime = LocalTime.of(10, 30).format(DateTimeFormatter.ISO_LOCAL_TIME);
        String detailsText = "Caída accidental desde un edificio.";

        deathDetailsFormData.add("explicitDeathDateStr", explicitDate);
        deathDetailsFormData.add("explicitDeathTimeStr", explicitTime);
        deathDetailsFormData.add("deathDetails", detailsText);

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
        Assertions.assertThat(updatedPerson.isAlive()).as("La persona debería seguir viva ya que la muerte es futura").isTrue();
        Assertions.assertThat(updatedPerson.getStatus()).isEqualTo("DEATH_SCHEDULED_EXPLICITLY");
        Assertions.assertThat(updatedPerson.getScheduledDeathTime()).isEqualTo(LocalDateTime.parse(explicitDate + "T" + explicitTime));
        Assertions.assertThat(updatedPerson.getDeathDate()).as("DeathDate real no debería estar seteada aún para muerte futura").isNull();
    }

    @Test
    void deletePerson_shouldRemovePersonAndRedirect() {
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

        MultiValueMap<String, String> createPersonFormData = new LinkedMultiValueMap<>();
        String personNameToDelete = "VictimToDelete-" + System.currentTimeMillis();
        createPersonFormData.add("name", personNameToDelete);

        webClient.post().uri("/persons/add")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(cookieSetter)
                .body(BodyInserters.fromFormData(createPersonFormData))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, "/listNames\\?success=.*")
                .expectBody(Void.class).returnResult();

        Person personToDelete = personRepository.findAll()
                .filter(p -> p.getName().equals(personNameToDelete))
                .next()
                .block(Duration.ofSeconds(10));

        Assertions.assertThat(personToDelete).as("La persona '" + personNameToDelete + "' no se encontró para borrarla.").isNotNull();
        String personIdToDelete = personToDelete.getId();
        System.out.println("TEST (deletePerson): Persona a borrar con ID '" + personIdToDelete + "' y nombre '" + personToDelete.getName() + "'");

        // --- Parte 4: Llamar al endpoint de borrado ---
        webClient.get().uri("/delete/{id}", personIdToDelete)
                .accept(MediaType.TEXT_HTML)
                .headers(cookieSetter)
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, "/listNames\\?success=.*eliminada.*")
                .expectBody(Void.class);

        Boolean personExists = personRepository.existsById(personIdToDelete).block(Duration.ofSeconds(5));
        Assertions.assertThat(personExists).isFalse();
    }
}