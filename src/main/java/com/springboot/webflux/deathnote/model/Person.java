package com.springboot.webflux.deathnote.model;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime; // ÚNICA importación de fecha/hora necesaria aquí

@Document(collection="people")
public class Person {

    @Id
    private String id;

    @NotEmpty
    private String name;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) // Para binding desde String a LocalDateTime
    private LocalDateTime deathDate;

    private String deathDetails;
    private String facePhoto;
    private String deathNoteId;

    // --- CAMPOS ACTUALIZADOS A LocalDateTime ---
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime entryTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime scheduledDeathTime;
    // --- FIN DE ACTUALIZACIÓN ---

    private String status;
    private String causeOfDeath;
    private boolean isAlive = true;

    public Person() {
        this.isAlive = true; // Por defecto viva
    }

    // Constructor actualizado para usar LocalDateTime en todos los campos de fecha
    public Person(String name, LocalDateTime deathDate, String deathDetails, String facePhoto, String deathNoteId) {
        this.name = name;
        this.deathDate = deathDate;
        this.deathDetails = deathDetails;
        this.facePhoto = facePhoto;
        this.deathNoteId = deathNoteId;
        this.isAlive = true; // Al crear, está viva
        this.entryTime = LocalDateTime.now(); // Podrías inicializarlo aquí o en el servicio
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    // Convención estándar para booleanos
    public boolean isAlive() { return isAlive; }
    public void setIsAlive(boolean alive) { isAlive = alive; }

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

    // El método `setAlive` es redundante si tienes `setIsAlive`. Elimina uno.
    // public void setAlive(boolean alive) { isAlive = alive; }
    // El método `IsAlive()` con 'I' mayúscula no sigue la convención. Elimínalo.
    // public boolean IsAlive() { return isAlive; }
}