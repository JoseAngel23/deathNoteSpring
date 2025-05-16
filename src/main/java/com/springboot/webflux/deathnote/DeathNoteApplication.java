package com.springboot.webflux.deathnote;

import com.springboot.webflux.deathnote.model.DeathNote;
import com.springboot.webflux.deathnote.model.Owner;
import com.springboot.webflux.deathnote.model.Shinigami; // Asegúrate de tener este modelo
import com.springboot.webflux.deathnote.services.DeathNoteService;
import com.springboot.webflux.deathnote.services.OwnerService;
import com.springboot.webflux.deathnote.services.ShinigamiService; // Y este servicio
// Quita PersonService si no se usa directamente en el CommandLineRunner
// import com.springboot.webflux.deathnote.services.PersonService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import reactor.core.publisher.Mono;

@SpringBootApplication
@EnableScheduling
public class DeathNoteApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DeathNoteApplication.class);

    @Autowired
    private ShinigamiService shinigamiService;

    @Autowired
    private OwnerService ownerService;

    @Autowired
    private DeathNoteService deathNoteService;

    // @Autowired
    // private PersonService personService;

    public static void main(String[] args) {
        SpringApplication.run(DeathNoteApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
    }
}