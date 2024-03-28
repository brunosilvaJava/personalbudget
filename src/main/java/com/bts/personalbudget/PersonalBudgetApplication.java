package com.bts.personalbudget;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class PersonalBudgetApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonalBudgetApplication.class, args);
    }

}
