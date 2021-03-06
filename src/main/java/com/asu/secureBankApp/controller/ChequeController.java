package com.asu.secureBankApp.controller;

import com.asu.secureBankApp.Request.ChequeRequest;
import com.asu.secureBankApp.Request.UserRequest;
import com.asu.secureBankApp.Response.StatusResponse;
import com.asu.secureBankApp.dao.ChequeDAO;
import com.asu.secureBankApp.service.ChequeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.ws.rs.Path;
import java.util.List;

@RestController
@RequestMapping("/api/cheque")
public class ChequeController {

    @Autowired
    ChequeService chequeService;

    @PostMapping(value = "/issue")
    public StatusResponse issueCheque(@RequestBody ChequeRequest cheque) {
    	System.out.print(cheque.getFromAccNo()+"  "+ cheque.getToAccNo()+" "+cheque.getTransferAmount());
        return chequeService.issueCheque(cheque);
    }

    @GetMapping(value = "/listIssueApprovals")
    public @ResponseBody List<ChequeDAO> listChequesForApprovals() {
        return chequeService.listCheques();
    }

    @PostMapping(value = "/approve/{chequeId}")
    public StatusResponse approveChequeIssue(@PathVariable Long chequeId, Authentication authentication) {
        return chequeService.approveChequeIssue(chequeId, authentication);
    }

    @PostMapping(value = "/reject/{chequeId}")
    public StatusResponse rejectChequeIssue(@PathVariable Long chequeId) {
        return chequeService.rejectChequeIssue(chequeId);
    }

    @GetMapping(value = "/listAvailableCheques/{accountNo}")
    public List<ChequeDAO> listChequesForUser(@PathVariable Integer accountNo) {
        return chequeService.listChequesForUser(accountNo);
    }

    @PostMapping(value = "/depositCheque/{chequeId}")
    public StatusResponse depositCheque(@PathVariable Long chequeId, Authentication authentication) {
        return chequeService.depositCheque(chequeId, authentication);
    }

}
