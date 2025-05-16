package com.springboot.webflux.deathnote.services;

import com.springboot.webflux.deathnote.model.Owner;
import com.springboot.webflux.deathnote.repository.OwnerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class OwnerServiceImpl implements OwnerService{

    @Autowired
    private OwnerRepository repository;

    @Override
    public Mono<Owner> find() {
        return repository.findFirstBy().switchIfEmpty(Mono.error(new IllegalStateException("No se encontró ningún propietario")));
    }

    @Override
    public Mono<Owner> save(Owner owner) {
        return repository.findFirstBy()
                .flatMap(existingOwner -> {
                    // Si existe, actualizar en lugar de crear uno nuevo
                    existingOwner.setName(owner.getName());
                    existingOwner.setHasShinigamiEyes(owner.isHasShinigamiEyes());
                    existingOwner.setShinigamiEyesDealDate(owner.getShinigamiEyesDealDate());
                    existingOwner.setDeathNoteId(owner.getDeathNoteId());
                    return repository.save(existingOwner);
                })
                .switchIfEmpty(repository.save(owner));
    }

    @Override
    public Mono<Void> delete(Owner owner) {
        return repository.delete(owner);
    }

    @Override
    public Mono<Owner> findByName(String name) {
        return repository.findByName(name);
    }
}
