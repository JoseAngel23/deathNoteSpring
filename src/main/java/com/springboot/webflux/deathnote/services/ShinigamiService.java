package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.Shinigami;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ShinigamiService {

    public Flux<Shinigami> find();

    public Mono<Shinigami> save(Shinigami shinigami);

    public Mono<Void> delete(Shinigami shinigami);

    Mono<Shinigami> findByName(String name);
}
