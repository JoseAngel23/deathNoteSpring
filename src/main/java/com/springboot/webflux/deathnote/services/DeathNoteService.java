package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.DeathNote;
import reactor.core.publisher.Mono;

public interface DeathNoteService {

    public Mono<Void> delete(DeathNote deathNote);
}
