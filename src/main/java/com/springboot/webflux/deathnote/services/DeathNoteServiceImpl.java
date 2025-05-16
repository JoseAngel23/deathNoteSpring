package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.DeathNote;
import com.springboot.webflux.deathnote.model.Owner;
import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.repository.DeathNoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Date;

@Service
public class DeathNoteServiceImpl implements DeathNoteService {

    @Autowired
    private DeathNoteRepository deathNoteRepository;

    @Autowired
    private PersonService personService;

    @Autowired
    private OwnerService ownerService;

    @Override
    public Mono<Void> delete(DeathNote deathNote) {
        return deathNoteRepository.delete(deathNote);
    }

    @Override
    public Mono<DeathNote> writePersonInDeathNote(String deathNoteId, String personId, String deathDetails,
                                                  LocalDateTime deathDate, String photo) {
        return Mono.zip(
                        deathNoteRepository.findById(deathNoteId),
                        personService.findById(personId)
                )
                .flatMap(tuple -> {
                    DeathNote deathNote = tuple.getT1();
                    Person person = tuple.getT2();
                    // Owner owner = tuple.getT3(); // Si lo necesitas, descomenta

                    if (!person.isAlive()) { // Usar isAlive()
                        return Mono.error(new IllegalStateException("La persona ya está muerta"));
                    }
                    // Asumo que getOwnerId() en DeathNote es el ID del dueño actual de la DeathNote
                    if (deathNote.getOwnerId() != null && deathNote.getOwnerId().equals(personId)) {
                        return Mono.error(new IllegalStateException("El propietario no puede escribir su propio nombre"));
                    }
                    if (photo == null || photo.isEmpty()) {
                        // Considera si la foto es realmente obligatoria para la lógica de muerte
                        // o solo para la visualización. La regla 2 dice "suba una foto".
                        return Mono.error(new IllegalStateException("Se requiere una foto del rostro para que la Death Note tenga efecto"));
                    }

                    person.setDeathNoteId(deathNoteId);
                    person.setDeathDetails(deathDetails != null ? deathDetails : "Ataque al Corazón (por defecto si no hay detalles y pasa el tiempo)");

                    person.setDeathDate(deathDate != null ? deathDate : LocalDateTime.now().plusSeconds(40)); // O la lógica que determine el saveInitialEntry

                    person.setFacePhoto(photo);
                    deathNote.addPersonId(personId);

                    return personService.save(person)
                            .then(deathNoteRepository.save(deathNote));
                });
    }

    @Override
    public Mono<DeathNote> rejectOwnership(String deathNoteId) {
        return deathNoteRepository.findById(deathNoteId)
                .zipWith(ownerService.find())
                .flatMap(tuple -> {
                    DeathNote deathNote = tuple.getT1();
                    Owner owner = tuple.getT2();

                    if (deathNote.getOwnerId() == null || !deathNote.getOwnerId().equals(owner.getId())) {
                        return Mono.error(new IllegalStateException("El owner recuperado no coincide con el de la Death Note o la DN no tiene owner."));
                    }

                    deathNote.setOwnerId(null);
                    owner.setDeathNoteId(null);

                    return ownerService.save(owner)
                            .then(deathNoteRepository.save(deathNote));
                });
    }

    @Override
    public Mono<DeathNote> initializeDeathNote(String shinigamiId, String ownerId) {
        // ... (tu lógica)
        return deathNoteRepository.findAll().count()
                .flatMap(count -> {
                    if (count > 0 && ownerId != null) {
                        return Mono.error(new IllegalStateException("Ya existe una Death Note con propietario. No se pueden crear más con propietario inicial."));
                    }
                    DeathNote deathNote = new DeathNote(shinigamiId, ownerId); // Usa tu constructor
                    return deathNoteRepository.save(deathNote);
                });
    }

    @Override
    public Flux<DeathNote> findAll() {
        return deathNoteRepository.findAll();
    }

    @Override
    public Mono<DeathNote> save(DeathNote deathNote) {
        return deathNoteRepository.save(deathNote);
    }
}