package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.DeathNote;
import com.springboot.webflux.deathnote.model.DeathNotePage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DeathNotePageService {

    public Flux<DeathNotePage> findAll();

    public Mono<DeathNotePage> findById(String id);

    public Mono<Void> delete(DeathNotePage deathNotePage);
}
