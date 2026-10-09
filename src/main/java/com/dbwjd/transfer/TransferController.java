package com.dbwjd.transfer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;
    private final TransferSendService transferSendService;

    public TransferController(TransferService transferService, TransferSendService transferSendService) {
        this.transferService = transferService;
        this.transferSendService = transferSendService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateTransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.create(idempotencyKey, request));
    }

    @GetMapping("/{transferId}")
    public TransferResponse get(@PathVariable String transferId) {
        return transferService.get(transferId);
    }

    @PostMapping("/{transferId}/cancel")
    public TransferResponse cancel(@PathVariable String transferId) {
        return transferService.cancel(transferId);
    }

    @PostMapping("/{transferId}/send")
    public TransferResponse send(@PathVariable String transferId) {
        return transferSendService.send(transferId);
    }
}