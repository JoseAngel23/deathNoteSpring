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
        log.info("Guardando persona en BD: ID: {}, Nombre: {}, Foto: {}, Viva: {}, Estado: {}, Fecha Muerte Prog.: {}, Causa Muerte: {}, Detalles Muerte: {}, Hora Entrada: {}",
                person.getId(), person.getName(), person.getFacePhoto(), person.isAlive(),
                person.getStatus(), person.getScheduledDeathTime(), person.getCauseOfDeath(),
                person.getDeathDetails(), person.getEntryTime());
        return personRepository.save(person);
    }

    @Override
    public Mono<Void> delete(Person person) {
        log.info("Eliminando persona: {}", person.getName());
        return personRepository.delete(person);
    }

    // En PersonServiceImpl.java
    @Override
    public Mono<Person> saveInitialEntry(Person person) {
        LocalDateTime now = LocalDateTime.now();
        person.setEntryTime(now);
        person.setAlive(true);

        log.info("Aplicando regla por defecto: {} será programada para morir en 40 segundos (ataque al corazón).", person.getName());
        person.setScheduledDeathTime(now.plusSeconds(40));
        person.setStatus("PENDING_HEART_ATTACK");
        // Estos son los valores que se mantendrán si el scheduler procesa este estado
        person.setCauseOfDeath("Ataque al Corazón"); // Más conciso para la causa
        person.setDeathDetails("Muerte automática por ataque al corazón a los 40 segundos (sin detalles especificados).");

        log.info("ANOTADA (REGLA POR DEFECTO 40s): {} (ID: {}). Muerte programada para las {}. Hora Entrada: {}",
                person.getName(), person.getId(), person.getScheduledDeathTime(), person.getEntryTime());

        return this.save(person);
    }

    @Override
    public Mono<Person> specifyDeath(String personId, LocalDateTime explicitDeathDateTime, String deathDetails, String causeOfDeath) {
        log.info("Servicio: Especificando muerte para ID: {}. Fecha/Hora: {}, Detalles: '{}', Causa: '{}'",
                personId, explicitDeathDateTime, deathDetails, causeOfDeath);

        return personRepository.findById(personId)
                .flatMap(person -> {
                    log.info("Persona encontrada para especificar muerte: {} (ID: {}). Estado actual: {}, Viva: {}, Hora Entrada: {}",
                            person.getName(), person.getId(), person.getStatus(), person.isAlive(), person.getEntryTime());

                    person.setDeathDetails(deathDetails);
                    person.setCauseOfDeath(causeOfDeath); // Asignar la causa de muerte proporcionada

                    // La regla de los 400 segundos se aplica si no se especifica una hora explícita que sea anterior.
                    // El `explicitDeathDateTime` viene del formulario del usuario.
                    // Si el usuario está en la página de detalles, es porque ya se aplicó (o se intentó aplicar) la extensión a 400s
                    // en el `showDeathDetailsForm`. Ahora el usuario puede confirmar esa hora o cambiarla.

                    LocalDateTime now = LocalDateTime.now();
                    if (explicitDeathDateTime != null) {
                        if (explicitDeathDateTime.isBefore(now.plusSeconds(1)) || explicitDeathDateTime.isEqual(now.plusSeconds(1))) { // Con un pequeño margen
                            person.setAlive(false);
                            person.setDeathDate(explicitDeathDateTime); // Fecha/hora real de muerte
                            person.setStatus("DEAD_DETAILS_SPECIFIED");
                            person.setScheduledDeathTime(null); // Ya no está programada, ocurrió
                            log.info("La fecha de muerte especificada ({}) ya pasó o es inminente. Marcando como muerta.", explicitDeathDateTime);
                        } else {
                            // La fecha especificada es en el futuro
                            person.setAlive(true);
                            person.setDeathDate(null);
                            person.setScheduledDeathTime(explicitDeathDateTime); // El scheduler usará esta
                            person.setStatus("DEATH_SCHEDULED_EXPLICITLY");
                            log.info("Muerte programada explícitamente para {} en {}.", person.getName(), explicitDeathDateTime);
                        }
                    } else {
                        // Esto no debería ocurrir si la validación del controlador es correcta,
                        // ya que explicitDeathDateTime es requerido.
                        log.error("explicitDeathDateTime fue null en specifyDeath para persona ID: {}. Esto es un error.", personId);
                        return Mono.error(new IllegalArgumentException("La fecha y hora de muerte explícitas son requeridas."));
                    }

                    log.info("Actualizando persona {} (ID: {}) a estado {}, deathDate: {}, scheduledDeathTime: {}, alive: {}",
                            person.getName(), person.getId(), person.getStatus(), person.getDeathDate(), person.getScheduledDeathTime(), person.isAlive());
                    return this.save(person);
                })
                .switchIfEmpty(Mono.error(new RuntimeException("Persona no encontrada con ID: " + personId + " al intentar especificar muerte.")));
    }
}