package com.jobbootcamp.user.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;

public class SessionInvalidatedException extends BusinessException {

    public SessionInvalidatedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}