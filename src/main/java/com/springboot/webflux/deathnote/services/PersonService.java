package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.Person;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.Savepoint;
import java.time.LocalDateTime;

public interface PersonService {

    public Flux<Person> findAll();

    public Mono<Person> findById(String id);

    public Mono<Person> save(Person person);

    public Mono<Void> delete(Person person);

    public Mono<Person> saveInitialEntry(Person person);

    Mono<Person> specifyDeath(String personId, LocalDateTime explicitDeathDateTime, String details, String cause);
}
