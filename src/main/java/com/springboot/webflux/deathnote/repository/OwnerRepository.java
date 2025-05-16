package com.springboot.webflux.deathnote.repository;

import com.springboot.webflux.deathnote.model.Owner;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface OwnerRepository extends ReactiveMongoRepository<Owner, String> {

    Mono<Owner> findFirstBy();

    Mono<Owner> findByName(String name);
}
