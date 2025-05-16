package com.springboot.webflux.deathnote.repository;

import com.springboot.webflux.deathnote.model.Person;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import java.time.LocalDateTime; // Importar LocalDateTime

public interface PersonRepository extends ReactiveMongoRepository<Person, String> {

    Flux<Person> findAllByStatusAndScheduledDeathTimeBeforeAndAlive(String status, LocalDateTime scheduledTime, boolean alive);
}