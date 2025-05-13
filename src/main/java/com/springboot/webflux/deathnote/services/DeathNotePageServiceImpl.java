package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.DeathNotePage;
import com.springboot.webflux.deathnote.repository.DeathNotePageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class DeathNotePageServiceImpl implements DeathNotePageService{

    @Autowired
    private DeathNotePageRepository repository;

    @Override
    public Flux<DeathNotePage> findAll() {
        return repository.findAll();
    }

    @Override
    public Mono<DeathNotePage> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Mono<Void> delete(DeathNotePage deathNotePage) {
        return repository.delete(deathNotePage);
    }
}
