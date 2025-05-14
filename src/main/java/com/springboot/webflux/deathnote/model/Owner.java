package com.springboot.webflux.deathnote.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Document(collection = "owner")
public class Owner {

    @Id
    private String id;

    private String name;

    private boolean hasShinigamiEyes;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date shinigamiEyesDealDate;

    private String deathNoteId;

    public Owner() {}

    public Owner(String name, boolean hasShinigamiEyes, Date shinigamiEyesDealDate, String deathNoteId) {
        this.name = name;
        this.hasShinigamiEyes = hasShinigamiEyes;
        this.shinigamiEyesDealDate = shinigamiEyesDealDate;
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

    public boolean isHasShinigamiEyes() {
        return hasShinigamiEyes;
    }

    public void setHasShinigamiEyes(boolean hasShinigamiEyes) {
        this.hasShinigamiEyes = hasShinigamiEyes;
    }

    public Date getShinigamiEyesDealDate() {
        return shinigamiEyesDealDate;
    }

    public void setShinigamiEyesDealDate(Date shinigamiEyesDealDate) {
        this.shinigamiEyesDealDate = shinigamiEyesDealDate;
    }

    public String getDeathNoteId() {
        return deathNoteId;
    }

    public void setDeathNoteId(String deathNoteId) {
        this.deathNoteId = deathNoteId;
    }
}
