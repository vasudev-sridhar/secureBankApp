package com.asu.secureBankApp.service;

import com.asu.secureBankApp.Repository.AccountRepository;
import com.asu.secureBankApp.Repository.ChequeRepository;
import com.asu.secureBankApp.Request.ChequeRequest;
import com.asu.secureBankApp.Response.StatusResponse;
import com.asu.secureBankApp.dao.AccountDAO;
import com.asu.secureBankApp.dao.ChequeDAO;
import com.asu.secureBankApp.dao.UserDAO;
import constants.Status;
import com.asu.secureBankApp.Config.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ChequeServiceImpl implements ChequeService {

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    ChequeRepository chequeRepository;

    @Override
    public StatusResponse issueCheque(ChequeRequest chequeRequest) {
        ChequeDAO chequeToIssue = new ChequeDAO();
        StatusResponse response = new StatusResponse();
        AccountDAO fromAccount = accountRepository.findById(chequeRequest.getFromAccNo()).orElse(null);
        AccountDAO toAccount = accountRepository.findById(chequeRequest.getToAccNo()).orElse(null);
        Float amount = chequeRequest.getTransferAmount();
        chequeToIssue.setFromAccount(fromAccount);
        chequeToIssue.setToAccount(toAccount);
        chequeToIssue.setAmount(amount);
        if(null==toAccount){
            response.setIsSuccess(false);
            response.setMsg("ToAccount does not exist");
            return response;
        }
        if(fromAccount.getBalance() < amount){
            response.setIsSuccess(false);
            response.setMsg("Insufficient funds");
            return response;
        }
        chequeToIssue.setStatus(Constants.CHEQUE_ISSUE_PENDING);
        chequeRepository.save(chequeToIssue);
        response.setIsSuccess(true);
        response.setMsg("Cheque sent for issue approval");
        return response;
    }

    @Override
    public List<ChequeDAO> listCheques(){
        List<ChequeDAO> chequesForApproval = chequeRepository.findByStatus(Constants.CHEQUE_ISSUE_PENDING);
//        for(ChequeDAO cheque: chequesForApproval){
//            AccountDAO fromAcc = cheque.getFromAccount();
//            AccountDAO toAcc = cheque.getToAccount();
//            fromAcc.getUser().setAuthRole(null);
//            toAcc.getUser().setAuthRole(null);
//        }
        return chequesForApproval;
        }
}
