package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.Owner;
import com.springboot.webflux.deathnote.model.Person;
import org.springframework.data.mongodb.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OwnerService {

    public Mono<Owner> find();

    public Mono<Owner> save(Owner owner);

    public Mono<Void> delete(Owner owner);
}