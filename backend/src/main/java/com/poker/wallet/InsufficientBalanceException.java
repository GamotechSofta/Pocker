package com.poker.wallet;

import com.poker.exception.ApiException;
import com.poker.exception.ErrorCodes;
import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends ApiException {

    public InsufficientBalanceException(String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCodes.INSUFFICIENT_BALANCE, message);
    }
}
