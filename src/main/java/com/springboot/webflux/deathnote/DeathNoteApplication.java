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
        log.info("Ejecutando CommandLineRunner para configurar dueño de Death Note...");

        // --- 1. Asegurar que el Shinigami exista (ej. Ryuk) ---
        String shinigamiName = "Ryuk";
        Mono<Shinigami> shinigamiMono = shinigamiService.findByName(shinigamiName) // Necesitarás un método así en tu ShinigamiService
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Shinigami {} no encontrado, creando uno nuevo.", shinigamiName);
                    Shinigami newShinigami = new Shinigami(); // Asume constructor por defecto y setters
                    newShinigami.setName(shinigamiName);
                    // Establece otros campos necesarios para Shinigami si los hay
                    return shinigamiService.save(newShinigami);
                }));

        // --- 2. Asegurar que el Dueño exista (ej. Light Yagami) ---
        String ownerName = "Light Yagami";
        Mono<Owner> ownerMono = ownerService.findByName(ownerName) // Necesitarás un método así en tu OwnerService
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Dueño {} no encontrado, creando uno nuevo.", ownerName);
                    Owner newOwner = new Owner(); // Asume constructor por defecto y setters
                    newOwner.setName(ownerName);
                    newOwner.setHasShinigamiEyes(false); // Como en tu imagen
                    // newOwner.setDeathNoteId(null); // Inicialmente no tiene Death Note
                    return ownerService.save(newOwner);
                }));

        // --- 3. Combinar y procesar ---
        Mono.zip(shinigamiMono, ownerMono)
                .flatMap(tuple -> {
                    Shinigami ryuk = tuple.getT1();
                    Owner light = tuple.getT2();

                    log.info("Shinigami ID: {}, Dueño ID: {}", ryuk.getId(), light.getId());

                    // Intentar encontrar una Death Note existente (quizás la primera o una específica)
                    // O una que pertenezca a Ryuk y no tenga dueño
                    return deathNoteService.findAll() // O un método más específico si lo tienes
                            .filter(dn -> dn.getShinigamiId() != null && dn.getShinigamiId().equals(ryuk.getId()) && dn.getOwnerId() == null)
                            .next() // Tomar la primera que cumpla (sin dueño y de Ryuk)
                            .switchIfEmpty(Mono.defer(() -> { // Si no hay ninguna DN de Ryuk sin dueño, o ninguna DN en absoluto
                                log.info("No se encontró Death Note adecuada para Ryuk sin dueño. Intentando crear una nueva.");
                                DeathNote newDeathNote = new DeathNote(ryuk.getId(), null); // Shinigami asignado, sin dueño humano aún
                                return deathNoteService.save(newDeathNote) // Asume que tienes un save genérico en DeathNoteService
                                        .doOnSuccess(dn -> log.info("Nueva Death Note creada con ID: {} para Shinigami ID: {}", dn.getId(), ryuk.getId()));
                            }))
                            .flatMap(deathNoteToAssign -> {
                                if (deathNoteToAssign.getOwnerId() == null) {
                                    log.info("Asignando Death Note ID: {} al Dueño: {}", deathNoteToAssign.getId(), light.getName());
                                    deathNoteToAssign.setOwnerId(light.getId());

                                    // Si el modelo Owner también guarda el ID de la Death Note
                                    if (light.getDeathNoteId() == null || !light.getDeathNoteId().equals(deathNoteToAssign.getId())) {
                                        light.setDeathNoteId(deathNoteToAssign.getId());
                                        return deathNoteService.save(deathNoteToAssign)
                                                .then(ownerService.save(light)) // Guarda el Owner actualizado
                                                .thenReturn(deathNoteToAssign); // Devuelve la DeathNote
                                    }
                                    return deathNoteService.save(deathNoteToAssign); // Solo guardar DeathNote
                                } else if (deathNoteToAssign.getOwnerId().equals(light.getId())) {
                                    log.info("Death Note ID: {} ya pertenece a {}.", deathNoteToAssign.getId(), light.getName());
                                    // Asegurar que el owner también tiene la referencia correcta (opcional, si se desincronizó)
                                    if (light.getDeathNoteId() == null || !light.getDeathNoteId().equals(deathNoteToAssign.getId())) {
                                        light.setDeathNoteId(deathNoteToAssign.getId());
                                        return ownerService.save(light).thenReturn(deathNoteToAssign);
                                    }
                                    return Mono.just(deathNoteToAssign); // Ya está asignada correctamente
                                } else {
                                    log.warn("Death Note ID: {} ya tiene un dueño diferente (ID: {}). No se asignará a {}.",
                                            deathNoteToAssign.getId(), deathNoteToAssign.getOwnerId(), light.getName());
                                    return Mono.just(deathNoteToAssign); // No hacer nada si ya tiene otro dueño
                                }
                            });
                })
                .subscribe(
                        finalDeathNote -> log.info("Proceso de asignación de Death Note completado. DN ID: {}, Dueño ID: {}, Shinigami ID: {}",
                                finalDeathNote.getId(), finalDeathNote.getOwnerId(), finalDeathNote.getShinigamiId()),
                        error -> log.error("Error en el CommandLineRunner al configurar Death Note: {}", error.getMessage()),
                        () -> log.info("CommandLineRunner finalizado.")
                );
    }
}