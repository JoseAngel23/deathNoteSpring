package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.DeathNote;
import com.springboot.webflux.deathnote.model.Owner;
import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.repository.DeathNoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    public Mono<DeathNote> writePersonInDeathNote(String deathNoteId, String personId, String deathDetails, Date deathDate, String photo) {
        return Mono.zip(deathNoteRepository.findById(deathNoteId), personService.findById(personId), ownerService.find())
                .flatMap(tuple -> {
                    DeathNote deathNote = tuple.getT1();
                    Person person = tuple.getT2();
                    Owner owner = tuple.getT3();

                    if (!person.getIsAlive()) {
                        return Mono.error(new IllegalStateException("La persona ya está muerta"));
                    }
                    if (deathNote.getOwnerId() != null && deathNote.getOwnerId().equals(personId)) {
                        return Mono.error(new IllegalStateException("El propietario no puede escribir su propio nombre"));
                    }
                    if (photo == null || photo.isEmpty()) {
                        return Mono.error(new IllegalStateException("Se requiere una foto del rostro"));
                    }

                    person.setDeathNoteId(deathNoteId);
                    person.setDeathDetails(deathDetails != null ? deathDetails : "Ataque al corazón");
                    person.setDeathDate(deathDate != null ? deathDate : new Date());
                    person.setIsAlive(false);
                    person.setFacePhoto(photo);

                    deathNote.addPersonId(personId);

                    return personService.save(person)
                            .then(deathNoteRepository.save(deathNote));
                });
    }

    @Override
    public Mono<DeathNote> rejectOwnership(String deathNoteId) {
        return Mono.zip(deathNoteRepository.findById(deathNoteId), ownerService.find())
                .flatMap(tuple -> {
                    DeathNote deathNote = tuple.getT1();
                    Owner owner = tuple.getT2();

                    deathNote.setOwnerId(null);
                    owner.setDeathNoteId(null);

                    return ownerService.save(owner)
                            .then(deathNoteRepository.save(deathNote));
                });
    }

    @Override
    public Mono<DeathNote> initializeDeathNote(String shinigamiId, String ownerId) {
        return deathNoteRepository.findAll().count()
                .flatMap(count -> {
                    if (count > 0) {
                        return Mono.error(new IllegalStateException("Ya existe una Death Note. No se pueden crear más."));
                    }
                    DeathNote deathNote = new DeathNote(shinigamiId, ownerId);
                    return deathNoteRepository.save(deathNote);
                });
    }

    @Override
    public Flux<DeathNote> findAll() {
        return deathNoteRepository.findAll();
    }
}