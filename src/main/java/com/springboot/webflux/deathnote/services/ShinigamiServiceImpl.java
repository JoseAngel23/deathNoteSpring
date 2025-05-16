package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.Shinigami;
import com.springboot.webflux.deathnote.repository.ShinigamiRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ShinigamiServiceImpl implements ShinigamiService{

    private static final Logger log = LoggerFactory.getLogger(ShinigamiServiceImpl.class);

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

    @Override
    public Mono<Shinigami> findByName(String name) {
        log.debug("Buscando Shinigami por nombre: {}", name);
        return repository.findByName(name);
    }
}