package com.asu.secureBankApp.service;

import com.asu.secureBankApp.Config.Constants;
import com.asu.secureBankApp.Repository.AccountRepository;
import com.asu.secureBankApp.Repository.TransactionRepository;
import com.asu.secureBankApp.Repository.UserRepository;
import com.asu.secureBankApp.Request.TransferRequest;
import com.asu.secureBankApp.Request.UpdateBalanceRequest;
import com.asu.secureBankApp.Response.StatusResponse;
import com.asu.secureBankApp.dao.AccountDAO;
import com.asu.secureBankApp.dao.TransactionDAO;
import com.asu.secureBankApp.dao.UserDAO;
import com.asu.secureBankApp.util.Util;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import constants.ErrorCodes;
import constants.RoleType;
import constants.TransactionStatus;
import constants.TransactionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import javax.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.*;

@Service
public class TransactionServiceImpl implements TransactionService {

	@Autowired
	AccountRepository accountRepository;

	@Autowired
	UserRepository userRepository;
	
	@Autowired
	TransactionRepository transactionRepository;

	@Autowired
	SystemLoggerService logger;



	@Transactional
	public StatusResponse transfer(@Valid TransferRequest transferReq, Authentication auth, boolean isApproved) {
		StatusResponse response = new StatusResponse();
		response.setIsSuccess(false);

		AccountDAO fromAccount = accountRepository.findById(transferReq.getFromAccNo()).orElse(null);
		AccountDAO toAccount = accountRepository.findById(transferReq.getToAccNo()).orElse(null);

		logger.log(fromAccount.getUser().getId(), "Transfer initiated to "+toAccount.getId(), "TRANSFER_INITIATED");

		if (fromAccount == null || toAccount == null) {
			response.setMsg(ErrorCodes.ID_NOT_FOUND);
			return response;
		}
		if(fromAccount.getBalance() - transferReq.getTransferAmount() < 0) {
			response.setMsg(ErrorCodes.INSUFFICIENT_FUNDS);
			return response;
		}
		if(fromAccount == toAccount) {
			response.setMsg(ErrorCodes.TO_ACCOUNT_SAME);
			return response;
		}
		RoleType accRoleType = fromAccount.getUser().getAuthRole().getRoleType();
//		System.out.println("auth.getPrincipal()" + auth.getName());
		UserDAO authUser = userRepository.findByUsername(auth.getPrincipal().toString());
		RoleType authRoleType = authUser.getAuthRole()
				.getRoleType();
		
		// Can't update someone else's account if authUser is not employee
		if (authRoleType != accRoleType && !Util.isEmployee(authRoleType)) {
			response.setMsg(ErrorCodes.INVALID_ACCESS);
			return response;
		}
		UpdateBalanceRequest fromUpdateBalanceRequest = new UpdateBalanceRequest();
		UpdateBalanceRequest toUpdateBalanceRequest = new UpdateBalanceRequest();
		fromUpdateBalanceRequest.setAccountNo(fromAccount.getId());
		fromUpdateBalanceRequest.setAmount(-transferReq.getTransferAmount());
		
		toUpdateBalanceRequest.setAccountNo(toAccount.getId());
		toUpdateBalanceRequest.setAmount(transferReq.getTransferAmount());
		
		TransactionDAO transactionDAO = new TransactionDAO();
		
		UserDAO workingUser = fromAccount.getUser();
		Float transactionSum = transactionRepository.dailyTransactionSum(workingUser);
		if(transactionSum == null)
			transactionSum = 0f;
		
//		System.out.println("transactionSum = " + transactionSum + " for working user:" + workingUser.getUsername() + " authUser: " + authUser);
		boolean isCritical = (transactionSum + transferReq.getTransferAmount()) > Constants.TRANSFER_CRITICAL_LIMIT;
				
		if (!isApproved) {
			transactionDAO.setCreatedBy(workingUser);
			transactionDAO.setFromAccount(fromAccount);
			transactionDAO.setToAccount(toAccount);
			transactionDAO.setTransactionAmount(Math.abs(transferReq.getTransferAmount()));
			transactionDAO.setType(TransactionType.TRANSFER);
			transactionDAO.setIsCritical(isCritical);
			transactionDAO.setStatus(TransactionStatus.PENDING);
			transactionDAO.setTransactionTimestamp(Calendar.getInstance().getTime());
			transactionRepository.save(transactionDAO);
			response.setIsSuccess(true);
			response.setMsg(ErrorCodes.SUBMIT_APPROVAL);
		} else {
			if(doUpdateBalance(fromUpdateBalanceRequest) && doUpdateBalance(toUpdateBalanceRequest)) {
				// Don't create new Transaction record if already approved. Update existing one
				response.setIsSuccess(true);
				return response;			
			} else {
				throw new RuntimeException();
			}
		}
		return response;
	}

	@Override
	@Transactional
	public StatusResponse updateBalance(@Valid UpdateBalanceRequest updateBalanceRequest, Authentication auth, boolean isApproved) throws Exception {
		StatusResponse response = new StatusResponse();
		response.setIsSuccess(false);
		AccountDAO account = accountRepository.findById(updateBalanceRequest.getAccountNo()).orElse(null);
		if (account == null) {
			response.setMsg(ErrorCodes.ID_NOT_FOUND);
			return response;
		}
		if(account.getBalance() + updateBalanceRequest.getAmount() < 0) {
			response.setMsg(ErrorCodes.INSUFFICIENT_FUNDS);
			return response;
		}
		RoleType accRoleType = account.getUser().getAuthRole().getRoleType();
		System.out.println("auth.getPrincipal()" + auth.getName());
		UserDAO authUser = userRepository.findByUsername(auth.getPrincipal().toString());
		System.out.println("user.getAuthRole(): " + authUser.getAuthRole());
		RoleType authRoleType = authUser.getAuthRole()
				.getRoleType();
		
		// Can't update someone else's account if authUser is not employee.
		if (authRoleType != accRoleType && !Util.isEmployee(authRoleType)) {
			response.setMsg(ErrorCodes.INVALID_ACCESS);
			return response;
		}
		UserDAO workingUser = account.getUser();
		Float transactionSum = transactionRepository.dailyTransactionSum(workingUser);
		if(transactionSum == null)
			transactionSum = 0f;
		
		System.out.println("transactionSum = " + transactionSum + " for working user:" + workingUser.getUsername() + " authUser: " + authUser);
		boolean isCritical = (transactionSum + updateBalanceRequest.getAmount()) > Constants.UPDATE_BALANCE_CRITICAL_LIMIT;
		
		TransactionDAO transactionDAO = new TransactionDAO();
		transactionDAO.setCreatedBy(workingUser);
		transactionDAO.setFromAccount(account);
		transactionDAO.setIsCritical(isCritical);
		transactionDAO.setTransactionAmount(Math.abs(updateBalanceRequest.getAmount()));
		transactionDAO.setType((updateBalanceRequest.getAmount()>0)?TransactionType.CREDIT : TransactionType.DEBIT);
		if (!isApproved) {
			transactionDAO.setStatus(TransactionStatus.PENDING);
			transactionDAO.setTransactionTimestamp(Calendar.getInstance().getTime());
			transactionRepository.save(transactionDAO);
			response.setIsSuccess(true);
			response.setMsg(ErrorCodes.SUBMIT_APPROVAL);
		} else if (doUpdateBalance(updateBalanceRequest)) {
			response.setIsSuccess(true);
			response.setMsg(ErrorCodes.SUCCESS);
			return response;
		} else {
			throw new Exception("Update balance failed");
		}
		return response;
	}

	@Override
	@Transactional
	public boolean doUpdateBalance(@Valid UpdateBalanceRequest updateBalanceRequest) {
		AccountDAO account = accountRepository.findById(updateBalanceRequest.getAccountNo()).orElse(null);
		if (account == null) {
			System.out.println(updateBalanceRequest.getAccountNo() + " does not exist");
			return false;
		}
		double bal = account.getBalance();
		bal += updateBalanceRequest.getAmount();
		account.setBalance(bal);
		accountRepository.save(account);
		return true;
	}
	
	/*
	 * Only called when a Transaction is TransactionStatus.CONFIRMED or APPROVED 
	 */
	@Override
	@Transactional
	public StatusResponse submitTransactionToHyperledger(@Valid TransactionDAO transactionDAO) {
		// Insert to Hyperledger
		StatusResponse response = new StatusResponse();
		response.setIsSuccess(true);
		return response;
	}

	@Override
	public StatusResponse approveTransaction(String transactionId, Authentication auth) throws Exception {
		StatusResponse response = new StatusResponse();
		response.setIsSuccess(false);
		UserDAO user = userRepository.findByUsername(auth.getPrincipal().toString());
		System.out.println("user.getAuthRole(): " + user.getAuthRole());
		RoleType authRoleType = user.getAuthRole()
				.getRoleType();
		if(!Util.isEmployee(authRoleType)) {
			response.setMsg(ErrorCodes.INVALID_ACCESS);
			return response; 
		}
		System.out.println(Integer.parseInt(transactionId));
		TransactionDAO transaction = transactionRepository.findByTransactionId(Integer.parseInt(transactionId));
		if(transaction == null) {
			response.setMsg(ErrorCodes.ID_NOT_FOUND);
			return response; 
		}
		if(transaction.getIsCritical() && (authRoleType != RoleType.TIER2 || authRoleType != RoleType.ADMIN)) {
			response.setMsg(ErrorCodes.TIER_2_ACCESS);
			return response;
		}
		transaction.setApprovedAt(Calendar.getInstance().getTime());
		transaction.setApprovedBy(user);
		transaction.setStatus(TransactionStatus.APPROVED);
		transactionRepository.save(transaction);
		if(transaction.getType() == TransactionType.CREDIT || transaction.getType() == TransactionType.DEBIT) {
			UpdateBalanceRequest updateBalanceRequest = new UpdateBalanceRequest();
			updateBalanceRequest.setAccountNo(transaction.getFromAccount().getId());
			updateBalanceRequest.setAmount(transaction.getTransactionAmount());
			if(doUpdateBalance(updateBalanceRequest)) {
				response.setIsSuccess(true);
				response.setMsg(ErrorCodes.SUCCESS);
				submitTransactionToHyperledger(transaction);
			} else {
				throw new Exception("Update balance failed");
			}
		} else {  // TRANSFER - Create request and call transfer function internally with isApproved=true
			TransferRequest transferReq = new TransferRequest();
			transferReq.setFromAccNo(transaction.getFromAccount().getId());
			transferReq.setToAccNo(transaction.getToAccount().getId());
			transferReq.setTransferAmount(transaction.getTransactionAmount());
			response = transfer(transferReq, auth, true); 
			if(response.getIsSuccess()) {
				response.setIsSuccess(true);
				response.setMsg(ErrorCodes.SUCCESS);
				submitTransactionToHyperledger(transaction);
			} else {
				throw new Exception("Transfer failed");
			}
		}
		return response;
	}

	@Override
	@Transactional
	public StatusResponse rejectTransaction(String transactionId, Authentication auth) {
		StatusResponse response = new StatusResponse();
		response.setIsSuccess(false);
		TransactionDAO transaction = transactionRepository.findByTransactionId(Integer.parseInt(transactionId));
		UserDAO user = userRepository.findByUsername(auth.getPrincipal().toString());
		if(transaction == null) {
			response.setMsg(ErrorCodes.ID_NOT_FOUND);
			return response;
		}
		transaction.setStatus(TransactionStatus.DECLINED);
		transaction.setApprovedAt(Calendar.getInstance().getTime());
		transaction.setApprovedBy(user);
		transactionRepository.save(transaction);
		response.setIsSuccess(true);
		response.setMsg(ErrorCodes.SUCCESS);
		return response;
	}

	@Override
	public List<TransactionDAO> getTransaction(Integer type, Integer status, String userName, Boolean isCritical, Authentication auth) throws Exception {
		TransactionType tType = null;
		if(type != null) {
			switch(type) {
			case 1:
				tType = TransactionType.CREDIT;
				break;
			case 2:
				tType = TransactionType.DEBIT;
				break;
			case 3:
				tType = TransactionType.TRANSFER;
				break;
			default:
				throw new Exception("Type should be between 1,3");				
			}
		}
		TransactionStatus tStatus = null;
		if(status != null) {
			switch(status) {
			case 1:
				tStatus = TransactionStatus.APPROVED;
				break;
			case 2:
				tStatus = TransactionStatus.DECLINED;
				break;
			case 3:
				tStatus = TransactionStatus.PENDING;
				break;
			default:
				throw new Exception("Status should be between 1,3");
			}		
		}
		UserDAO authUser = userRepository.findByUsername(auth.getPrincipal().toString()), user = null;
		// For Technical Account Access where authUser != user
		if(userName != null) {
			user = userRepository.findByUsername(userName);
			if(user == null)
				throw new Exception(ErrorCodes.ID_NOT_FOUND);
			
			System.out.println("Under TAC for " + userName);
		}
		// Can't access someone else's account if authUser is not employee
		if(user != null && !Util.isEmployee(authUser.getAuthRole().getRoleType()))
			throw new Exception(ErrorCodes.INVALID_ACCESS);
		
		if(status != null && !Util.isEmployee(authUser.getAuthRole().getRoleType())) {
			throw new Exception(ErrorCodes.INVALID_ACCESS);
		}
		
		if(tStatus == TransactionStatus.PENDING && isCritical && authUser.getAuthRole().getRoleType() == RoleType.TIER1)
			throw new Exception(ErrorCodes.INVALID_ACCESS);
		// Under TAC for someone, can't see PENDING,APPROVED or COMPLETED transactions. Call without status
		if(tStatus != null && user != null && Util.isEmployee(authUser.getAuthRole().getRoleType()))
			throw new Exception(ErrorCodes.INVALID_ACCESS);
		
		// All validations done. Use only user variable from here
		if(user == null)
			user = authUser;
		List<TransactionStatus> statuses = new ArrayList<>();
		if(tStatus == null) {
			statuses.add(TransactionStatus.APPROVED);
			statuses.add(TransactionStatus.COMPLETED);
		} else {
			statuses.add(tStatus);
		}
		List<TransactionDAO> responses= null;
		if(tStatus == TransactionStatus.APPROVED || tStatus == TransactionStatus.DECLINED)
			responses = transactionRepository.findByApprovedByAndStatusIn(user, statuses);
		else if (tStatus == TransactionStatus.PENDING)
			if(!isCritical)
				responses = transactionRepository.findByStatusInAndIsCritical(statuses, isCritical);
			else {
				System.out.println("Fetching Pending critical transactions\n\n");
				responses = transactionRepository.findByStatusIn(statuses);
			}
		else {
			responses = transactionRepository.findByFromAccount_User(user);
		}
		for(TransactionDAO t : responses) {
			UserDAO createdBy = t.getCreatedBy(); 
			createdBy.setAccounts(null);
			createdBy.setAuthRole(null);
			t.setCreatedBy(createdBy);
			t.getFromAccount().setUser(null);;
			if(t.getToAccount() != null)
				t.getToAccount().setUser(null);
			if(t.getApprovedBy() != null) {
				t.getApprovedBy().setAuthRole(null);
				t.getApprovedBy().setAccounts(null);
			}
		}
		return responses;
	}

	private List<TransactionDAO> getTransactionsForToday(UserDAO user) {
		ZoneId defaultZoneId = ZoneId.systemDefault();
		Date date = Date.from(LocalDate.now().atStartOfDay(defaultZoneId).toInstant());
		return transactionRepository.findByFromAccount_UserAndTransactionTimestampGreaterThan(user, date);
	}

	@Override
	public ResponseEntity<InputStreamResource> downloadStatement(String userName, Authentication auth) throws Exception {
		UserDAO authUser = userRepository.findByUsername(auth.getPrincipal().toString());
		UserDAO workingUser = null;
		String dob = "";
		if(userName != null && !Util.isEmployee(authUser.getAuthRole().getRoleType()))
			throw new Exception(ErrorCodes.INVALID_ACCESS);
		
		if(userName != null) {
			workingUser = userRepository.findByUsername(userName);
			if(workingUser == null) {
				throw new Exception(ErrorCodes.USERNAME_NOT_FOUND);
			}
		}
		if(workingUser == null)
			workingUser = authUser;
		dob = workingUser.getDob().getDate() + "" + (workingUser.getDob().getMonth()+1) + (workingUser.getDob().getYear()%100);
		
		ByteArrayOutputStream out = null;
		Document document = null;
		InputStream in = null;
		ByteArrayOutputStream encOs = null;
		PdfStamper pdfStamper = null;
		List<TransactionDAO> transactions = getTransaction(null, null, userName, false, auth);
		try {

			document = new Document();
			String file = workingUser.getUsername().strip() + "-Bank_Statement.pdf";
			// String fPath = this.getClass().getResource("/").getPath();
			out = new ByteArrayOutputStream();
			PdfWriter.getInstance(document, out);
			document.open();
			Font font = FontFactory.getFont(FontFactory.TIMES_BOLD, 16, 1, BaseColor.BLACK);
			Chunk chunk = new Chunk("Banking Statement for " + workingUser.getName(), font);
			 
			document.add(chunk);
			document.add(new Paragraph(20, "\u00a0"));
			if(transactions == null || transactions.size() == 0) {
				font = FontFactory.getFont(FontFactory.TIMES, 16, BaseColor.BLACK);
				chunk = new Chunk("No transactions for user", font);
				document.add(chunk);
			} else {
				PdfPTable table = new PdfPTable(5);
				addTableHeader(table, transactions);
				addRows(table, transactions);
				document.add(table);
			}
			document.close();
			out.close();
			
			PdfReader pdfReader = new PdfReader(((ByteArrayOutputStream)out).toByteArray()); 
			encOs = new ByteArrayOutputStream(); 
			pdfStamper = new PdfStamper(pdfReader, encOs); 
			 
			pdfStamper.setEncryption(
					dob.getBytes(),
					"".getBytes(),
					0,
					PdfWriter.ENCRYPTION_AES_256
			);
			encOs.close();
			pdfStamper.close();
			
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.parseMediaType("application/pdf"));
			headers.add("Access-Control-Allow-Origin", "*");
			headers.add("Access-Control-Allow-Methods", "GET, POST, PUT");
			headers.add("Access-Control-Allow-Headers", "Content-Type");
			headers.add("Content-Disposition", "; filename=\"" + file + "\"");
			headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
			headers.add("Pragma", "no-cache");
			headers.add("Expires", "0");
			
			headers.setContentLength(encOs.size());
			in = new ByteArrayInputStream(encOs.toByteArray());
			ResponseEntity<InputStreamResource> response = new ResponseEntity<InputStreamResource>(
					new InputStreamResource(in), headers, HttpStatus.OK);
			return response;

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			encOs.close();
			pdfStamper.close();
			in.close();
			document.close();
			out.close();
		}
		return null;

	}
	
	private static void addTableHeader(PdfPTable table, List<TransactionDAO> transactions) {
		List<String> headers = Arrays.asList("Transaction Id", "Amount", "Time", "Type", "To Account");
		for(String header : headers) {
			PdfPCell h = new PdfPCell();
			h.setBackgroundColor(BaseColor.LIGHT_GRAY);
			h.setBorderWidth(2);
			h.setPhrase(new Phrase(header));
			table.addCell(h);
		}
    }

    private static void addRows(PdfPTable table, List<TransactionDAO> transactions) {
    	for(TransactionDAO tr : transactions) {
    		table.addCell(tr.getTransactionId().toString());
    		table.addCell(tr.getTransactionAmount().toString());
    		table.addCell(tr.getTransactionTimestamp().toString());
    		table.addCell(tr.getType().toString());
    		if(tr.getToAccount() == null)
    			table.addCell("");
    		else
    			table.addCell(tr.getToAccount().getId().toString());
    	}
    }

}