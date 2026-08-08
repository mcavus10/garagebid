package com.garagebid.auction.adapter.in.web;

import com.garagebid.auction.application.service.AuctionNotFoundException;
import com.garagebid.auction.application.service.CarNotFoundException;
import com.garagebid.auction.application.service.CatalogUnavailableException;
import com.garagebid.auction.domain.model.AuctionNotOpenException;
import com.garagebid.auction.domain.model.BidTooLowException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuctionNotFoundException.class)
    public ProblemDetail handleNotFound(AuctionNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
    }

    @ExceptionHandler({
            BidTooLowException.class,
            AuctionNotOpenException.class
    })
    public ProblemDetail handleBusinessRuleViolation(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidArgument(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
    }

    @ExceptionHandler(CarNotFoundException.class)
    public ProblemDetail handleCarNotFound(CarNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getMessage()
        );
    }

    @ExceptionHandler(CatalogUnavailableException.class)
    public ProblemDetail handleCatalogUnavailable(
            CatalogUnavailableException exception
    ) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getMessage()
        );
    }
}