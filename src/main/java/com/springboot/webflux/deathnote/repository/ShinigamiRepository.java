package com.springboot.webflux.deathnote.repository;

import com.springboot.webflux.deathnote.model.Shinigami;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface ShinigamiRepository extends ReactiveMongoRepository<Shinigami, String> {
    Mono<Shinigami> findByName(String name);
}
