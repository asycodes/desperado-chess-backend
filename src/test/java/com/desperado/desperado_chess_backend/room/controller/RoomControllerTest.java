package com.desperado.desperado_chess_backend.room.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


import com.desperado.desperado_chess_backend.room.DTO.*;
import com.desperado.desperado_chess_backend.room.controller.RoomController;
import com.desperado.desperado_chess_backend.room.service.RoomService;


@WebMvcTest 
public class RoomControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private RoomService roomService;

    //tests create room with a valid name and returns room code, header with the room code
    @Test 
    void createRoom_withValidName_returns201AndJson() throws Exception{
        when(roomService.createRoom(any())).thenReturn(new JoinRoomResponse("ABCD", 1L));
        mockMvc.perform(post("/api/rooms")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"nickname\":\"Jackson\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/rooms/ABCD"))
        .andExpect(jsonPath("$.code").value("ABCD"))
        .andExpect(jsonPath("$.playerId").value(1));
        verify(roomService).createRoom(new CreateRoomRequest("Jackson"));
    }

    //tests creating a room with a nickname longer than 16 length
    @Test 
    void createRoom_withInvalidName_returns400() throws Exception{
        mockMvc.perform(post("/api/rooms")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"nickname\":\"" + "aaaaabbbbbcccccddddd" + "\"}"))
            .andExpect(status().isBadRequest());

    verify(roomService, never()).createRoom(any());}
    
}
