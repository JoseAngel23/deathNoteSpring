package com.springboot.webflux.deathnote.repository;

import com.springboot.webflux.deathnote.model.Person;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface PersonRepository extends ReactiveMongoRepository<Person, String> {}