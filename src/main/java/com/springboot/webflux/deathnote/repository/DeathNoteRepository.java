package com.springboot.webflux.deathnote.repository;

import com.springboot.webflux.deathnote.model.DeathNote;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface DeathNoteRepository extends ReactiveMongoRepository<DeathNote, String> {
}
