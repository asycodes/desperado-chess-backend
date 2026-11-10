package com.desperado.desperado_chess_backend.config;

import com.desperado.desperado_chess_backend.match.engine.bot.BotStrategy;
import com.desperado.desperado_chess_backend.match.engine.bot.GreedyBot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;

@Configuration
public class BotConfig {

    @Bean
    public BotStrategy botStrategy() {
        return new GreedyBot(new Random());
    }
}