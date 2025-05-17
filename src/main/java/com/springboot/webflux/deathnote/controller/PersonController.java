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
                    .then() // Convierte a Mono<Void>
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
                        // Lógica de actualización (asegúrate que esta lógica también usa person.getFacePhoto())
                        log.info("Llamando a lógica de actualización para: {}. Foto: {}", person.getName(), person.getFacePhoto());
                        return personService.findById(person.getId())
                                .flatMap(existingPerson -> {
                                    existingPerson.setName(person.getName());
                                    existingPerson.setDeathDate(person.getDeathDate());
                                    existingPerson.setDeathDetails(person.getDeathDetails());
                                    existingPerson.setFacePhoto(person.getFacePhoto()); // Usa la foto del objeto 'person'
                                    existingPerson.setDeathNoteId(activeDeathNoteId);
                                    return personService.save(existingPerson);
                                })
                                .switchIfEmpty(Mono.error(new RuntimeException("No se encontró la persona para actualizar con ID: " + person.getId())));
                    }
                }))
                .flatMap(savedPerson -> {
                    log.info("Persona '{}' guardada/procesada (ID: {}). Foto: {}. Estado: {}, Viva: {}. Asociando con DN: {}",
                            savedPerson.getName(), savedPerson.getId(), savedPerson.getFacePhoto(), savedPerson.getStatus(), savedPerson.isAlive(), activeDeathNoteId);

                    LocalDateTime deathTimestampForNote = savedPerson.getScheduledDeathTime() != null ? savedPerson.getScheduledDeathTime() : savedPerson.getDeathDate();
                    if (person.getDeathDate() != null && (deathTimeStr != null && !deathTimeStr.isEmpty())) {
                        deathTimestampForNote = person.getDeathDate(); // Si se especificó fecha y hora explícita, usar esa.
                    }

                    return deathNoteService.writePersonInDeathNote(
                            activeDeathNoteId,
                            savedPerson.getId(),
                            savedPerson.getDeathDetails(),
                            deathTimestampForNote,
                            savedPerson.getFacePhoto()
                    ).map(updatedDeathNote -> savedPerson);
                })
                .doOnSuccess(finalSavedPerson -> {
                    log.info("Proceso completado para persona {} ({}). Redirigiendo.", finalSavedPerson.getName(), finalSavedPerson.getId());
                })
                .thenReturn("redirect:/listNames?success=" + encodeURL("Persona '" + person.getName() + "' procesada exitosamente."))
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

        log.info("Cargando página de listado de todas las personas.");
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
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Intento de eliminar persona no encontrada con ID: {}", id);
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

    private String encodeURL(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return value; // Fallback
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

                    model.addAttribute("person", person);
                    model.addAttribute("pageTitle", "Especificar Muerte para " + person.getName());
                    model.addAttribute("activeDeathNoteId", activeDeathNoteId);
                    return Mono.just("details");
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Persona no encontrada con ID {} al intentar mostrar detalles de muerte.", id);
                    return Mono.just("redirect:/listNames?error=" + encodeURL("Persona no encontrada con ID: " + id));
                }));
    }

    @PostMapping("/persons/details/save")
    public Mono<String> saveDeathDetails(@ModelAttribute("person") Person personFromForm,
                                         BindingResult result,
                                         Model model, WebSession session, SessionStatus sessionStatus,
                                         ServerWebExchange exchange) {

        return exchange.getFormData().flatMap(formData -> {
            String explicitDeathDateStr = formData.getFirst("explicitDeathDateStr");
            String explicitDeathTimeStr = formData.getFirst("explicitDeathTimeStr");

            log.info("--- INICIO DATOS FORMULARIO (ServerWebExchange) ---");
            formData.forEach((key, values) -> {
                log.info("FormData: Clave='{}', Valores='{}'", key, values);
            });
            log.info("Valor extraído para explicitDeathDateStr: '{}'", explicitDeathDateStr);
            log.info("Valor extraído para explicitDeathTimeStr: '{}'", explicitDeathTimeStr);
            log.info("--- FIN DATOS FORMULARIO ---");

            String activeDeathNoteId = session.getAttribute("ACTIVE_DEATH_NOTE_ID");
            log.info("Guardando detalles de muerte para Persona ID: {}", personFromForm.getId());
            log.info("Datos recibidos (desde formData) - FechaStr: {}, HoraStr: {}, Detalles: {}, Causa: {}",
                    explicitDeathDateStr, explicitDeathTimeStr, personFromForm.getDeathDetails(), personFromForm.getCauseOfDeath());

            LocalDateTime finalDeathDateTime = null;

            if (explicitDeathDateStr == null || explicitDeathDateStr.trim().isEmpty()) {
                result.rejectValue("deathDate", "NotEmpty", "La fecha de muerte es obligatoria.");
            }
            if (explicitDeathTimeStr == null || explicitDeathTimeStr.trim().isEmpty()) {
                result.rejectValue("deathDate", "NotEmpty.time", "La hora de muerte es obligatoria.");
            }

            if (result.hasErrors()) {
                log.warn("Errores de validación al guardar detalles de muerte para ID: {}. Errores: {}", personFromForm.getId(), result.getAllErrors());
                model.addAttribute("person", personFromForm);
                model.addAttribute("explicitDeathDateStrSubmitted", explicitDeathDateStr);
                model.addAttribute("explicitDeathTimeStrSubmitted", explicitDeathTimeStr);
                model.addAttribute("pageTitle", "Error - Detalles de Muerte para " + (personFromForm.getName() != null ? personFromForm.getName() : "ID: " + personFromForm.getId()));
                model.addAttribute("errorMessage", "Por favor corrige los errores e inténtalo de nuevo.");
                return Mono.just("details");
            }

            try {
                LocalDate datePart = LocalDate.parse(explicitDeathDateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                LocalTime timePart = LocalTime.parse(explicitDeathTimeStr, DateTimeFormatter.ISO_LOCAL_TIME);
                finalDeathDateTime = LocalDateTime.of(datePart, timePart);
                log.info("Fecha y hora de muerte combinadas: {}", finalDeathDateTime);
            } catch (DateTimeParseException e) {
                log.warn("Error al parsear fecha/hora: Date='{}', Time='{}' - Error: {}", explicitDeathDateStr, explicitDeathTimeStr, e.getMessage());
                result.rejectValue("deathDate", "invalid.datetime", "Formato de fecha u hora inválido. Use yyyy-MM-dd y HH:mm.");
            } catch (NullPointerException npe){
                log.warn("NPE al parsear fecha/hora, uno de los strings de fecha/hora es null (esto no debería pasar si la validación anterior funciona).");
                if (!result.hasFieldErrors("deathDate")) {
                    result.rejectValue("deathDate", "invalid.datetime", "Fecha y hora deben ser proporcionadas y válidas.");
                }
            }

            if (result.hasErrors()) {
                log.warn("Errores después del parseo al guardar detalles de muerte para ID: {}. Errores: {}", personFromForm.getId(), result.getAllErrors());
                model.addAttribute("person", personFromForm);
                model.addAttribute("explicitDeathDateStrSubmitted", explicitDeathDateStr);
                model.addAttribute("explicitDeathTimeStrSubmitted", explicitDeathTimeStr);
                model.addAttribute("pageTitle", "Error - Detalles de Muerte para " + (personFromForm.getName() != null ? personFromForm.getName() : "ID: " + personFromForm.getId()));
                model.addAttribute("errorMessage", "Por favor corrige los errores e inténtalo de nuevo.");
                return Mono.just("details");
            }

            // Si todo OK, proceder a guardar
            return personService.specifyDeath(personFromForm.getId(), finalDeathDateTime, personFromForm.getDeathDetails(), personFromForm.getCauseOfDeath())
                    .doOnSuccess(updatedPerson -> {
                        log.info("Detalles de muerte actualizados para {} (ID: {}).", updatedPerson.getName(), updatedPerson.getId());
                    })
                    .thenReturn("redirect:/listNames?success=" + encodeURL("Detalles de muerte actualizados para '" + personFromForm.getName() + "'."))
                    .onErrorResume(e -> {
                        log.error("Error al actualizar detalles de muerte para ID {}: {}", personFromForm.getId(), e.getMessage(), e);
                        return personService.findById(personFromForm.getId())
                                .defaultIfEmpty(personFromForm)
                                .flatMap(originalPerson -> {
                                    model.addAttribute("person", originalPerson);
                                    model.addAttribute("explicitDeathDateStrSubmitted", explicitDeathDateStr);
                                    model.addAttribute("explicitDeathTimeStrSubmitted", explicitDeathTimeStr);
                                    if (originalPerson != personFromForm) {
                                        model.addAttribute("submittedDeathDetails", personFromForm.getDeathDetails());
                                        model.addAttribute("submittedCauseOfDeath", personFromForm.getCauseOfDeath());
                                    }
                                    model.addAttribute("pageTitle", "Error - Detalles de Muerte para " + originalPerson.getName());
                                    model.addAttribute("errorMessage", "Error al guardar los detalles: " + e.getMessage());
                                    return Mono.just("details");
                                });
                    });
        });
    }
}