package com.springboot.webflux.deathnote.repository;

import com.springboot.webflux.deathnote.controller.PersonController;
import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.services.PersonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class PersonServiceImpl implements PersonService {

    @Autowired
    private PersonRepository repository;

    @Override
    public Flux<Person> findAll() {
        return repository.findAll();
    }

    @Override
    public Mono<Person> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Mono<Person> save(Person person) {
        return repository.save(person);
    }
}
