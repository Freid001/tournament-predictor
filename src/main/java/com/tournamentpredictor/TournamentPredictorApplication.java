package com.tournamentpredictor;

import org.fusesource.jansi.AnsiConsole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

import java.util.Arrays;

@SpringBootApplication
public class TournamentPredictorApplication implements CommandLineRunner, ExitCodeGenerator {

    @Autowired
    private TournamentPredictorCommand command;

    @Autowired
    private IFactory factory;

    private int exitCode;

    public static void main(String[] args) {
        AnsiConsole.systemInstall();
        try {
            SpringApplication app = new SpringApplication(TournamentPredictorApplication.class);
            boolean browserMode = Arrays.stream(args)
                    .anyMatch(arg -> "--browser".equals(arg) || "--browser=true".equalsIgnoreCase(arg));
            app.setWebApplicationType(browserMode ? WebApplicationType.SERVLET : WebApplicationType.NONE);
            if (!browserMode) {
                System.exit(SpringApplication.exit(app.run(args)));
            } else {
                app.run(args);
            }
        } finally {
            AnsiConsole.systemUninstall();
        }
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(command, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
