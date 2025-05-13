package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.Person;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.Savepoint;

public interface PersonService {

    public Flux<Person> findAll();

    public Mono<Person> findById(String id);

    public Mono<Person> save(Person person);
}
