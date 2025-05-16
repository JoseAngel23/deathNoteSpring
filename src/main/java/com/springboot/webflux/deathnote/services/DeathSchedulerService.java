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

    // Ejecuta cada 5 segundos (PT5S). Puedes ajustarlo para pruebas.
    @Scheduled(fixedDelayString = "${deathnote.scheduler.fixedDelay:PT5S}") // Puedes hacerlo configurable
    public void processPendingHeartAttacks() {
        LocalDateTime currentTime = LocalDateTime.now();
        String targetStatus = "PENDING_HEART_ATTACK";
        boolean targetIsAlive = true;

        log.info("SCHEDULER: Ciclo iniciado. Hora actual: {}. Buscando: status='{}', isAlive={}, scheduledDeathTime < {}",
                currentTime, targetStatus, targetIsAlive, currentTime);

        personRepository.findAllByStatusAndScheduledDeathTimeBeforeAndAlive(targetStatus, currentTime, targetIsAlive)
                .collectList() // Colecta para loguear el tamaño de la lista encontrada
                .flatMapMany(list -> {
                    if (list.isEmpty()) {
                        log.info("SCHEDULER: No se encontraron personas para procesar en este ciclo.");
                    } else {
                        log.info("SCHEDULER: {} personas encontradas para procesar.", list.size());
                        list.forEach(p -> log.info("SCHEDULER: -> Encontrada: ID={}, Nombre={}, EstadoActual={}, Viva={}, MuerteProg={}",
                                p.getId(), p.getName(), p.getStatus(), p.isAlive(), p.getScheduledDeathTime()));
                    }
                    return Flux.fromIterable(list); // Devuelve el Flux para seguir procesando
                })
                .flatMap(person -> {
                    log.info("SCHEDULER: Procesando a: {} (ID: {}). Hora de muerte programada original: {}",
                            person.getName(), person.getId(), person.getScheduledDeathTime());

                    person.setAlive(false);
                    person.setCauseOfDeath("Ataque al Corazón (Automático por Scheduler)");
                    person.setDeathDate(person.getScheduledDeathTime()); // Asigna la hora programada como la hora de muerte real
                    person.setStatus("DEAD");

                    log.info("SCHEDULER: Intentando actualizar a {} (ID: {}) como DEAD. Fecha de muerte establecida: {}",
                            person.getName(), person.getId(), person.getDeathDate());

                    return personRepository.save(person)
                            .doOnSuccess(savedPerson -> log.info("SCHEDULER: Persona {} (ID: {}) GUARDADA exitosamente como DEAD.", savedPerson.getName(), savedPerson.getId()))
                            .doOnError(saveError -> log.error("SCHEDULER: ERROR al guardar a {} (ID: {}) como DEAD: {}", person.getName(), person.getId(), saveError.getMessage(), saveError));
                })
                .subscribe(
                        updatedPerson -> log.info("SCHEDULER: Ciclo de procesamiento para {} (ID: {}) FINALIZADO con éxito.", updatedPerson.getName(), updatedPerson.getId()),
                        error -> log.error("SCHEDULER: ERROR general en el flujo del scheduler al procesar muertes.", error),
                        () -> log.debug("SCHEDULER: Fin del ciclo de procesamiento de muertes (puede que no se hayan encontrado personas).")
                );
    }
}