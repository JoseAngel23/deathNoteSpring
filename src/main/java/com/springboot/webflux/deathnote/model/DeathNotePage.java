package com.springboot.webflux.deathnote.model;

import org.springframework.data.annotation.Id;

import java.sql.Timestamp;

public class DeathNotePage {

    @Id
    private String id;

    private DeathNote deathNote;

    private String personId;

    public DeathNotePage() {}

    public DeathNotePage(DeathNote deathNote, String personId) {
        this.deathNote = deathNote;
        this.personId = personId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DeathNote getDeathNote() {
        return deathNote;
    }

    public void setDeathNote(DeathNote deathNote) {
        this.deathNote = deathNote;
    }

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(String personId) {
        this.personId = personId;
    }
}
