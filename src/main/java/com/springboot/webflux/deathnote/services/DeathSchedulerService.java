package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.time.LocalDateTime; // Importar LocalDateTime

@Service
public class DeathSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(DeathSchedulerService.class);

    @Autowired
    private PersonRepository personRepository;

    @Scheduled(fixedDelayString = "PT5S")
    public void processPendingHeartAttacks() {
        LocalDateTime currentTime = LocalDateTime.now(); // USAR LocalDateTime
        log.debug("SCHEDULER: Verificando muertes pendientes por ataque al corazón antes de {}", currentTime);

        Flux<Person> dueForHeartAttack = personRepository.findAllByStatusAndScheduledDeathTimeBeforeAndIsAlive(
                "PENDING_HEART_ATTACK", currentTime, true); // Ahora 'currentTime' es LocalDateTime

        dueForHeartAttack
                .flatMap(person -> {
                    log.info("SCHEDULER: Procesando muerte por ataque al corazón para: {} (ID: {})", person.getName(), person.getId());
                    person.setIsAlive(false); // Usar el setter correcto
                    person.setCauseOfDeath("Ataque al Corazón (Automático)");
                    // person.getScheduledDeathTime() ahora devuelve LocalDateTime
                    person.setDeathDate(person.getScheduledDeathTime()); // Correcto: setDeathDate(LocalDateTime) recibe LocalDateTime
                    person.setStatus("DEAD");
                    return personRepository.save(person);
                })
                .subscribe(
                        savedPerson -> log.info("SCHEDULER: {} ha sido marcada como muerta (Ataque al Corazón).", savedPerson.getName()),
                        error -> log.error("SCHEDULER: Error al procesar muerte por ataque al corazón.", error),
                        () -> log.debug("SCHEDULER: Ciclo de verificación de ataques al corazón completado.")
                );
    }
}