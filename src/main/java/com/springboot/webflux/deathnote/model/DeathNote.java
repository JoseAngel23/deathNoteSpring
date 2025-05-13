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
}
