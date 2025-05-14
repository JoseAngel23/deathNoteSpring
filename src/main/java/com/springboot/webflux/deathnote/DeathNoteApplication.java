package com.springboot.webflux.deathnote;

import com.springboot.webflux.deathnote.model.DeathNote;
import com.springboot.webflux.deathnote.model.Owner;
import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.model.Shinigami;
import com.springboot.webflux.deathnote.repository.PersonRepository;
import com.springboot.webflux.deathnote.repository.ShinigamiRepository;
import com.springboot.webflux.deathnote.services.DeathNoteService;
import com.springboot.webflux.deathnote.services.OwnerService;
import com.springboot.webflux.deathnote.services.PersonService;
import com.springboot.webflux.deathnote.services.ShinigamiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Scanner;

@SpringBootApplication
public class DeathNoteApplication implements CommandLineRunner {

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private ShinigamiRepository shinigamiRepository;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    private static final Logger log = LoggerFactory.getLogger(DeathNoteApplication.class);
    @Autowired
    private ShinigamiService shinigamiService;
    @Autowired
    private OwnerService ownerService;
    @Autowired
    private DeathNoteService deathNoteService;
    @Autowired
    private PersonService personService;

    public static void main(String[] args) {
        SpringApplication.run(DeathNoteApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // Crear un Shinigami
        Shinigami ryuk = new Shinigami("Ryuk", null);
        Mono<Shinigami> shinigamiMono = shinigamiService.save(ryuk);

        // Crear un Owner
        Owner light = new Owner("Light Yagami", false, null, null);
        Mono<Owner> ownerMono = shinigamiMono.flatMap(savedShinigami -> ownerService.save(light));

        // Inicializar una DeathNote
        Mono<DeathNote> deathNoteMono = ownerMono.flatMap(savedOwner ->
                deathNoteService.initializeDeathNote(ryuk.getId(), savedOwner.getId())
                        .flatMap(savedDeathNote -> {
                            // Actualizar el Shinigami y el Owner con el ID de la DeathNote
                            ryuk.setDeathNoteId(savedDeathNote.getId());
                            light.setDeathNoteId(savedDeathNote.getId());
                            return shinigamiService.save(ryuk)
                                    .then(ownerService.save(light))
                                    .thenReturn(savedDeathNote); // Devolver la DeathNote guardada
                        })
        );

        // Ejecutar la inicialización
        deathNoteMono.subscribe(
                result -> System.out.println("Datos iniciales creados: DeathNote ID = " + result.getId()),
                error -> System.err.println("Error al crear datos iniciales: " + error.getMessage())
        );
    }
}