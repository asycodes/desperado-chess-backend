package com.desperado.desperado_chess_backend;

import org.springframework.boot.SpringApplication;

public class TestDesperadoChessBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(DesperadoChessBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
