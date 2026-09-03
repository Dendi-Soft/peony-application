package com.dendi.peony;

import org.springframework.boot.SpringApplication;

public class TestPeonyApplication {

    public static void main(String[] args) {
        SpringApplication.from(PeonyApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
