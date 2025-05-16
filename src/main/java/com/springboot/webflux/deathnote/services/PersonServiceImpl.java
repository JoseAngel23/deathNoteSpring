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

    // En PersonServiceImpl.java
    public Mono<Person> specifyDeath(String personId, LocalDateTime explicitDeathDateTime, String deathDetails, String causeOfDeath /* o elimínalo si ya no lo usas */) {
        log.info("Servicio: Especificando muerte para ID: {}. Fecha/Hora: {}, Detalles: '{}', Causa: '{}'",
                personId, explicitDeathDateTime, deathDetails, causeOfDeath);

        return personRepository.findById(personId)
                .flatMap(person -> {
                    log.info("Persona encontrada para especificar muerte: {} (ID: {}). Estado actual: {}, Viva: {}",
                            person.getName(), person.getId(), person.getStatus(), person.isAlive());

                    person.setDeathDetails(deathDetails);
                    // if (causeOfDeath != null) { // Solo si todavía usas causeOfDeath
                    //     person.setCauseOfDeath(causeOfDeath);
                    // }

                    // Lógica crucial para determinar el estado 'alive' y la programación
                    if (explicitDeathDateTime != null && explicitDeathDateTime.isBefore(LocalDateTime.now().plusSeconds(1))) { // Un pequeño margen por si es "justo ahora"
                        // La fecha especificada ya pasó o es inminente
                        person.setAlive(false);
                        person.setDeathDate(explicitDeathDateTime); // Esta es la fecha/hora real de muerte
                        person.setStatus("DEAD_DETAILS_SPECIFIED");
                        person.setScheduledDeathTime(null); // Ya no hay muerte programada, ya ocurrió
                        log.info("La fecha de muerte especificada ({}) ya pasó o es inminente. Marcando como muerta.", explicitDeathDateTime);
                    } else if (explicitDeathDateTime != null) {
                        // La fecha especificada es en el futuro
                        person.setAlive(true); // Sigue viva
                        person.setDeathDate(null); // La muerte real aún no ha ocurrido
                        person.setScheduledDeathTime(explicitDeathDateTime); // El scheduler usará esta fecha/hora
                        person.setStatus("DEATH_SCHEDULED_EXPLICITLY"); // Nuevo estado para que el scheduler lo maneje
                        log.info("Muerte programada explícitamente para {} en {}.", person.getName(), explicitDeathDateTime);
                    } else {
                        // No se proporcionó explicitDeathDateTime, esto no debería pasar si la validación del controlador es correcta.
                        // O podrías decidir qué hacer en este caso, ¿mantener la programación original?
                        // Por ahora, asumimos que explicitDeathDateTime siempre vendrá si no hay errores de validación.
                        log.warn("explicitDeathDateTime fue null en specifyDeath, lo cual no debería ocurrir si la validación del controlador está activa.");
                        // Podrías retornar Mono.error() o manejarlo de otra forma.
                    }

                    log.info("Actualizando persona {} (ID: {}) a estado {}, deathDate: {}, scheduledDeathTime: {}, alive: {}",
                            person.getName(), person.getId(), person.getStatus(), person.getDeathDate(), person.getScheduledDeathTime(), person.isAlive());

                    // Loguear todos los campos antes de guardar para máxima claridad
                    log.info("Guardando persona en BD: ID: {}, Nombre: {}, Foto: {}, Viva: {}, Estado: {}, Fecha Muerte Real: {}, Fecha Muerte Prog.: {}, Causa Muerte: {}, Detalles Muerte: {}",
                            person.getId(), person.getName(), person.getFacePhoto(), person.isAlive(), person.getStatus(), person.getDeathDate(), person.getScheduledDeathTime(), person.getCauseOfDeath(), person.getDeathDetails());

                    return personRepository.save(person);
                })
                .switchIfEmpty(Mono.error(new RuntimeException("Persona no encontrada con ID: " + personId + " al intentar especificar muerte.")));
    }
}