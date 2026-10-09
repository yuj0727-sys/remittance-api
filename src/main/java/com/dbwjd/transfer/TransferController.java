package com.dbwjd.transfer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateTransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.create(idempotencyKey, request));
    }
}