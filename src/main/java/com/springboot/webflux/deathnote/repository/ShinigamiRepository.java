package com.springboot.webflux.deathnote.repository;

import com.springboot.webflux.deathnote.model.Shinigami;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface ShinigamiRepository extends ReactiveMongoRepository<Shinigami, String> {
}
