package com.springboot.webflux.deathnote.controller;

import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.services.PersonService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.support.SessionStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;

@Controller
public class PersonController {

    @Autowired
    private PersonService service;

    private static final Logger log = LoggerFactory.getLogger(PersonController.class);

    @GetMapping({"/list", "/"})
    public Mono<String> list(Model model) {

        Flux<Person> peopleFlux = service.findAll().map(person -> {
            person.setName(person.getName().toUpperCase());
            return person;
        });

        model.addAttribute("people", peopleFlux);
        model.addAttribute("title", "Listado de Personas");

        // --- ESTA ES LA LÍNEA IMPORTANTE QUE HAY QUE AÑADIR ---
        model.addAttribute("person", new Person()); // Para el formulario en index.html
        model.addAttribute("button", "Añadir Persona");

        return Mono.just("index");
    }

    @PostMapping({"/list", "/"})
    public Mono<String> save(@Valid Person person, BindingResult result, Model model, SessionStatus status) {
        if (result.hasErrors()) {
            model.addAttribute("title", "Hay problemas con el formulario. Por favor, corrige los errores.");
            model.addAttribute("button", "Reintentar Guardar");

            Flux<Person> peopleFlux = service.findAll().map(p -> {
                p.setName(p.getName().toUpperCase());
                return p;
            });
            model.addAttribute("people", peopleFlux);
            return Mono.just("index");
        } else {
            if (person.getDeathDate() == null) {
                person.setDeathDate(new Date());
            }
            status.setComplete();
            return service.save(person).doOnNext(p -> {
                log.info("Persona guardada: " + p.getName() + " Id: " + p.getId());
            }).thenReturn("redirect:/list?success=Persona+guardada+exitosamente");
        }
    }

    // En PersonController.java
    @GetMapping("/delete/{id}")
    public Mono<String> deletePerson(@PathVariable String id) {
        return service.findById(id) // Paso 1: Buscar la persona
                .flatMap(personEncontrada -> {
                    // Paso 2: Si se encuentra, proceder a borrarla.
                    // Asumimos que service.delete() devuelve Mono<Void> o Mono<AlgunaConfirmacion>
                    log.info("Intentando eliminar persona: {}", personEncontrada.getName());
                    return service.delete(personEncontrada)
                            .thenReturn("redirect:/list?success=Persona+eliminada+exitosamente") // Éxito en el borrado
                            .doOnError(e -> log.error("Error durante la eliminación de la persona con ID {}: {}", personEncontrada.getId(), e.getMessage())); // Log del error específico de borrado
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Paso 3: Si findById devuelve Mono.empty() (persona no encontrada)
                    log.warn("Intento de eliminar persona no encontrada con ID: {}", id);
                    return Mono.just("redirect:/list?error=Persona+no+encontrada+para+eliminar");
                }))
                .onErrorResume(Exception.class, e -> {
                    // Paso 4: Capturar cualquier otro error inesperado en la cadena (ej. problemas de base de datos en delete)
                    log.error("Error general en la operación de eliminar para ID {}: {}", id, e.getMessage());
                    return Mono.just("redirect:/list?error=Error+inesperado+al+intentar+eliminar+la+persona");
                });
    }
}