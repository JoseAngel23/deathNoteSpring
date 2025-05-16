package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.Person;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.Savepoint;

public interface PersonService {

    public Flux<Person> findAll();

    public Mono<Person> findById(String id);

    public Mono<Person> save(Person person);

    public Mono<Void> delete(Person person);

    public Mono<Person> saveInitialEntry(Person person);
}
