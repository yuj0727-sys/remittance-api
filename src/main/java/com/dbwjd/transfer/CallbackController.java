package com.dbwjd.transfer;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/callbacks")
public class CallbackController {

    private final PartnerCallbackService callbackService;

    public CallbackController(PartnerCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @PostMapping("/partner")
    public CallbackResponse receive(@RequestBody PartnerCallbackRequest request) {
        return callbackService.receive(request);
    }
}
