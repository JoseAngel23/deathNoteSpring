package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.DeathNote;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;

public interface DeathNoteService {

    Mono<Void> delete(DeathNote deathNote);

    Mono<DeathNote> writePersonInDeathNote(String deathNoteId, String personId, String deathDetails, Date deathDate, String photo);

    Mono<DeathNote> rejectOwnership(String deathNoteId);

    Mono<DeathNote> initializeDeathNote(String shinigamiId, String ownerId);

    Flux<DeathNote> findAll();
}