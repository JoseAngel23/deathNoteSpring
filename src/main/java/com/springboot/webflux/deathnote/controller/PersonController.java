package com.springboot.webflux.deathnote.controller;

import com.springboot.webflux.deathnote.model.DeathNote;
import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.repository.DeathNoteRepository;
import com.springboot.webflux.deathnote.services.DeathNoteService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.SessionStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;

@Controller
public class PersonController {

    @Autowired
    private PersonService personService;

    @Autowired
    private DeathNoteService deathNoteService;

    private static final Logger log = LoggerFactory.getLogger(PersonController.class);
    @Autowired
    private DeathNoteRepository deathNoteRepository;

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

        Flux<DeathNote> deathNotes = deathNoteRepository.findAll(); // Asumiendo que tienes acceso al repositorio
        model.addAttribute("deathNotes", deathNotes);

        return Mono.just("index");
    }

    @PostMapping({"/list", "/"})
    public Mono<String> save(@Valid Person person, BindingResult result, Model model, SessionStatus status,
                             @RequestParam String deathNoteId, @RequestParam String photo) {
        if (result.hasErrors()) {
            model.addAttribute("title", "Errores en el formulario");
            model.addAttribute("button", "Reintentar Guardar");

            Flux<Person> peopleFlux = personService.findAll().map(p -> {
                p.setName(p.getName().toUpperCase());
                return p;
            });
            model.addAttribute("people", peopleFlux);
            model.addAttribute("deathNotes", deathNoteRepository.findAll());
            return Mono.just("index");
        }

        // Guardar la persona y escribir en la DeathNote
        return personService.save(person)
                .flatMap(savedPerson -> deathNoteService.writePersonInDeathNote(
                        deathNoteId, savedPerson.getId(), person.getDeathDetails(), person.getDeathDate(), photo))
                .doOnNext(deathNote -> {
                    log.info("Persona guardada y escrita en DeathNote: " + person.getName());
                    status.setComplete();
                })
                .thenReturn("redirect:/list?success=Persona+escrita+exitosamente")
                .onErrorResume(e -> {
                    log.error("Error: " + e.getMessage());
                    return Mono.just("redirect:/list?error=" + e.getMessage());
                });
    }

    @GetMapping("/delete/{id}")
    public Mono<String> deletePerson(@PathVariable String id) {
        return personService.findById(id)
                .flatMap(person -> personService.delete(person)
                        .thenReturn("redirect:/list?success=Persona+eliminada+exitosamente"))
                .switchIfEmpty(Mono.just("redirect:/list?error=Persona+no+encontrada"))
                .onErrorResume(e -> Mono.just("redirect:/list?error=Error+al+eliminar"));
    }

    @GetMapping("/deathnote/reject/{id}")
    public Mono<String> rejectOwnership(@PathVariable String id) {
        return deathNoteService.rejectOwnership(id)
                .thenReturn("redirect:/list?success=Propiedad+rechazada+exitosamente")
                .onErrorResume(e -> Mono.just("redirect:/list?error=Error+al+rechazar+propiedad"));
    }
}