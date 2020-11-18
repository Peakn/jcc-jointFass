package com.fc.springcloud.exception;

import com.fc.springcloud.common.CommonResult;
import com.fc.springcloud.enums.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.validation.ConstraintViolationException;
import java.io.FileNotFoundException;
import java.sql.SQLException;

/**
 * 通用异常处理
 *
 * @author meng
 */
@RestControllerAdvice
public class GenericException {

    @Value("${generic.exception.debug-mode:true}")
    private boolean genericExceptionDebugMode;

    private static final Logger logger = LoggerFactory.getLogger(GenericException.class);

    /**
     * 统一异常处理
     *
     * @return
     */
    @ExceptionHandler({Exception.class})
    public ResponseEntity<CommonResult> handException(Exception e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch Exception：[{}]", e);
        }
        //未捕捉的异常导致的错误
        return ResponseEntity.ok().body(new CommonResult(ResultCode.INTERNAL_SERVER_ERROR.getCode(), "服务器异常"));
    }

    /**
     * 捕捉参数校验异常
     *
     * @param e
     * @return
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CommonResult> equals(ConstraintViolationException e) {
        if (genericExceptionDebugMode) {
            logger.error("catch ConstraintViolationException：", e);

        }
        return ResponseEntity.ok().body(new CommonResult(ResultCode.BAD_REQUEST.getCode(), e.getMessage()));
    }

    /**
     * 超出业务范围的异常
     *
     * @param e
     * @return
     */
    @ExceptionHandler(OutOfBusinessException.class)
    public ResponseEntity<CommonResult> equals(OutOfBusinessException e) {
        if (genericExceptionDebugMode) {
            logger.error("catch OutOfBusinessException：", e);
        }
        ResponseEntity<CommonResult> body = ResponseEntity.status(e.getCode()).body(new CommonResult(e.getCode(), e.getMessage()));
        return body;
    }

    /**
     * 空指针异常
     *
     * @return
     */
    @ExceptionHandler({NullPointerException.class})
    public ResponseEntity<CommonResult> handException(NullPointerException e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch NullPointerException：", e);
        }
        return ResponseEntity.ok().body(new CommonResult(ResultCode.INTERNAL_SERVER_ERROR.getCode(), "数据异常"));
    }

    /**
     * 转换异常
     *
     * @param e
     * @return
     */
    @ExceptionHandler({ClassCastException.class})
    public ResponseEntity<CommonResult> handException(ClassCastException e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch ClassCastException：", e);
        }
        return ResponseEntity.ok().body(new CommonResult(ResultCode.INTERNAL_SERVER_ERROR.getCode(), "格式转换错误"));
    }

    /**
     * 文件未找到
     *
     * @param e
     * @return
     */
    @ExceptionHandler({FileNotFoundException.class})
    public ResponseEntity<CommonResult> handException(FileNotFoundException e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch FileNotFoundException：", e);
        }
        return ResponseEntity.ok().body(new CommonResult(HttpStatus.NOT_FOUND.value(), "文件未找到"));
    }

    /**
     * 数据库异常
     *
     * @param e
     * @return
     */
    @ExceptionHandler({SQLException.class})
    public ResponseEntity<CommonResult> handException(SQLException e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch Exception：", e);
        }
        return ResponseEntity.ok().body(new CommonResult(HttpStatus.INTERNAL_SERVER_ERROR.value(), "数据库异常"));
    }

    /**
     * 参数转换失败
     *
     * @param e
     * @return
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class})
    public ResponseEntity handException(MethodArgumentTypeMismatchException e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch MethodArgumentTypeMismatchException：", e);
        }
        return ResponseEntity.ok().body(new CommonResult(HttpStatus.INTERNAL_SERVER_ERROR.value(), "参数转换失败"));
    }

    /**
     * 非法参数
     *
     * @param e
     * @return
     */
    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<CommonResult> handException(IllegalArgumentException e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch Exception：", e);
        }
        return ResponseEntity.ok().body(new CommonResult(HttpStatus.BAD_REQUEST.value(), "非法参数"));
    }

    @ExceptionHandler({NotFoundException.class})
    public ResponseEntity<CommonResult> handException(NotFoundException e) {
        if (genericExceptionDebugMode) {
            logger.error("Catch Exception：", e);
        }
        return ResponseEntity.ok().body(new CommonResult(HttpStatus.NOT_FOUND.value(), "未找到资源"));
    }
}
