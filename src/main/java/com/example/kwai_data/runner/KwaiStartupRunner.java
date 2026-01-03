package com.example.kwai_data.runner;


import com.example.kwai_data.facade.KwaiFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

//@Component
@RequiredArgsConstructor
public class KwaiStartupRunner implements CommandLineRunner {

    private final KwaiFacade kwaiFacade;

    @Override
    public void run(String... args) throws Exception {
        kwaiFacade.syncAll();
    }
}

