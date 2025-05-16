// En el archivo: com/springboot/webflux/deathnote/services/PersonServiceImpl.java

package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class PersonServiceImpl implements PersonService {

    private static final Logger log = LoggerFactory.getLogger(PersonServiceImpl.class);

    @Autowired
    private PersonRepository personRepository;

    @Override
    public Flux<Person> findAll() {
        return personRepository.findAll();
    }

    @Override
    public Mono<Person> findById(String id) {
        return personRepository.findById(id);
    }

    @Override
    public Mono<Person> save(Person person) {
        log.debug("Guardando persona: {} con ID: {}, Foto: {}, Estado: {}, Viva: {}",
                person.getName(), person.getId(), person.getFacePhoto(), person.getStatus(), person.isAlive());
        return personRepository.save(person);
    }

    @Override
    public Mono<Void> delete(Person person) {
        log.debug("Eliminando persona: {}", person.getName());
        return personRepository.delete(person);
    }

    @Override
    public Mono<Person> saveInitialEntry(Person person) {
        LocalDateTime now = LocalDateTime.now();
        person.setEntryTime(now);
        person.setIsAlive(true); // La persona está viva al ser anotada inicialmente.

        // Verificar si se ha asignado una foto (asumimos que se hizo en el controlador)
        if (person.getFacePhoto() != null && !person.getFacePhoto().isEmpty()) {
            // --- CASO: Nombre y Foto ---
            // Se programa la muerte por ataque al corazón en 40 segundos.
            person.setScheduledDeathTime(now.plusSeconds(40));
            person.setStatus("PENDING_HEART_ATTACK"); // El scheduler buscará este estado.
            person.setCauseOfDeath(null); // Se definirá por el scheduler como "Ataque al Corazón".
            person.setDeathDetails("Muerte programada por efecto de Death Note (con foto)."); // Detalle inicial
            log.info("ANOTADA (CON FOTO): {} (ID: {}). Muerte programada por ataque al corazón para las {}. Foto: {}",
                    person.getName(), person.getId(), person.getScheduledDeathTime(), person.getFacePhoto());
        } else {
            // --- CASO: Solo Nombre (Sin Foto) ---
            // La persona es anotada pero no muere automáticamente por este efecto.
            person.setScheduledDeathTime(null); // No hay muerte programada.
            person.setStatus("ALIVE_NO_PHOTO_EFFECT"); // Un estado para indicar que está anotada pero el efecto de muerte no aplica.
            person.setCauseOfDeath(null);
            person.setDeathDetails("Anotada sin foto, efecto de muerte automática no aplicado.");
            log.info("ANOTADA (SIN FOTO): {}. La persona permanece viva, sin muerte programada. Foto: {}",
                    person.getName(), (person.getFacePhoto() == null || person.getFacePhoto().isEmpty() ? "Ninguna" : person.getFacePhoto()));
        }
        return this.save(person); // Guardar la persona con su estado inicial.
    }
}