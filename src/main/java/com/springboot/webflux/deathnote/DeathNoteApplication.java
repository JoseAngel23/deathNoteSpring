package com.springboot.webflux.deathnote;

import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;

import java.util.Scanner;

@SpringBootApplication
public class DeathNoteApplication implements CommandLineRunner {

    @Autowired
    private PersonRepository repository;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    private static final Logger log = LoggerFactory.getLogger(DeathNoteApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(DeathNoteApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

        mongoTemplate.dropCollection("people").subscribe();

        Flux.just(new Person("Jose Forero", true, null, null),
                new Person("Pepito Perez", true, null, null),
                new Person("Juan Sanchez", true, null, null))
                .flatMap(person -> {
                    return repository.save(person);
                })
                .subscribe(person -> log.info("Insert: " + person.getId() + " " + person.getName()));
    }
}