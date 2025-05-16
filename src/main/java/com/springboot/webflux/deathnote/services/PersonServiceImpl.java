package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime; // ¡IMPORTANTE! Importar LocalDateTime

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
        log.debug("Guardando persona: {}", person.getName());
        return personRepository.save(person);
    }

    @Override
    public Mono<Void> delete(Person person) {
        log.debug("Eliminando persona: {}", person.getName());
        return personRepository.delete(person);
    }

    @Override
    public Mono<Person> saveInitialEntry(Person person) {
        LocalDateTime now = LocalDateTime.now(); // Usar LocalDateTime.now()

        person.setEntryTime(now);
        person.setScheduledDeathTime(now.plusSeconds(40));

        person.setStatus("PENDING_HEART_ATTACK");
        person.setIsAlive(true);
        person.setCauseOfDeath(null);
        person.setDeathDetails(null);

        log.info("Anotando a {} para morir de ataque al corazón en 40s. Hora de entrada: {}, Hora programada de muerte: {}",
                person.getName(), person.getEntryTime(), person.getScheduledDeathTime());

        return this.save(person);
    }
}