package com.springboot.webflux.deathnote.model;

import org.springframework.data.annotation.Id;

public class Shinigami {

    @Id
    private String id;

    private String name;

    private String deathNoteId;

    public Shinigami() {}

    public Shinigami(String name, String deathNoteId) {
        this.name = name;
        this.deathNoteId = deathNoteId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDeathNoteId() {
        return deathNoteId;
    }

    public void setDeathNoteId(String deathNoteId) {
        this.deathNoteId = deathNoteId;
    }
}
