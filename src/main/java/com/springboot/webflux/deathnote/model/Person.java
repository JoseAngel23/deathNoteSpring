package com.springboot.webflux.deathnote.model;

import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Document(collection="people")
public class Person {

    @Id
    private String id;

    private String name;

    @NotBlank
    private boolean isAlive;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date deathDate;

    private String deathDetails;

    // photo

    public Person() {
    }

    public Person(String name, Boolean isAlive, Date deathDate, String deathDetails) {
        this.name = name;
        this.isAlive = isAlive;
        this.deathDate = deathDate;
        this.deathDetails = deathDetails;
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

    public boolean getIsAlive() {
        return isAlive;
    }

    public void setIsAlive(boolean alive) {
        isAlive = alive;
    }

    public Date getDeathDate() {
        return deathDate;
    }

    public void setDeathDate(Date deathDate) {
        this.deathDate = deathDate;
    }

    public String getDeathDetails() {
        return deathDetails;
    }

    public void setDeathDetails(String deathDetails) {
        this.deathDetails = deathDetails;
    }
}
