package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.time.LocalDateTime;

@Service
public class DeathSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(DeathSchedulerService.class);

    @Autowired
    private PersonRepository personRepository;

    // En DeathSchedulerService.java
    @Scheduled(fixedDelayString = "${deathnote.scheduler.fixedDelay:PT5S}")
    public void processPendingDeaths() { // Renombrado para ser más general
        LocalDateTime currentTime = LocalDateTime.now();
        String logPrefixScheduler = "SCHEDULER: ";

        // 1. Procesar muertes automáticas por ataque al corazón (PENDING_HEART_ATTACK)
        personRepository.findAllByStatusAndScheduledDeathTimeBeforeAndAlive("PENDING_HEART_ATTACK", currentTime, true)
                .flatMap(person -> {
                    log.info("{}Procesando (Ataque Corazón) a: {} (ID: {}). Muerte prog.: {}", logPrefixScheduler, person.getName(), person.getId(), person.getScheduledDeathTime());
                    person.setAlive(false);
                    person.setCauseOfDeath("Ataque al Corazón (Automático por Scheduler)"); // Si aún usas causeOfDeath
                    person.setDeathDate(person.getScheduledDeathTime()); // Hora real de muerte
                    person.setStatus("DEAD_BY_SCHEDULER"); // O simplemente "DEAD"
                    person.setScheduledDeathTime(null); // Limpiar, ya no está programada
                    log.info("{}Intentando actualizar a {} como DEAD_BY_SCHEDULER. Fecha muerte: {}", logPrefixScheduler, person.getName(), person.getDeathDate());
                    return personRepository.save(person)
                            .doOnSuccess(savedPerson -> log.info("{}{} (ID: {}) GUARDADA como DEAD_BY_SCHEDULER.", logPrefixScheduler, savedPerson.getName(), savedPerson.getId()))
                            .doOnError(saveError -> log.error("{}ERROR al guardar a {} como DEAD_BY_SCHEDULER: {}", logPrefixScheduler, person.getName(), saveError.getMessage()));
                })
                .subscribe(
                        updatedPerson -> log.debug("{}Ciclo (Ataque Corazón) para {} finalizado.", logPrefixScheduler, updatedPerson.getName()),
                        error -> log.error("{}ERROR general en scheduler (Ataque Corazón).", logPrefixScheduler, error)
                );

        // 2. Procesar muertes explícitamente programadas (DEATH_SCHEDULED_EXPLICITLY)
        personRepository.findAllByStatusAndScheduledDeathTimeBeforeAndAlive("DEATH_SCHEDULED_EXPLICITLY", currentTime, true)
                .flatMap(person -> {
                    log.info("{}Procesando (Muerte Explícita) a: {} (ID: {}). Muerte especificada: {}", logPrefixScheduler, person.getName(), person.getId(), person.getScheduledDeathTime());
                    person.setAlive(false);
                    // person.setCauseOfDeath(person.getCauseOfDeath()); // Ya debería estar seteada desde specifyDeath
                    person.setDeathDate(person.getScheduledDeathTime()); // Hora real de muerte
                    person.setStatus("DEAD_DETAILS_SPECIFIED"); // O simplemente "DEAD"
                    person.setScheduledDeathTime(null); // Limpiar, ya no está programada
                    log.info("{}Intentando actualizar a {} como DEAD_DETAILS_SPECIFIED. Fecha muerte: {}", logPrefixScheduler, person.getName(), person.getDeathDate());
                    return personRepository.save(person)
                            .doOnSuccess(savedPerson -> log.info("{}{} (ID: {}) GUARDADA como DEAD_DETAILS_SPECIFIED.", logPrefixScheduler, savedPerson.getName(), savedPerson.getId()))
                            .doOnError(saveError -> log.error("{}ERROR al guardar a {} como DEAD_DETAILS_SPECIFIED: {}", logPrefixScheduler, person.getName(), saveError.getMessage()));
                })
                .subscribe(
                        updatedPerson -> log.debug("{}Ciclo (Muerte Explícita) para {} finalizado.", logPrefixScheduler, updatedPerson.getName()),
                        error -> log.error("{}ERROR general en scheduler (Muerte Explícita).", logPrefixScheduler, error)
                );
        // Podrías añadir un log general al final del método processPendingDeaths si no se encontró nada para procesar en ninguna categoría.
    }
}