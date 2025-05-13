package com.springboot.webflux.deathnote.model;

import org.springframework.data.annotation.Id;

import java.sql.Timestamp;

public class DeathNotePage {

    @Id
    private String id;

    private String DeathNoteId;

    private String personId;

    public DeathNotePage() {}

    public DeathNotePage(String deathNoteId, String personId) {
        DeathNoteId = deathNoteId;
        this.personId = personId;
    }

    public String getDeathNoteId() {
        return DeathNoteId;
    }

    public void setDeathNoteId(String deathNoteId) {
        DeathNoteId = deathNoteId;
    }

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(String personId) {
        this.personId = personId;
    }
}
