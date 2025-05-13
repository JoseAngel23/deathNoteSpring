package com.springboot.webflux.deathnote.repository;

import com.springboot.webflux.deathnote.model.DeathNotePage;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface DeathNotePageRepository extends ReactiveMongoRepository<DeathNotePage, String> {
}
