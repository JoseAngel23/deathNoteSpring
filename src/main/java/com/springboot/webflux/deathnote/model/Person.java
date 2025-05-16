package com.springboot.webflux.deathnote.model;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Document(collection="people")
public class Person {

    @Id
    private String id;

    @NotEmpty
    private String name;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime deathDate;

    private String deathDetails;
    private String facePhoto;
    private String deathNoteId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime entryTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime scheduledDeathTime;

    private String status;
    private String causeOfDeath;
    private boolean alive = true; // <--- CAMPO RENOMBRADO

    public Person() {
        this.alive = true; // Ajustado al nuevo nombre de campo
    }

    public Person(String name, LocalDateTime deathDate, String deathDetails, String facePhoto, String deathNoteId) {
        this.name = name;
        this.deathDate = deathDate;
        this.deathDetails = deathDetails;
        this.facePhoto = facePhoto;
        this.deathNoteId = deathNoteId;
        this.alive = true; // Ajustado al nuevo nombre de campo
        this.entryTime = LocalDateTime.now();
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    // Getter y Setter para la propiedad 'alive'
    public boolean isAlive() { // Este es el getter para la propiedad 'alive'
        return alive;
    }
    public void setAlive(boolean alive) { // Este es el setter para la propiedad 'alive'
        this.alive = alive;
    }

    public LocalDateTime getDeathDate() { return deathDate; }
    public void setDeathDate(LocalDateTime deathDate) { this.deathDate = deathDate; }

    public String getDeathDetails() { return deathDetails; }
    public void setDeathDetails(String deathDetails) { this.deathDetails = deathDetails; }

    public String getFacePhoto() { return facePhoto; }
    public void setFacePhoto(String facePhoto) { this.facePhoto = facePhoto; }

    public String getDeathNoteId() { return deathNoteId; }
    public void setDeathNoteId(String deathNoteId) { this.deathNoteId = deathNoteId; }

    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }

    public LocalDateTime getScheduledDeathTime() { return scheduledDeathTime; }
    public void setScheduledDeathTime(LocalDateTime scheduledDeathTime) { this.scheduledDeathTime = scheduledDeathTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCauseOfDeath() { return causeOfDeath; }
    public void setCauseOfDeath(String causeOfDeath) { this.causeOfDeath = causeOfDeath; }
}