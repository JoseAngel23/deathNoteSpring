package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.DeathNote;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;

public interface DeathNoteService {
    Mono<DeathNote> writePersonInDeathNote(String deathNoteId, String personId, String deathDetails,
                                           LocalDateTime deathDate, String photo);

    Mono<Void> delete(DeathNote deathNote);
    Mono<DeathNote> rejectOwnership(String deathNoteId);
    Mono<DeathNote> initializeDeathNote(String shinigamiId, String ownerId);
    Flux<DeathNote> findAll();
    Mono<DeathNote> save(DeathNote deathNote); // Ahora acepta un objeto DeathNote
}