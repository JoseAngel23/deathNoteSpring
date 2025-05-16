package com.springboot.webflux.deathnote.controller;

import com.springboot.webflux.deathnote.model.DeathNote;
import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.services.DeathNoteService;
import com.springboot.webflux.deathnote.services.PersonService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource; // Para viewPhoto
import org.springframework.core.io.UrlResource; // Para viewPhoto
import org.springframework.http.HttpHeaders; // Para viewPhoto
import org.springframework.http.ResponseEntity; // Para viewPhoto
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.SessionStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers; // Para operaciones de archivo

import java.io.File;
import java.net.MalformedURLException; // Para viewPhoto
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
// Elimina java.util.Date si ya no se usa en esta clase
import java.util.UUID;

@Controller
public class PersonController {
    private final PersonService personService;
    private final DeathNoteService deathNoteService;

    // ... tus autowired y otros campos ...

    @Value("${deathnote.upload.path}") // Para mostrar fotos, asegúrate que sea la misma ruta de subida
    private String photoDisplayPath;

    // Este uploadPath se usa en el método save, debería ser el mismo que photoDisplayPath
    // o tener una configuración clara para cada uno.
    @Value("${deathnote.upload.path:/tmp/deathnote_uploads}")
    private String uploadPath;

    public static final Logger log = LoggerFactory.getLogger(PersonController.class);

    public PersonController(PersonService personService, DeathNoteService deathNoteService) {
        this.personService = personService;
        this.deathNoteService = deathNoteService;
    }

    @GetMapping("/upload/img/{photoName:.+}") // Añadido / al inicio
    public Mono<ResponseEntity<Resource>> viewPhoto(@PathVariable String photoName) {
        try {
            // Usar la variable correcta para la ruta base de las fotos
            Path filePath = Paths.get(this.photoDisplayPath).resolve(photoName).toAbsolutePath();
            Resource resource = new UrlResource(filePath.toUri());

            return Mono.just(resource)
                    .filter(r -> r.exists() && r.isReadable())
                    .map(res -> ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + res.getFilename() + "\"") // Cambiado a inline
                            .body(res))
                    .switchIfEmpty(Mono.fromCallable(() -> {
                        log.warn("Recurso no encontrado o no legible: {}", photoName);
                        return ResponseEntity.notFound().build();
                    }));
        } catch (MalformedURLException e) {
            log.error("URL mal formada para el recurso {}: {}", photoName, e.getMessage());
            return Mono.just(ResponseEntity.badRequest().build()); // O un error 500
        } catch (Exception e) {
            log.error("Error inesperado al intentar servir la foto {}: {}", photoName, e.getMessage());
            return Mono.just(ResponseEntity.status(500).build());
        }
    }

    // ... tu método list(...) parece estar bien ...
    @GetMapping({"/list", "/"})
    public Mono<String> list(Model model) {
        Flux<Person> peopleFlux = personService.findAll().map(person -> {
            person.setName(person.getName().toUpperCase());
            return person;
        });
        model.addAttribute("people", peopleFlux);
        model.addAttribute("title", "Listado de Personas");
        model.addAttribute("person", new Person());
        model.addAttribute("button", "Añadir Persona");
        Flux<DeathNote> deathNotes = deathNoteService.findAll();
        model.addAttribute("deathNotes", deathNotes);
        return Mono.just("index");
    }


    // Método view
    @GetMapping("/view/{id}") // Corregido: path variable debe coincidir con el nombre del parámetro
    public Mono<String> view(Model model, @PathVariable String id) {
        return personService.findById(id)
                .doOnNext(person -> {
                    log.info("Viendo persona: {}", person.getName());
                    model.addAttribute("person", person); // El objeto person encontrado se añade al modelo
                    model.addAttribute("title", "Detalle Persona: " + person.getName());
                })
                .thenReturn("view") // Si se encuentra la persona, se va a la plantilla "view"
                .switchIfEmpty(Mono.defer(() -> { // Si findById está vacío (no se encontró)
                    log.warn("Intento de ver persona no encontrada con ID: {}", id);
                    // Redirigir con un mensaje de error. No puedes añadir al modelo aquí y luego redirigir fácilmente.
                    String errorMessage = "Persona no encontrada con ID: " + id;
                    try {
                        // Es importante codificar el mensaje de error para la URL
                        return Mono.just("redirect:/list?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.name()));
                    } catch (java.io.UnsupportedEncodingException e) {
                        log.error("Error de codificación de URL", e);
                        return Mono.just("redirect:/list?error=Error+al+procesar+mensaje");
                    }
                }))
                .onErrorResume(e -> { // Capturar otros errores inesperados durante findById o doOnNext
                    log.error("Error al intentar ver persona con ID {}: {}", id, e.getMessage());
                    String errorMessage = "Error inesperado al obtener la persona.";
                    try {
                        return Mono.just("redirect:/list?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.name()));
                    } catch (java.io.UnsupportedEncodingException encodingException) {
                        log.error("Error de codificación de URL", encodingException);
                        return Mono.just("redirect:/list?error=Error+inesperado");
                    }
                });
    }


    @PostMapping({"/list", "/"})
    public Mono<String> save(@Valid Person person, BindingResult result, Model model, SessionStatus status,
                             @RequestParam String deathNoteId,
                             @RequestParam(name = "file", required = false) FilePart file) {

        person.setDeathNoteId(deathNoteId); // Asignar el ID de la DeathNote a la persona

        if (result.hasErrors()) {
            log.warn("Errores de validación en el formulario para la persona: {}", person.getName());
            result.getAllErrors().forEach(err -> log.warn("Validation Error: {}", err));
            model.addAttribute("title", "Errores en el formulario");
            model.addAttribute("button", "Reintentar Guardar");
            Flux<Person> peopleFlux = personService.findAll().map(p -> {
                p.setName(p.getName().toUpperCase());
                return p;
            });
            model.addAttribute("people", peopleFlux);
            model.addAttribute("deathNotes", deathNoteService.findAll());
            // 'person' con errores ya está en el modelo si se usa th:object
            return Mono.just("index");
        }

        // Lógica de guardado de archivo
        Mono<String> photoFilenameMono;
        if (file != null && file.filename() != null && !file.filename().isEmpty()) {
            String uniqueFilename = UUID.randomUUID().toString() + "-" +
                    file.filename().replace(" ", "")
                            .replace(":", "").replace("\\", "");
            Path targetPath = Paths.get(uploadPath).resolve(uniqueFilename);

            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) {
                boolean dirCreated = uploadDir.mkdirs();
                if (!dirCreated) {
                    log.error("No se pudo crear el directorio de subida: {}", uploadPath);
                    // Manejar error, quizás devolver al formulario con mensaje
                }
            }
            log.info("Guardando archivo {} en {}", uniqueFilename, targetPath);
            photoFilenameMono = file.transferTo(targetPath) // Esto devuelve Mono<Void>
                    .thenReturn(uniqueFilename) // Devolver el nombre del archivo en caso de éxito
                    .doOnError(e -> log.error("Error al transferir archivo {}", file.filename(), e));
        } else {
            photoFilenameMono = Mono.justOrEmpty(person.getFacePhoto()); // Usar foto existente o null si no hay nueva
        }

        return photoFilenameMono
                .doOnNext(filename -> person.setFacePhoto(filename)) // Establecer el nombre de la foto en el objeto person
                .defaultIfEmpty(person.getFacePhoto()) // Si photoFilenameMono estaba vacío, usa el valor que ya tenía person.facePhoto
                .flatMap(photoName -> { // photoName es el nombre del archivo o null
                    // Ahora, decidir si es una entrada inicial o una actualización
                    if (person.getId() == null || person.getId().isEmpty()) {
                        log.info("Llamando a personService.saveInitialEntry para: {}", person.getName());
                        return personService.saveInitialEntry(person); // Configura para muerte en 40s
                    } else {
                        log.info("Llamando a personService.save (actualización) para: {}", person.getName());
                        // Para una actualización, deberías recuperar la persona existente para no perder
                        // entryTime, scheduledDeathTime, status si no se envían desde el form de edición.
                        // Por ahora, un save simple que podría sobrescribir esos campos si 'person' no los tiene.
                        return personService.save(person);
                    }
                })
                .flatMap(savedPerson -> {
                    log.info("Persona guardada/actualizada: {}. Ahora asociando con DeathNote ID: {}", savedPerson.getName(), deathNoteId);
                    // savedPerson.getDeathDate() ahora es LocalDateTime, lo cual coincide con el servicio
                    return deathNoteService.writePersonInDeathNote(
                            deathNoteId,
                            savedPerson.getId(),
                            savedPerson.getDeathDetails(),
                            savedPerson.getDeathDate(), // Esto es LocalDateTime
                            savedPerson.getFacePhoto()
                    );
                })
                .doOnSuccess(dn -> { // dn es el DeathNote devuelto por writePersonInDeathNote
                    log.info("Persona {} asociada a DeathNote ID: {}. Completando status de sesión.", person.getName(), dn.getId());
                    status.setComplete();
                })
                .thenReturn("redirect:/list?success=Persona+procesada+exitosamente")
                .onErrorResume(e -> {
                    log.error("Error final en el flujo de guardado de persona: {}", e.getMessage(), e);
                    model.addAttribute("title", "Error al procesar la solicitud");
                    model.addAttribute("button", "Reintentar Guardar");
                    Flux<Person> peopleFluxOnError = personService.findAll().map(p -> {
                        p.setName(p.getName().toUpperCase());
                        return p;
                    });
                    model.addAttribute("people", peopleFluxOnError);
                    model.addAttribute("deathNotes", deathNoteService.findAll());
                    model.addAttribute("person", person);
                    model.addAttribute("errorMessage", "Error: " + e.getMessage().replace("\n", " "));
                    return Mono.just("index");
                });
    }

    // En PersonController.java
// (Asegúrate de tener las importaciones correctas, incluyendo java.net.URLEncoder y java.nio.charset.StandardCharsets)

    @GetMapping("/delete/{id}")
    public Mono<String> deletePerson(@PathVariable String id) {
        return personService.findById(id) // Devuelve Mono<Person>
                .flatMap(person -> {
                    log.info("Intentando eliminar persona: {} (ID: {})", person.getName(), person.getId());
                    // Aquí la lógica para eliminar el archivo de foto si existe (como te comenté)
                    // ...
                    return personService.delete(person) // Asumiendo que delete(person) devuelve Mono<Void>
                            .thenReturn("redirect:/list?success=Persona+eliminada+exitosamente"); // Hace que el flatMap devuelva Mono<String>
                })
                .switchIfEmpty(Mono.defer(() -> { // Se ejecuta si findById devuelve Mono.empty()
                    log.warn("Intento de eliminar persona no encontrada con ID: {}", id);
                    String errorMessage = "Persona no encontrada para eliminar con ID: " + id;
                    try {
                        return Mono.just("redirect:/list?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.name()));
                    } catch (java.io.UnsupportedEncodingException e) {
                        return Mono.just("redirect:/list?error=Error+codificando+mensaje");
                    }
                }))
                .onErrorResume(e -> { // Captura errores de findById, delete, o cualquier otro en la cadena
                    log.error("Error en operación de eliminar para ID {}: {}", id, e.getMessage(), e);
                    String errorMessage = "Error al eliminar la persona.";
                    try {
                        return Mono.just("redirect:/list?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.name()));
                    } catch (java.io.UnsupportedEncodingException encodingException) {
                        return Mono.just("redirect:/list?error=Error+inesperado");
                    }
                });
    }

    @GetMapping("/deathnote/reject/{id}")
    public Mono<String> rejectOwnership(@PathVariable String id) {
        return deathNoteService.rejectOwnership(id)
                .thenReturn("redirect:/list?success=Propiedad+rechazada+exitosamente")
                .onErrorResume(e -> {
                    log.error("Error al rechazar propiedad de DeathNote ID {}: {}", id, e.getMessage(), e);
                    return Mono.just("redirect:/list?error=Error+al+rechazar+propiedad");
                });
    }
}