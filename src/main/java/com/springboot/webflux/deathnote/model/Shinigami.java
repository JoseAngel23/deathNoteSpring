package com.springboot.webflux.deathnote.model;

import org.springframework.data.annotation.Id;

public class Shinigami {

    @Id
    private String id;

    private String name;
}
