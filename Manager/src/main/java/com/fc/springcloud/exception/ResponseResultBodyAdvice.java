package com.fc.springcloud.exception;

import com.fc.springcloud.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.validation.ConstraintViolationException;
import javax.validation.ValidationException;
import java.io.FileNotFoundException;
import java.sql.SQLException;

/**
 * 通用异常处理
 *
 * @author meng
 */
@RestControllerAdvice
public class ResponseResultBodyAdvice<T> {


    @Value("${generic.exception.debug-mode:true}")
    private boolean genericExceptionDebugMode;

    private static final Logger logger = LoggerFactory.getLogger(ResponseResultBodyAdvice.class);

    /**
     * 统一异常处理
     *
     * @param e exception
     * @return
     */
    @ExceptionHandler({Exception.class})
    public ResponseEntity<Result<T>> handException(Exception e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch Exception：", e);
        }
        //未捕捉的异常导致的错误
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR.value()).body(Result.failure(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage()));
    }

    /**
     * 捕捉参数校验异常
     *
     * @param e constraintViolationException
     * @return
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<T>> constraintViolationException(ConstraintViolationException e) {
        String msg;
        if (genericExceptionDebugMode) {
            logger.error("catch ConstraintViolationException：", e);
        }
        msg = e.getMessage();
        if (msg != null) {
            int lastIndex = msg.lastIndexOf(':');
            if (lastIndex >= 0) {
                msg = msg.substring(lastIndex + 1).trim();
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(Result.failure(HttpStatus.BAD_REQUEST.value(), msg));
    }

    /**
     * 超出业务范围的异常
     *
     * @param e
     * @return
     */
    @ExceptionHandler(OutOfBusinessException.class)
    public ResponseEntity<Object> equals(OutOfBusinessException e, WebRequest request) {
        if (genericExceptionDebugMode) {
            logger.error("catch OutOfBusinessException：", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR.value()).body(Result.failure(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage()));
    }

    /**
     * 空指针异常
     *
     * @return
     */
    @ExceptionHandler({NullPointerException.class})
    public ResponseEntity<Object> handException(NullPointerException e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch NullPointerException：", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR.value()).body(Result.failure(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage()));
    }

    /**
     * 转换异常
     *
     * @param e
     * @return
     */
    @ExceptionHandler({ClassCastException.class})
    public ResponseEntity<Object> handException(ClassCastException e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch ClassCastException：", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR.value()).body(Result.failure(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage()));
    }

    /**
     * 文件未找到
     *
     * @param e
     * @return
     */
    @ExceptionHandler({FileNotFoundException.class})
    public ResponseEntity<Object> handException(FileNotFoundException e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch FileNotFoundException：", e);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND.value()).body(Result.failure(HttpStatus.NOT_FOUND.value(), e.getMessage()));
    }

    /**
     * 数据库异常
     *
     * @param e
     * @return
     */
    @ExceptionHandler({SQLException.class})
    public ResponseEntity<Object> handException(SQLException e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch SQLException：", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR.value()).body(Result.failure(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage()));
    }

    /**
     * 参数转换失败
     *
     * @param e
     * @return
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Object> handException(MethodArgumentTypeMismatchException e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch MethodArgumentTypeMismatchException：", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR.value()).body(Result.failure(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage()));
    }

    /**
     * 非法参数
     *
     * @param e
     * @return
     */
    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<Object> handException(IllegalArgumentException e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch IllegalArgumentException：", e);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(Result.failure(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
    }

    @ExceptionHandler({NotFoundException.class})
    public ResponseEntity<Object> handException(NotFoundException e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch NotFoundException：", e);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND.value()).body(Result.failure(HttpStatus.NOT_FOUND.value(), e.getMessage()));
    }

    @ExceptionHandler({EntityNotFoundException.class})
    public ResponseEntity<Object> handException(EntityNotFoundException e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch EntityNotFoundException：", e);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND.value()).body(Result.failure(HttpStatus.NOT_FOUND.value(), e.getMessage()));
    }

    @ExceptionHandler({EntityExistsException.class})
    public ResponseEntity<Object> handException(EntityExistsException e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch EntityExistsException：", e);
        }
        return ResponseEntity.status(HttpStatus.CONFLICT.value()).body(Result.failure(HttpStatus.CONFLICT.value(), e.getMessage()));
    }

    /**
     * 处理Validated校验异常
     * <p>
     * 注: 常见的ConstraintViolationException异常， 也属于ValidationException异常
     *
     * @param e 捕获到的异常
     * @return 返回给前端的data
     */
    @ResponseStatus(code = HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = {BindException.class, ValidationException.class, MethodArgumentNotValidException.class})
    public ResponseEntity handleParameterVerificationException(Exception e) {
        if (genericExceptionDebugMode) {
            logger.error("HandleParameterVerificationException has been invoked", e);
        }
        String msg = null;
        /// BindException
        if (e instanceof BindException) {
            // getFieldError获取的是第一个不合法的参数(P.S.如果有多个参数不合法的话)
            FieldError fieldError = ((BindException) e).getFieldError();
            if (fieldError != null) {
                msg = fieldError.getDefaultMessage();
            }
            /// MethodArgumentNotValidException
        } else if (e instanceof MethodArgumentNotValidException) {
            BindingResult bindingResult = ((MethodArgumentNotValidException) e).getBindingResult();
            // getFieldError获取的是第一个不合法的参数(P.S.如果有多个参数不合法的话)
            FieldError fieldError = bindingResult.getFieldError();
            if (fieldError != null) {
                msg = fieldError.getDefaultMessage();
            }
            /// ValidationException 的子类异常ConstraintViolationException
        } else if (e instanceof ConstraintViolationException) {
            msg = e.getMessage();
            if (msg != null) {
                int lastIndex = msg.lastIndexOf(':');
                if (lastIndex >= 0) {
                    msg = msg.substring(lastIndex + 1).trim();
                }
            }
        } else {
            msg = "Exception when processing parameters";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.failure(HttpStatus.BAD_REQUEST.value(), msg));
    }
}
