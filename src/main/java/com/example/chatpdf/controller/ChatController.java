package com.example.chatpdf.controller;

import com.example.chatpdf.dto.AskRequest;
import com.example.chatpdf.dto.AskResponse;
import com.example.chatpdf.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return chatService.ask(request);
    }
}