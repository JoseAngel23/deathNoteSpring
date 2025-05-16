package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.DeathNote;
import com.springboot.webflux.deathnote.model.Owner;
import com.springboot.webflux.deathnote.model.Person;
import com.springboot.webflux.deathnote.repository.DeathNoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(PersonServiceImpl.class);

    @Override
    public Mono<Void> delete(DeathNote deathNote) {
        return deathNoteRepository.delete(deathNote);
    }

    @Override
    public Mono<DeathNote> writePersonInDeathNote(String deathNoteId, String personId, String deathDetailsFromPerson,
                                                  LocalDateTime scheduledDeathTimeFromPerson, String photoNameFromPerson) {
        return Mono.zip(
                        deathNoteRepository.findById(deathNoteId),
                        personService.findById(personId) // Este 'person' ya fue procesado por saveInitialEntry
                )
                .flatMap(tuple -> {
                    DeathNote deathNote = tuple.getT1();
                    Person person = tuple.getT2(); // person ya tiene su estado y foto (si la hubo)

                    // Regla: Propietario no puede escribir su propio nombre
                    if (deathNote.getOwnerId() != null && deathNote.getOwnerId().equals(personId)) {
                        return Mono.error(new IllegalStateException("El propietario no puede escribir su propio nombre"));
                    }

                    // La validación estricta de la foto se elimina de aquí.
                    // La lógica de si la persona muere o no ya fue manejada en PersonServiceImpl.saveInitialEntry.
                    // Este método ahora se enfoca en actualizar la DeathNote y, si acaso,
                    // detalles de muerte explícitos si vinieran de un formulario más avanzado.

                    log.info("Escribiendo a {} (ID: {}) en DeathNote {} (ID: {}). Detalles provistos: '{}', Muerte programada: '{}', Foto: '{}'",
                            person.getName(), person.getId(), deathNote.getId(), deathNote.getId(), deathDetailsFromPerson, scheduledDeathTimeFromPerson, photoNameFromPerson);


                    // Aquí podrías manejar la actualización de 'person.setDeathDetails' o 'person.setDeathDate'
                    // si el formulario de anotación permitiera especificar causa y momento exacto de la muerte,
                    // lo cual sobrescribiría la muerte por defecto de 40s.
                    // Por ahora, asumimos que 'deathDetailsFromPerson' y 'scheduledDeathTimeFromPerson'
                    // son los que 'saveInitialEntry' estableció.

                    // Si se proporcionaron detalles explícitos de muerte que deben aplicarse inmediatamente:
                    boolean personModifiedInWrite = false;
                    if (deathDetailsFromPerson != null && !deathDetailsFromPerson.equals(person.getDeathDetails())) {
                        // Podrías querer actualizar person.setDeathDetails si el formulario permite especificarlo
                        // y es diferente de lo que saveInitialEntry pudo haber puesto.
                        // person.setDeathDetails(deathDetailsFromPerson);
                        // personModifiedInWrite = true;
                    }
                    // Similar para una deathDate explícita que no sea la programada.


                    // Añadir el ID de la persona a la lista de la Death Note
                    deathNote.addPersonId(personId);

                    // Si 'person' se modificó en este método (ej. con detalles explícitos de muerte), guardarlo.
                    // De lo contrario, 'person' ya fue guardado por saveInitialEntry.
                    Mono<Person> savePersonIfNeededMono = Mono.just(person);
                    if(personModifiedInWrite) {
                        // savePersonIfNeededMono = personService.save(person);
                    }

                    // Solo se guarda la DeathNote ya que la persona ya se guardó en saveInitialEntry
                    // a menos que se modifique explícitamente aquí.
                    return deathNoteRepository.save(deathNote).map(dn -> person); // Devolvemos la persona para mantener el flujo del controlador
                })
                .switchIfEmpty(Mono.error(new IllegalStateException("No se encontró DeathNote o Persona para escribir.")))
                .flatMap(savedPerson -> deathNoteRepository.findById(deathNoteId)); // Aseguramos devolver la DeathNote actualizada
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