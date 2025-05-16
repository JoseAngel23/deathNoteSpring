// En: com/springboot/webflux/deathnote/services/PersonServiceImpl.java

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
        person.setIsAlive(true); // Siempre viva al ser anotada.

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
}