package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.DeathNote;
import com.springboot.webflux.deathnote.repository.DeathNoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class DeathNoteServiceImpl implements DeathNoteService{

    @Autowired
    private DeathNoteRepository repository;

    @Override
    public Mono<Void> delete(DeathNote deathNote) {
        return repository.delete(deathNote);
    }
}
