package com.springboot.webflux.deathnote.model;

import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "death_note")
public class DeathNote {

    @Id
    private String id;

    @NotBlank
    private String originalShinigamiOwner;

    private String owner;

    public DeathNote() {}

    public DeathNote(String originalShinigamiOwner, String owner) {
        this.originalShinigamiOwner = originalShinigamiOwner;
        this.owner = owner;
    }

    public String getOriginalShinigamiOwner() {
        return originalShinigamiOwner;
    }

    public void setOriginalShinigamiOwner(String originalShinigamiOwner) {
        this.originalShinigamiOwner = originalShinigamiOwner;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}
