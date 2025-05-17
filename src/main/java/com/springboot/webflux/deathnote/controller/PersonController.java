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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Controller
public class PersonController {

    private final PersonService personService;
    private final DeathNoteService deathNoteService;

    @Value("${deathnote.upload.path}")
    private String photoDisplayPath;

    @Value("${deathnote.upload.path:/tmp/deathnote_uploads}")
    private String uploadPath;

    public static final Logger log = LoggerFactory.getLogger(PersonController.class);

    public PersonController(PersonService personService, DeathNoteService deathNoteService) {
        this.personService = personService;
        this.deathNoteService = deathNoteService;
    }
    // ... (otros mappings sin cambios significativos para este problema)

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

    @GetMapping("/rules")
    public Mono<String> showRulesPage(Model model, WebSession session) {
        String activeDeathNoteId = session.getAttribute("ACTIVE_DEATH_NOTE_ID");
        if (activeDeathNoteId == null) {
            log.warn("No hay Death Note activa en sesión, redirigiendo a selección (/).");
            return Mono.just("redirect:/");
        }
        log.info("Mostrando página de reglas para Death Note ID: {}", activeDeathNoteId);
        model.addAttribute("pageTitle", "Reglas de la Death Note");
        model.addAttribute("activeDeathNoteId", activeDeathNoteId);
        return Mono.just("rules");
    }

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

    @PostMapping("/api/test-upload")
    @ResponseBody
    public Mono<String> handleFileUploadTest(@RequestParam(name = "file", required = false) FilePart filePart) {
        if (filePart != null) {
            log.info("[TEST UPLOAD] Archivo recibido: {}, tamaño: {}", filePart.filename(), filePart.headers().getContentLength());
            return Mono.just("Archivo recibido: " + filePart.filename());
        } else {
            log.info("[TEST UPLOAD] FilePart es NULO.");
            return Mono.just("Archivo NO recibido (FilePart fue nulo).");
        }
    }

    @PostMapping("/persons/add")
    public Mono<String> savePerson(@Valid Person personInput, BindingResult result, Model model,
                                   @RequestParam(name = "file", required = false) FilePart file,
                                   @RequestParam(name = "deathTime", required = false) String deathTimeStr,
                                   WebSession session, SessionStatus sessionStatus) {

        final Person person = personInput;
        String activeDeathNoteId = session.getAttribute("ACTIVE_DEATH_NOTE_ID");
        if (activeDeathNoteId == null) {
            log.warn("No hay Death Note activa en sesión al guardar persona. Redirigiendo.");
            return Mono.just("redirect:/?error=" + encodeURL("Por favor, selecciona una Death Note primero."));
        }
        person.setDeathNoteId(activeDeathNoteId);

        log.info("Inicio de savePerson para: {}. DN Activa: {}", person.getName(), activeDeathNoteId);

        if (file != null) {
            log.info("FilePart 'file' NO es nulo.");
            if (file.filename() != null && !file.filename().isEmpty()) {
                log.info("Nombre original del archivo en FilePart: '{}', ContentType: {}", file.filename(), file.headers().getContentType());
            } else {
                log.warn("FilePart 'file' recibido, pero su nombre es nulo o vacío. Headers: {}", file.headers());
            }
        } else {
            log.info("FilePart 'file' ES nulo.");
        }

        if (result.hasErrors()) {
            log.warn("Errores de validación al guardar persona: {}", person.getName());
            result.getAllErrors().forEach(err -> log.warn("Error de validación: {}", err.toString()));
            model.addAttribute("pageTitle", "Error al Anotar Persona");
            model.addAttribute("button", "Reintentar Anotar");
            model.addAttribute("activeDeathNoteId", activeDeathNoteId);
            model.addAttribute("person", personInput);
            return Mono.just("form");
        }

        Mono<Void> photoProcessingMono;
        boolean processFile = (file != null && file.filename() != null && !file.filename().isEmpty());
        log.info("¿Se procesará el archivo? : {}", processFile);

        if (processFile) {
            String originalFilename = file.filename();
            String safeOriginalFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String uniqueFilename = UUID.randomUUID().toString() + "-" + safeOriginalFilename;
            Path targetPath = Paths.get(uploadPath).resolve(uniqueFilename);
            File uploadDirFile = new File(uploadPath);

            if (!uploadDirFile.exists()) {
                boolean dirCreated = uploadDirFile.mkdirs();
                log.info("Directorio de subida {} creado: {}", uploadPath, dirCreated);
            }

            log.info("Intentando guardar archivo: '{}' como '{}' en la ruta: '{}'", originalFilename, uniqueFilename, targetPath);
            photoProcessingMono = file.transferTo(targetPath)
                    .doOnSuccess(voidResult -> {
                        log.info("ARCHIVO TRANSFERIDO EXITOSAMENTE: '{}'. Asignando nombre a person.facePhoto.", uniqueFilename);
                        person.setFacePhoto(uniqueFilename);
                    })
                    .doOnError(e -> {
                        log.error("ERROR AL TRANSFERIR ARCHIVO '{}': {}. La foto no se asignará.", originalFilename, e.getMessage(), e);
                    })
                    .then()
                    .onErrorResume(e -> {
                        log.error("ERROR CATASTRÓFICO AL TRANSFERIR ARCHIVO '{}', continuando sin foto: {}", originalFilename, e.getMessage());
                        return Mono.empty();
                    });
        } else {
            log.info("No se proporcionó archivo de foto válido. person.facePhoto actual (antes de saveInitialEntry): {}", person.getFacePhoto());
            photoProcessingMono = Mono.empty();
        }


        return photoProcessingMono
                .then(Mono.defer(() -> {
                    log.info("Dentro de Mono.defer. Nombre: {}, Foto actual en objeto person (después de photoProcessingMono): {}", person.getName(), person.getFacePhoto());

                    if (person.getId() == null || person.getId().isEmpty()) {
                        log.info("Llamando a personService.saveInitialEntry para: {}. Foto que se pasará al servicio: {}", person.getName(), person.getFacePhoto());
                        return personService.saveInitialEntry(person);
                    } else {
                        log.info("Llamando a lógica de actualización para: {}. Foto: {}", person.getName(), person.getFacePhoto());
                        return personService.findById(person.getId())
                                .flatMap(existingPerson -> {
                                    existingPerson.setName(person.getName());
                                    existingPerson.setFacePhoto(person.getFacePhoto());
                                    existingPerson.setDeathNoteId(activeDeathNoteId);
                                    return personService.save(existingPerson);
                                })
                                .switchIfEmpty(Mono.error(new RuntimeException("No se encontró la persona para actualizar con ID: " + person.getId())));
                    }
                }))
                .flatMap(savedPerson -> {
                    log.info("Persona '{}' guardada/procesada (ID: {}). Foto: {}. Estado: {}, Viva: {}. Asociando con DN: {}",
                            savedPerson.getName(), savedPerson.getId(), savedPerson.getFacePhoto(), savedPerson.getStatus(), savedPerson.isAlive(), activeDeathNoteId);
                    return deathNoteService.writePersonInDeathNote(
                            activeDeathNoteId,
                            savedPerson.getId(),
                            savedPerson.getDeathDetails(),
                            savedPerson.getScheduledDeathTime(),
                            savedPerson.getFacePhoto()
                    ).map(updatedDeathNote -> savedPerson);
                })
                .doOnSuccess(finalSavedPerson -> {
                    log.info("Proceso completado para persona {} ({}). Redirigiendo.", finalSavedPerson.getName(), finalSavedPerson.getId());
                    sessionStatus.setComplete();
                })
                .thenReturn("redirect:/listNames?success=" + encodeURL("Persona '" + person.getName() + "' anotada exitosamente."))
                .onErrorResume(e -> {
                    log.error("ERROR FINAL en el flujo de savePerson para '{}': {}", person.getName(), e.getMessage(), e);
                    model.addAttribute("pageTitle", "Error al Anotar Persona");
                    model.addAttribute("button", "Reintentar Anotar");
                    model.addAttribute("activeDeathNoteId", activeDeathNoteId);
                    model.addAttribute("person", person);
                    model.addAttribute("errorMessage", "Error al procesar: " + e.getMessage());
                    return Mono.just("form");
                });
    }

    @GetMapping("/listNames")
    public Mono<String> listAllPersons(Model model, WebSession session) {
        String activeDeathNoteId = session.getAttribute("ACTIVE_DEATH_NOTE_ID");
        log.info("Cargando página de listado de todas las personas. DN Activa: {}", activeDeathNoteId);

        Flux<Person> peopleFlux = personService.findAll().map(person -> {
            person.setName(person.getName().toUpperCase());
            return person;
        });
        model.addAttribute("people", peopleFlux);
        model.addAttribute("pageTitle", "Listado de Personas Anotadas");
        model.addAttribute("activeDeathNoteId", activeDeathNoteId);
        return Mono.just("list");
    }

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
                .map(person -> "view")
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Intento de ver persona no encontrada con ID: {}", id);
                    return Mono.just("redirect:/listNames?error=" + encodeURL("Persona no encontrada con ID: " + id));
                }))
                .onErrorResume(e -> {
                    log.error("Error al intentar ver persona con ID {}: {}", id, e.getMessage());
                    return Mono.just("redirect:/listNames?error=" + encodeURL("Error inesperado al obtener la persona."));
                });
    }

    @GetMapping("/delete/{id}")
    public Mono<String> deletePerson(@PathVariable String id, WebSession session) {
        String activeDeathNoteId = session.getAttribute("ACTIVE_DEATH_NOTE_ID");
        log.info("Solicitud para eliminar persona con ID: {}. DN Activa: {}", id, activeDeathNoteId);

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
                    Mono<Void> removeFromDeathNoteMono = Mono.empty();

                    return deleteFileMono
                            .then(removeFromDeathNoteMono)
                            .then(personService.delete(person))
                            .thenReturn("redirect:/listNames?success=" + encodeURL("Persona '" + person.getName() + "' eliminada."));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Intento de eliminar persona no encontrada con ID: {}", id);
                    return Mono.just("redirect:/listNames?error=" + encodeURL("Persona no encontrada con ID: " + id));
                }))
                .onErrorResume(e -> Mono.just("redirect:/listNames?error=" + encodeURL("Error al eliminar: " + e.getMessage())));
    }


    @GetMapping("/deathnote/reject/{id}")
    public Mono<String> rejectOwnership(@PathVariable String id, WebSession session) {
        return deathNoteService.rejectOwnership(id)
                .doOnSuccess(dn -> {
                    String activeDeathNoteId = session.getAttribute("ACTIVE_DEATH_NOTE_ID");
                    if (dn.getId().equals(activeDeathNoteId)) {
                        session.getAttributes().remove("ACTIVE_DEATH_NOTE_ID");
                        log.info("Death Note activa {} removida de sesión debido a rechazo de propiedad.", activeDeathNoteId);
                    }
                })
                .thenReturn("redirect:/?success=" + encodeURL("Propiedad de Death Note rechazada."))
                .onErrorResume(e -> Mono.just("redirect:/?error=" + encodeURL("Error al rechazar propiedad: " + e.getMessage())));
    }

    private String encodeURL(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            log.warn("Error al encodear URL para valor '{}': {}", value, e.getMessage());
            return value;
        }
    }

    @GetMapping("/persons/details/{id}")
    public Mono<String> showDeathDetailsForm(@PathVariable String id, Model model, WebSession session) {
        String activeDeathNoteId = session.getAttribute("ACTIVE_DEATH_NOTE_ID");
        if (activeDeathNoteId == null) {
            log.warn("Intento de acceder a detalles de muerte sin DN activa. ID Persona: {}", id);
        }

        log.info("Mostrando formulario de detalles de muerte para persona con ID: {}", id);
        return personService.findById(id)
                .flatMap(person -> {
                    if ("PENDING_HEART_ATTACK".equals(person.getStatus()) && person.isAlive()) {
                        log.info("Persona {} (ID: {}) en PENDING_HEART_ATTACK. Entrando a detalles, extendiendo tiempo a 400s.",
                                person.getName(), person.getId());
                        person.setScheduledDeathTime(person.getEntryTime().plusSeconds(400));
                        person.setStatus("AWAITING_DETAILS");
                        person.setDeathDetails("Esperando especificación de detalles (tiempo extendido a 400s).");

                        return personService.save(person).map(updatedPerson -> {
                            model.addAttribute("person", updatedPerson);
                            model.addAttribute("pageTitle", "Especificar Muerte para " + updatedPerson.getName());
                            model.addAttribute("activeDeathNoteId", activeDeathNoteId);
                            if (updatedPerson.getScheduledDeathTime() != null) {
                                model.addAttribute("explicitDeathDateStrSubmitted", updatedPerson.getScheduledDeathTime().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
                                model.addAttribute("explicitDeathTimeStrSubmitted", updatedPerson.getScheduledDeathTime().toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME));
                            }
                            return "details";
                        });
                    } else {
                        model.addAttribute("person", person);
                        model.addAttribute("pageTitle", "Especificar Muerte para " + person.getName());
                        model.addAttribute("activeDeathNoteId", activeDeathNoteId);
                        if (person.getDeathDate() != null) {
                            model.addAttribute("explicitDeathDateStrSubmitted", person.getDeathDate().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
                            model.addAttribute("explicitDeathTimeStrSubmitted", person.getDeathDate().toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME));
                        } else if (person.getScheduledDeathTime() != null) {
                            model.addAttribute("explicitDeathDateStrSubmitted", person.getScheduledDeathTime().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
                            model.addAttribute("explicitDeathTimeStrSubmitted", person.getScheduledDeathTime().toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME));
                        }
                        return Mono.just("details");
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Persona no encontrada con ID {} al intentar mostrar detalles de muerte.", id);
                    return Mono.just("redirect:/listNames?error=" + encodeURL("Persona no encontrada con ID: " + id));
                }));
    }

    // ¡MÉTODO CORREGIDO!
    @PostMapping("/persons/details/save")
    public Mono<String> saveDeathDetails(Person personFromForm,
                                         BindingResult result,
                                         Model model, WebSession session, SessionStatus sessionStatus,
                                         ServerWebExchange exchange) {

        return exchange.getFormData().flatMap(formData -> {
            final String personIdFromForm = formData.getFirst("id"); // Efectivamente final
            final String explicitDeathDateStr = formData.getFirst("explicitDeathDateStr"); // Efectivamente final
            final String explicitDeathTimeStr = formData.getFirst("explicitDeathTimeStr"); // Efectivamente final
            final String deathDetailsFromForm = formData.getFirst("deathDetails"); // Efectivamente final
            final String causeOfDeathFromForm = formData.getFirst("causeOfDeath"); // Efectivamente final

            log.info("--- INICIO DATOS FORMULARIO (ServerWebExchange) ---");
            formData.forEach((key, values) -> log.info("FormData: Clave='{}', Valores='{}'", key, values));
            log.info("Valores extraídos: ID='{}', FechaStr='{}', HoraStr='{}', Detalles='{}', Causa='{}'",
                    personIdFromForm, explicitDeathDateStr, explicitDeathTimeStr, deathDetailsFromForm, causeOfDeathFromForm);
            log.info("--- FIN DATOS FORMULARIO ---");

            final String activeDeathNoteId = session.getAttribute("ACTIVE_DEATH_NOTE_ID"); // Efectivamente final

            if (personIdFromForm == null || personIdFromForm.trim().isEmpty()) {
                log.error("ID de persona no recibido en el formulario de detalles.");
                model.addAttribute("person", personFromForm); // personFromForm podría no tener ID aquí.
                model.addAttribute("explicitDeathDateStrSubmitted", explicitDeathDateStr);
                model.addAttribute("explicitDeathTimeStrSubmitted", explicitDeathTimeStr);
                model.addAttribute("submittedDeathDetails", deathDetailsFromForm);
                model.addAttribute("submittedCauseOfDeath", causeOfDeathFromForm);
                model.addAttribute("pageTitle", "Error - Detalles de Muerte");
                model.addAttribute("errorMessage", "Error crítico: ID de persona no encontrado.");
                model.addAttribute("activeDeathNoteId", activeDeathNoteId);
                return Mono.just("details");
            }

            // Parseo y validación de fecha/hora.
            // Esta variable será asignada una vez y luego usada.
            final LocalDateTime finalParsedDeathDateTime;

            if (explicitDeathDateStr == null || explicitDeathDateStr.trim().isEmpty()) {
                result.rejectValue("deathDate", "NotEmpty", "La fecha de muerte es obligatoria.");
            }
            if (explicitDeathTimeStr == null || explicitDeathTimeStr.trim().isEmpty()) {
                result.reject("deathTime", "La hora de muerte es obligatoria.");
            }

            if (result.hasErrors()) {
                // Si hay errores de campos vacíos, necesitamos cargar la persona para el nombre, etc.
                return personService.findById(personIdFromForm)
                        .defaultIfEmpty(personFromForm) // Fallback
                        .flatMap(personForErrorContext -> {
                            log.warn("Errores de validación (campos vacíos) al guardar detalles para ID: {}. Errores: {}", personIdFromForm, result.getAllErrors());
                            model.addAttribute("person", personForErrorContext);
                            model.addAttribute("explicitDeathDateStrSubmitted", explicitDeathDateStr);
                            model.addAttribute("explicitDeathTimeStrSubmitted", explicitDeathTimeStr);
                            model.addAttribute("submittedDeathDetails", deathDetailsFromForm);
                            model.addAttribute("submittedCauseOfDeath", causeOfDeathFromForm);
                            model.addAttribute("pageTitle", "Error - Detalles de Muerte para " + (personForErrorContext.getName() != null ? personForErrorContext.getName() : "ID: "+personIdFromForm));
                            model.addAttribute("errorMessage", "Por favor corrige los errores e inténtalo de nuevo.");
                            model.addAttribute("activeDeathNoteId", activeDeathNoteId);
                            return Mono.just("details");
                        });
            }

            try {
                LocalDate datePart = LocalDate.parse(explicitDeathDateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                LocalTime timePart = LocalTime.parse(explicitDeathTimeStr, DateTimeFormatter.ISO_LOCAL_TIME);
                finalParsedDeathDateTime = LocalDateTime.of(datePart, timePart); // Asignación
                log.info("Fecha y hora de muerte combinadas: {}", finalParsedDeathDateTime);
            } catch (DateTimeParseException e) {
                log.warn("Error al parsear fecha/hora: Date='{}', Time='{}' - Error: {}", explicitDeathDateStr, explicitDeathTimeStr, e.getMessage());
                result.reject("invalid.datetime", "Formato de fecha u hora inválido. Use yyyy-MM-dd y HH:mm.");
                // Necesitamos cargar la persona para el contexto del error.
                return personService.findById(personIdFromForm)
                        .defaultIfEmpty(personFromForm) // Fallback
                        .flatMap(personForErrorContext -> {
                            model.addAttribute("person", personForErrorContext);
                            model.addAttribute("explicitDeathDateStrSubmitted", explicitDeathDateStr);
                            model.addAttribute("explicitDeathTimeStrSubmitted", explicitDeathTimeStr);
                            model.addAttribute("submittedDeathDetails", deathDetailsFromForm);
                            model.addAttribute("submittedCauseOfDeath", causeOfDeathFromForm);
                            model.addAttribute("pageTitle", "Error - Detalles de Muerte para " + (personForErrorContext.getName() != null ? personForErrorContext.getName() : "ID: "+personIdFromForm));
                            model.addAttribute("errorMessage", "Formato de fecha u hora inválido. Por favor corrige los errores.");
                            model.addAttribute("activeDeathNoteId", activeDeathNoteId);
                            return Mono.just("details");
                        });
            }
            // En este punto, finalParsedDeathDateTime está asignada y es efectivamente final si no hubo excepciones/retornos tempranos.

            return personService.findById(personIdFromForm)
                    .switchIfEmpty(Mono.error(new RuntimeException("Persona con ID " + personIdFromForm + " no encontrada para guardar detalles.")))
                    .flatMap(personToUpdate -> {
                        // Doble chequeo de errores, por si acaso (aunque los principales ya retornaron).
                        if (result.hasErrors()) {
                            log.warn("Errores de validación (posiblemente de parseo) al guardar detalles para ID: {}. Errores: {}", personIdFromForm, result.getAllErrors());
                            model.addAttribute("person", personToUpdate);
                            model.addAttribute("explicitDeathDateStrSubmitted", explicitDeathDateStr);
                            model.addAttribute("explicitDeathTimeStrSubmitted", explicitDeathTimeStr);
                            model.addAttribute("submittedDeathDetails", deathDetailsFromForm);
                            model.addAttribute("submittedCauseOfDeath", causeOfDeathFromForm);
                            model.addAttribute("pageTitle", "Error - Detalles de Muerte para " + personToUpdate.getName());
                            model.addAttribute("errorMessage", "Por favor corrige los errores e inténtalo de nuevo.");
                            model.addAttribute("activeDeathNoteId", activeDeathNoteId);
                            return Mono.just("details");
                        }

                        log.info("Procediendo a guardar detalles para Persona ID: {}, Nombre: {}", personIdFromForm, personToUpdate.getName());
                        // Usamos las variables (efectivamente) finales capturadas por esta lambda
                        return personService.specifyDeath(personIdFromForm, finalParsedDeathDateTime, deathDetailsFromForm, causeOfDeathFromForm)
                                .doOnSuccess(updatedPerson -> {
                                    log.info("Detalles de muerte actualizados para {} (ID: {}).", updatedPerson.getName(), updatedPerson.getId());
                                    if (activeDeathNoteId != null) {
                                        deathNoteService.writePersonInDeathNote(
                                                activeDeathNoteId,
                                                updatedPerson.getId(),
                                                updatedPerson.getDeathDetails(),
                                                updatedPerson.getScheduledDeathTime() != null ? updatedPerson.getScheduledDeathTime() : updatedPerson.getDeathDate(),
                                                updatedPerson.getFacePhoto()
                                        ).subscribe(
                                                dn -> log.info("DeathNote actualizada para persona {}", updatedPerson.getName()),
                                                err -> log.error("Error actualizando DeathNote para persona {}: {}", updatedPerson.getName(), err.getMessage())
                                        );
                                    }
                                    sessionStatus.setComplete();
                                })
                                .thenReturn("redirect:/listNames?success=" + encodeURL("Detalles de muerte actualizados para '" + personToUpdate.getName() + "'."));
                    })
                    .onErrorResume(e -> {
                        log.error("Error en el flujo de saveDeathDetails para ID {}: {}", personIdFromForm, e.getMessage(), e);
                        return personService.findById(personIdFromForm) // Recargar para el nombre etc.
                                .defaultIfEmpty(personFromForm) // Fallback si no se encuentra
                                .flatMap(originalPerson -> {
                                    model.addAttribute("person", originalPerson); // La persona original o del formulario
                                    model.addAttribute("explicitDeathDateStrSubmitted", explicitDeathDateStr);
                                    model.addAttribute("explicitDeathTimeStrSubmitted", explicitDeathTimeStr);
                                    model.addAttribute("submittedDeathDetails", deathDetailsFromForm);
                                    model.addAttribute("submittedCauseOfDeath", causeOfDeathFromForm);
                                    model.addAttribute("pageTitle", "Error - Detalles de Muerte para " + (originalPerson.getName() != null ? originalPerson.getName() : "ID: " + personIdFromForm));
                                    model.addAttribute("errorMessage", "Error al guardar los detalles: " + e.getMessage());
                                    model.addAttribute("activeDeathNoteId", activeDeathNoteId);
                                    return Mono.just("details");
                                });
                    });
        });
    }
}