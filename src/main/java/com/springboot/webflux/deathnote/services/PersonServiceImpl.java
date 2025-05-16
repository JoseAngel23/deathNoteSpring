package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class PersonServiceImpl implements PersonService {

    private static final Logger log = LoggerFactory.getLogger(PersonServiceImpl.class);

    @Autowired
    private PersonRepository personRepository;

    @Override
    public Flux<Person> findAll() {
        return personRepository.findAll();
    }

    @Override
    public Mono<Person> findById(String id) {
        return personRepository.findById(id);
    }

    @Override
    public Mono<Person> save(Person person) {
        // Este log es útil para ver qué se está guardando exactamente
        log.info("Guardando persona en BD: ID: {}, Nombre: {}, Foto: {}, Viva: {}, Estado: {}, Fecha Muerte Prog.: {}, Causa Muerte: {}, Detalles Muerte: {}",
                person.getId(),
                person.getName(),
                person.getFacePhoto(),
                person.isAlive(),
                person.getStatus(),
                person.getScheduledDeathTime(),
                person.getCauseOfDeath(),
                person.getDeathDetails());
        return personRepository.save(person);
    }

    @Override
    public Mono<Void> delete(Person person) {
        log.debug("Eliminando persona: {}", person.getName());
        return personRepository.delete(person);
    }

    @Override
    public Mono<Person> saveInitialEntry(Person person) {
        LocalDateTime now = LocalDateTime.now();
        person.setEntryTime(now);
        person.setAlive(true); // Siempre viva al ser anotada.

        // --- LÓGICA TEMPORAL: Muerte en 40 segundos, independientemente de la foto ---
        log.info("Aplicando regla temporal: {} será programada para morir en 40 segundos.", person.getName());
        person.setScheduledDeathTime(now.plusSeconds(40));
        person.setStatus("PENDING_HEART_ATTACK"); // El scheduler buscará este estado.
        // CauseOfDeath y DeathDate real se establecerán por el scheduler.
        person.setCauseOfDeath(null);
        person.setDeathDetails("Muerte programada automáticamente a los 40s (regla temporal).");
        // person.setFacePhoto() se manejará en el controlador si se sube un archivo,
        // pero aquí no afecta la programación de la muerte.
        // --- FIN LÓGICA TEMPORAL ---

        log.info("ANOTADA (REGLA TEMPORAL 40s): {} (ID: {}). Muerte programada para las {}. Foto (si la hay, no afecta programación): {}",
                person.getName(), person.getId(), person.getScheduledDeathTime(), person.getFacePhoto());

        return this.save(person); // Llama al método save de esta clase, que luego llama al repositorio.
    }

    @Override
    public Mono<Person> specifyDeath(String personId, LocalDateTime explicitDeathDateTime, String details, String cause) {
        log.info("Servicio: Especificando muerte para ID: {}. Fecha/Hora: {}, Detalles: '{}', Causa: '{}'",
                personId, explicitDeathDateTime, details, cause);

        return personRepository.findById(personId)
                .flatMap(person -> {
                    // Opcional: Verificar si se puede modificar (ej. si no está ya 'DEAD' de forma definitiva)
                    // if ("DEAD".equals(person.getStatus()) && (person.getCauseOfDeath() != null && !person.getCauseOfDeath().contains("Automático"))) {
                    //     log.warn("Intentando especificar muerte para {} (ID: {}) que ya está muerta con causa final.", person.getName(), personId);
                    //     return Mono.error(new IllegalStateException("La persona ya está muerta y los detalles no pueden ser alterados de esta manera."));
                    // }

                    log.info("Persona encontrada para especificar muerte: {} (ID: {}). Estado actual: {}, Viva: {}",
                            person.getName(), personId, person.getStatus(), person.isAlive());

                    person.setDeathDate(explicitDeathDateTime); // Esta es la nueva fecha/hora de muerte
                    person.setDeathDetails(details);
                    person.setCauseOfDeath(cause != null && !cause.trim().isEmpty() ? cause : "Causa especificada por el usuario");
                    person.setAlive(false); // Al especificar la muerte, la persona ya no está viva (o lo estará hasta esa fecha)
                    person.setStatus("DEAD_DETAILS_SPECIFIED"); // Un nuevo estado para indicar que la muerte fue especificada
                    person.setScheduledDeathTime(null); // Anula cualquier muerte programada por ataque al corazón

                    log.info("Actualizando persona {} (ID: {}) a estado DEAD_DETAILS_SPECIFIED, deathDate: {}",
                            person.getName(), person.getId(), person.getDeathDate());

                    return this.save(person); // Reutiliza tu método save que ya tiene logging
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Servicio: Persona no encontrada con ID: {} para especificar detalles de muerte.", personId);
                    return Mono.error(new RuntimeException("Persona no encontrada con ID: " + personId + " para especificar detalles de muerte."));
                }));
    }
}