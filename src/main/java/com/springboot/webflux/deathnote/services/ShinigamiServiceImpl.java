package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.Shinigami;
import com.springboot.webflux.deathnote.repository.ShinigamiRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ShinigamiServiceImpl implements ShinigamiService{

    @Autowired
    private ShinigamiRepository repository;

    @Override
    public Flux<Shinigami> find() {
        return null;
    }

    @Override
    public Mono<Shinigami> save(Shinigami shinigami) {
        return repository.save(shinigami);
    }

    @Override
    public Mono<Void> delete(Shinigami shinigami) {
        return repository.delete(shinigami);
    }
}