package com.springboot.webflux.deathnote.controller;

import com.springboot.webflux.deathnote.model.DeathNote;
import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.services.DeathNoteService;
import com.springboot.webflux.deathnote.services.PersonService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession; // Importar WebSession
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Controller
public class PersonController { // Considera renombrar a AppController o dividir en más controladores si crece mucho

    private final PersonService personService;
    private final DeathNoteService deathNoteService;

    @Value("${deathnote.upload.path}")
    private String photoDisplayPath;

    @Value("${deathnote.upload.path:/tmp/deathnote_uploads}")
    private String uploadPath;

    public static final Logger log = LoggerFactory.getLogger(PersonController.class);

    // Inyección por constructor (buena práctica)
    public PersonController(PersonService personService, DeathNoteService deathNoteService) {
        this.personService = personService;
        this.deathNoteService = deathNoteService;
    }

    @GetMapping("/")
    public Mono<String> showSelectDeathNotePage(Model model) {
        log.info("Cargando página de selección de Death Note (ruta /).");
        return deathNoteService.findAll()
                .collectList()
                .flatMap(deathNotes -> {
                    if (deathNotes.isEmpty()) {
                        log.warn("No hay Death Notes disponibles para seleccionar.");
                        return Mono.just("redirect:/?error=" + encodeURL("No hay Death Notes disponibles. Por favor, crea una primero."));
                    }
                    model.addAttribute("pageTitle", "Elige tu Death Note");
                    model.addAttribute("deathNotes", deathNotes);
                    return Mono.just("index");
                });
    }

    @PostMapping("/processDeathNoteSelection")
    public Mono<String> processDeathNoteSelection(ServerWebExchange exchange, WebSession session) {
        return exchange.getFormData()
                .flatMap(formData -> {
                    String selectedDeathNoteId = formData.getFirst("selectedDeathNoteId");
                    log.info("Received selectedDeathNoteId: {}", selectedDeathNoteId);
                    if (selectedDeathNoteId == null || selectedDeathNoteId.isEmpty()) {
                        log.warn("No se seleccionó una Death Note. Redirigiendo a selección (/).");
                        return Mono.just("redirect:/?error=" + encodeURL("Por favor, selecciona una Death Note."));
                    }
                    log.info("Death Note seleccionada con ID: {}. Guardando en sesión y redirigiendo a /rules.", selectedDeathNoteId);
                    session.getAttributes().put("ACTIVE_DEATH_NOTE_ID", selectedDeathNoteId);
                    return Mono.just("redirect:/rules");
                });
    }

    // --- 3. MOSTRAR PÁGINA DE REGLAS ---
    @GetMapping("/rules")
    public Mono<String> showRulesPage(Model model, WebSession session) {
        String activeDeathNoteId = session.getAttribute("ACTIVE_DEATH_NOTE_ID");
        if (activeDeathNoteId == null) {
            log.warn("No hay Death Note activa en sesión, redirigiendo a selección (/).");
            return Mono.just("redirect:/");
        }
        log.info("Mostrando página de reglas para Death Note ID: {}", activeDeathNoteId);
        model.addAttribute("pageTitle", "Reglas de la Death Note");
        model.addAttribute("activeDeathNoteId", activeDeathNoteId); // Útil si el botón "continuar" en rules.html necesita este ID
        return Mono.just("rules"); // HTML de las reglas
    }

    // --- 4. PÁGINA PARA ANOTAR NOMBRES (FORMULARIO) ---
    @GetMapping("/anotarNombres")
    public Mono<String> showAnotarNombresPage(Model model, WebSession session) {
        String activeDeathNoteId = session.getAttribute("ACTIVE_DEATH_NOTE_ID");
        if (activeDeathNoteId == null) {
            log.warn("No hay Death Note activa en sesión para anotar nombres. Redirigiendo a selección (/).");
            return Mono.just("redirect:/");
        }
        log.info("Mostrando página para anotar nombres en Death Note ID: {}", activeDeathNoteId);
        model.addAttribute("pageTitle", "Anotar Persona en Death Note");
        model.addAttribute("activeDeathNoteId", activeDeathNoteId);
        model.addAttribute("person", new Person());
        model.addAttribute("button", "Anotar Persona");
        return Mono.just("form");
    }

    @PostMapping("/persons/add")
    public Mono<String> savePerson(@Valid Person person, BindingResult result, Model model,
                                   @RequestParam(name = "file", required = false) FilePart file,
                                   WebSession session, SessionStatus sessionStatus) {

        String activeDeathNoteId = session.getAttribute("ACTIVE_DEATH_NOTE_ID");
        if (activeDeathNoteId == null) {
            log.warn("No hay Death Note activa en sesión al intentar guardar persona. Redirigiendo a selección (/).");
            // Añadir un mensaje de error al modelo si rediriges a una página que pueda mostrarlo
            // model.addAttribute("globalError", "Por favor, selecciona una Death Note primero.");
            return Mono.just("redirect:/?error=" + encodeURL("Por favor, selecciona una Death Note primero."));
        }
        person.setDeathNoteId(activeDeathNoteId); // Asignar la DN activa a la persona

        if (result.hasErrors()) {
            log.warn("Errores de validación al guardar persona: {}", person.getName());
            result.getAllErrors().forEach(err -> log.warn("Error de validación: {}", err));
            model.addAttribute("pageTitle", "Error al Anotar Persona");
            model.addAttribute("button", "Reintentar Anotar");
            model.addAttribute("activeDeathNoteId", activeDeathNoteId); // Para que el form lo siga teniendo
            // model.addAttribute("person", person); // Spring usualmente lo hace con BindingResult
            return Mono.just("form"); // Volver al formulario de anotar nombres
        }

        Mono<String> photoFilenameMono;
        if (file != null && file.filename() != null && !file.filename().isEmpty()) {
            String uniqueFilename = UUID.randomUUID().toString() + "-" +
                    file.filename().replace(" ", "").replace(":", "").replace("\\", "");
            Path targetPath = Paths.get(uploadPath).resolve(uniqueFilename);
            File uploadDirFile = new File(uploadPath);
            if (!uploadDirFile.exists()) { uploadDirFile.mkdirs(); }

            log.info("Guardando archivo {} en {}", uniqueFilename, targetPath);
            photoFilenameMono = file.transferTo(targetPath)
                    .thenReturn(uniqueFilename)
                    .doOnError(e -> log.error("Error al transferir archivo {}", file.filename(), e));
        } else {
            photoFilenameMono = Mono.justOrEmpty(person.getFacePhoto());
        }

        return photoFilenameMono
                .doOnNext(filename -> {
                    if (filename != null) person.setFacePhoto(filename);
                })
                .defaultIfEmpty(person.getFacePhoto()) // Si no hay foto nueva y no había una antes, será null
                .flatMap(photoName -> {
                    if (person.getId() == null || person.getId().isEmpty()) { // Es una nueva persona
                        log.info("Llamando a personService.saveInitialEntry para: {} en DN: {}", person.getName(), activeDeathNoteId);
                        return personService.saveInitialEntry(person);
                    } else { // Actualización (esta lógica necesitará un formulario de edición separado)
                        log.info("Llamando a personService.save (actualización) para: {}", person.getName());
                        return personService.findById(person.getId())
                                .flatMap(existingPerson -> {
                                    existingPerson.setName(person.getName());
                                    existingPerson.setDeathDate(person.getDeathDate());
                                    existingPerson.setDeathDetails(person.getDeathDetails());
                                    existingPerson.setFacePhoto(person.getFacePhoto());
                                    existingPerson.setDeathNoteId(activeDeathNoteId); // Asegurar que sigue asociada a la DN activa
                                    // Aquí decidir si se puede cambiar el status, isAlive, etc. desde un form de edición
                                    return personService.save(existingPerson);
                                })
                                .switchIfEmpty(Mono.error(new RuntimeException("No se encontró la persona para actualizar con ID: " + person.getId())));
                    }
                })
                .flatMap(savedPerson -> {
                    log.info("Persona '{}' guardada (ID: {}). Asociando con DeathNote ID: {}", savedPerson.getName(), savedPerson.getId(), activeDeathNoteId);
                    // El personService.saveInitialEntry ya preparó a la persona.
                    // writePersonInDeathNote ahora se encarga más de la "escritura" formal y de actualizar la lista en DeathNote.
                    return deathNoteService.writePersonInDeathNote(
                            activeDeathNoteId, // Usar el ID de la DN activa de la sesión
                            savedPerson.getId(),
                            savedPerson.getDeathDetails(),
                            savedPerson.getDeathDate(),
                            savedPerson.getFacePhoto()
                    ).map(deathNote -> savedPerson); // Devolver savedPerson para el siguiente doOnSuccess
                })
                .doOnSuccess(savedPerson -> {
                    log.info("Persona {} procesada y asociada a DeathNote. Completando status de sesión.", savedPerson.getName());
                    // sessionStatus.setComplete(); // Usar si tienes @SessionAttributes("person") en el controlador
                })
                .thenReturn("redirect:/listNames?success=" + encodeURL("Persona '" + person.getName() + "' procesada exitosamente."))
                .onErrorResume(e -> {
                    log.error("Error en el flujo de guardado de persona: {}", e.getMessage(), e);
                    model.addAttribute("pageTitle", "Error al Anotar Persona");
                    model.addAttribute("button", "Reintentar Anotar");
                    model.addAttribute("activeDeathNoteId", activeDeathNoteId);
                    // model.addAttribute("person", person); // Spring lo hace
                    model.addAttribute("errorMessage", "Error: " + encodeURL(e.getMessage()));
                    return Mono.just("form"); // Volver al form de anotar_nombres con error
                });
    }

    // --- 6. LISTAR TODAS LAS PERSONAS (O FILTRADAS POR DN ACTIVA) ---
    @GetMapping("/listNames")
    public Mono<String> listAllPersons(Model model, WebSession session) {
        String activeDeathNoteId = session.getAttribute("ACTIVE_DEATH_NOTE_ID");
        // Decide si quieres mostrar todas las personas o solo las de la DN activa
        // Aquí muestro todas, pero podrías filtrarlas con:
        // Flux<Person> peopleFlux = personService.findAllByDeathNoteId(activeDeathNoteId);

        log.info("Cargando página de listado de todas las personas.");
        Flux<Person> peopleFlux = personService.findAll().map(person -> {
            person.setName(person.getName().toUpperCase());
            return person;
        });
        model.addAttribute("people", peopleFlux);
        model.addAttribute("pageTitle", "Listado de Personas Anotadas");
        model.addAttribute("activeDeathNoteId", activeDeathNoteId); // Por si la vista lo necesita
        return Mono.just("list"); // HTML para listar personas
    }

    // --- 7. VER DETALLE DE UNA PERSONA ---
    @GetMapping("/view/{id}")
    public Mono<String> viewPersonDetails(Model model, @PathVariable String id, WebSession session) {
        String activeDeathNoteId = session.getAttribute("ACTIVE_DEATH_NOTE_ID");

        return personService.findById(id)
                .doOnNext(person -> {
                    log.info("Viendo persona: {}", person.getName());
                    model.addAttribute("person", person);
                    model.addAttribute("pageTitle", "Detalle Persona: " + person.getName());
                    model.addAttribute("activeDeathNoteId", activeDeathNoteId);
                })
                .map(person -> "view") // Cambiado a map para retornar el nombre de la vista
                .switchIfEmpty(Mono.defer(() -> { // Mantenemos defer para la lógica condicional del log
                    log.warn("Intento de ver persona no encontrada con ID: {}", id);
                    return Mono.just("redirect:/listNames?error=" + encodeURL("Persona no encontrada con ID: " + id));
                }))
                .onErrorResume(e -> {
                    log.error("Error al intentar ver persona con ID {}: {}", id, e.getMessage());
                    return Mono.just("redirect:/listNames?error=" + encodeURL("Error inesperado al obtener la persona."));
                });
    }

    // --- OTROS MÉTODOS (delete, rejectOwnership, viewPhoto) ---
    @GetMapping("/delete/{id}")
    public Mono<String> deletePerson(@PathVariable String id, WebSession session) {
        return personService.findById(id)
                .flatMap(person -> {
                    Mono<Void> deleteFileMono = Mono.empty();
                    if (person.getFacePhoto() != null && !person.getFacePhoto().isEmpty()) {
                        Path photoPath = Paths.get(uploadPath).resolve(person.getFacePhoto());
                        File photoFile = photoPath.toFile();
                        if (photoFile.exists()) {
                            deleteFileMono = Mono.fromRunnable(() -> {
                                if (photoFile.delete()) {
                                    log.info("Archivo de foto {} eliminado.", person.getFacePhoto());
                                } else {
                                    log.warn("No se pudo eliminar el archivo de foto {}.", person.getFacePhoto());
                                }
                            }).subscribeOn(Schedulers.boundedElastic()).then();
                        }
                    }
                    return deleteFileMono.then(personService.delete(person))
                            .thenReturn("redirect:/listNames?success=" + encodeURL("Persona '" + person.getName() + "' eliminada."));
                })
                .switchIfEmpty(Mono.defer(() -> { // Mantenemos defer para la lógica condicional del log o si la creación del Mono es costosa
                    log.warn("Intento de eliminar persona no encontrada con ID: {}", id); // Añadido log
                    return Mono.just("redirect:/listNames?error=" + encodeURL("Persona no encontrada con ID: " + id));
                }))
                .onErrorResume(e -> Mono.just("redirect:/listNames?error=" + encodeURL("Error al eliminar: " + e.getMessage())));
    }

    @GetMapping("/deathnote/reject/{id}") // id aquí es el deathNoteId
    public Mono<String> rejectOwnership(@PathVariable String id, WebSession session) {
        return deathNoteService.rejectOwnership(id)
                .doOnSuccess(dn -> {
                    // Si la DN rechazada era la activa, limpiar de sesión
                    String activeDeathNoteId = session.getAttribute("ACTIVE_DEATH_NOTE_ID");
                    if (dn.getId().equals(activeDeathNoteId)) {
                        session.getAttributes().remove("ACTIVE_DEATH_NOTE_ID");
                        log.info("Death Note activa {} removida de sesión debido a rechazo de propiedad.", activeDeathNoteId);
                    }
                })
                .thenReturn("redirect:/?success=" + encodeURL("Propiedad de Death Note rechazada.")) // Redirigir a la selección
                .onErrorResume(e -> Mono.just("redirect:/?error=" + encodeURL("Error al rechazar propiedad: " + e.getMessage())));
    }

    // Helper para codificar URLs (opcional pero recomendado para mensajes)
    private String encodeURL(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return value; // Fallback
        }
    }
}