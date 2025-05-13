package com.springboot.webflux.deathnote.controller;

import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.services.PersonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

        // Es buena práctica dejar que Thymeleaf maneje la suscripción al Flux reactivo.
        // La suscripción explícita aquí podría tener efectos secundarios o consumir el Flux antes de tiempo.
        // peopleFlux.subscribe(per -> log.info(per.getName())); // Considera mover el logging o manejarlo de otra forma si es necesario.

        model.addAttribute("people", peopleFlux); // Para la tabla que lista las personas
        model.addAttribute("title", "Listado de Personas");

        // --- ESTA ES LA LÍNEA IMPORTANTE QUE HAY QUE AÑADIR ---
        model.addAttribute("person", new Person()); // Para el formulario en index.html

        return Mono.just("index");
    }

    @GetMapping("/form") // Este método podría ser para editar una persona existente si pasas un ID
    public Mono<String> crear(Model model) { // O si quieres una página separada solo para el formulario
        model.addAttribute("person", new Person());
        model.addAttribute("title", "Formulario de Persona");
        // Si tu intención es siempre tener el formulario en index.html,
        // este endpoint "/form" con GET quizás ya no sea tan necesario
        // a menos que lo uses para editar.
        return Mono.just("form"); // O "index" si quieres reusar la misma vista y el modelo está preparado
    }

    @PostMapping("/form")
    public Mono<String> save(Person person) { // Spring WebFlux puede enlazar el objeto Person directamente desde los datos del formulario
        return service.save(person).doOnNext(p -> {
            log.info("Persona guardada: " + p.getName() + " Id: " + p.getId());
        }).thenReturn("redirect:/list?success"); // Redirige a la lista (puedes añadir un parámetro para mensajes de éxito)
        // Considera usar Post/Redirect/Get pattern
    }
}