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

    @Scheduled(fixedDelayString = "${deathnote.scheduler.fixedDelay:PT5S}")
    public void processPendingDeaths() {
        LocalDateTime currentTime = LocalDateTime.now();
        String logPrefixScheduler = "SCHEDULER: ";

        // 1. Procesar muertes que estaban pendientes (PENDING_HEART_ATTACK - 40s)
        //    o que estaban esperando detalles y no se guardaron (AWAITING_DETAILS - 400s)
        //    y su tiempo límite llegó.
        Flux.concat(
                        personRepository.findAllByStatusAndScheduledDeathTimeBeforeAndAlive("PENDING_HEART_ATTACK", currentTime, true),
                        personRepository.findAllByStatusAndScheduledDeathTimeBeforeAndAlive("AWAITING_DETAILS", currentTime, true)
                )
                .flatMap(person -> {
                    log.info("{}Procesando TIMEOUT para: {} (ID: {}). Estado actual: {}. Muerte prog.: {}",
                            logPrefixScheduler, person.getName(), person.getId(), person.getStatus(), person.getScheduledDeathTime());
                    person.setAlive(false);
                    person.setDeathDate(person.getScheduledDeathTime()); // Hora real de muerte es la programada
                    person.setScheduledDeathTime(null); // Limpiar, ya no está programada

                    // Establecer causa y detalles según el estado original si no se especificaron explícitamente
                    if ("PENDING_HEART_ATTACK".equals(person.getStatus())) {
                        // Ya debería tener la causa y detalles de saveInitialEntry.
                        // Solo nos aseguramos de que no sean nulos por alguna razón.
                        if (person.getCauseOfDeath() == null) {
                            person.setCauseOfDeath("Ataque al Corazón (Automático por defecto - 40s)");
                        }
                        if (person.getDeathDetails() == null) {
                            person.setDeathDetails("Ataque al corazón a los 40 segundos (sin detalles).");
                        }
                        log.info("{} {} murió por PENDING_HEART_ATTACK timeout. Causa: {}, Detalles: {}",
                                logPrefixScheduler, person.getName(), person.getCauseOfDeath(), person.getDeathDetails());

                    } else if ("AWAITING_DETAILS".equals(person.getStatus())) {
                        // El usuario entró a detalles pero no guardó a tiempo (límite de 400s)
                        // La regla es que si no se especifican detalles, es ataque al corazón.
                        person.setCauseOfDeath("Ataque al Corazón (Tiempo límite de 400s para detalles expirado)");
                        person.setDeathDetails("Muerte por ataque al corazón. El tiempo para especificar detalles (400s) expiró.");
                        log.info("{} {} murió por AWAITING_DETAILS timeout. Causa: {}, Detalles: {}",
                                logPrefixScheduler, person.getName(), person.getCauseOfDeath(), person.getDeathDetails());
                    }
                    // Si por alguna razón muy extraña el estado no es ninguno de los dos, pero fue recogido por el query:
                    // else {
                    //     person.setCauseOfDeath("Indeterminada (Scheduler Timeout)");
                    //     person.setDeathDetails("Muerte procesada por scheduler debido a timeout en estado inesperado.");
                    // }


                    person.setStatus("DEAD_BY_SCHEDULER_TIMEOUT"); // Estado final para este tipo de muerte

                    log.info("{}Intentando actualizar a {} como {}. Causa: '{}', Detalles: '{}', Fecha muerte: {}",
                            logPrefixScheduler, person.getName(), person.getStatus(), person.getCauseOfDeath(), person.getDeathDetails(), person.getDeathDate());
                    return personRepository.save(person)
                            .doOnSuccess(savedPerson -> log.info("{}{} (ID: {}) GUARDADA como {}.",
                                    logPrefixScheduler, savedPerson.getName(), savedPerson.getId(), savedPerson.getStatus()))
                            .doOnError(saveError -> log.error("{}ERROR al guardar a {} como {}: {}",
                                    logPrefixScheduler, person.getName(), person.getStatus(), saveError.getMessage()));
                })
                .subscribe(
                        updatedPerson -> log.debug("{}Ciclo (TIMEOUT) para {} finalizado.", logPrefixScheduler, updatedPerson.getName()),
                        error -> log.error("{}ERROR general en scheduler (TIMEOUT).", logPrefixScheduler, error)
                );

        // 2. Procesar muertes explícitamente programadas por el usuario (DEATH_SCHEDULED_EXPLICITLY)
        //    cuyo tiempo ha llegado. (Esta parte permanece igual)
        personRepository.findAllByStatusAndScheduledDeathTimeBeforeAndAlive("DEATH_SCHEDULED_EXPLICITLY", currentTime, true)
                .flatMap(person -> {
                    log.info("{}Procesando (Muerte Explícita Programada) a: {} (ID: {}). Muerte especificada: {}",
                            logPrefixScheduler, person.getName(), person.getId(), person.getScheduledDeathTime());
                    person.setAlive(false);
                    // Causa y Detalles ya deberían estar seteados desde PersonServiceImpl.specifyDeath
                    person.setDeathDate(person.getScheduledDeathTime());
                    person.setStatus("DEAD_DETAILS_SPECIFIED_BY_SCHEDULER"); // O simplemente DEAD_DETAILS_SPECIFIED
                    person.setScheduledDeathTime(null);
                    log.info("{}Intentando actualizar a {} como {}. Causa: '{}', Detalles: '{}', Fecha muerte: {}",
                            logPrefixScheduler, person.getName(), person.getStatus(), person.getCauseOfDeath(), person.getDeathDetails(), person.getDeathDate());
                    return personRepository.save(person)
                            .doOnSuccess(savedPerson -> log.info("{}{} (ID: {}) GUARDADA como {}.",
                                    logPrefixScheduler, savedPerson.getName(), savedPerson.getId(), savedPerson.getStatus()))
                            .doOnError(saveError -> log.error("{}ERROR al guardar a {} como {}: {}",
                                    logPrefixScheduler, person.getName(), person.getStatus(), saveError.getMessage()));
                })
                .subscribe(
                        updatedPerson -> log.debug("{}Ciclo (Muerte Explícita Programada) para {} finalizado.", logPrefixScheduler, updatedPerson.getName()),
                        error -> log.error("{}ERROR general en scheduler (Muerte Explícita Programada).", logPrefixScheduler, error)
                );
    }
}