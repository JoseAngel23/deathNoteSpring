package com.springboot.webflux.deathnote.model;

import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "death_note")
public class DeathNote {
    @Id
    private String id;

    @NotBlank
    private String shinigamiId;

    private String ownerId;

    private List<String> personIds = new ArrayList<>();

    public DeathNote() {}

    public DeathNote(String shinigamiId, String ownerId) {
        this.shinigamiId = shinigamiId;
        this.ownerId = ownerId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getShinigamiId() {
        return shinigamiId;
    }

    public void setShinigamiId(String shinigamiId) {
        this.shinigamiId = shinigamiId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public List<String> getPersonIds() {
        return personIds;
    }

    public void setPersonIds(List<String> personIds) {
        this.personIds = personIds;
    }

    public void addPersonId(String personId) {
        this.personIds.add(personId);
    }
}
