package team9.baseball.exceptionHandler;

import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import team9.baseball.DTO.response.ApiResult;
import team9.baseball.exception.BadStatusException;
import team9.baseball.exception.NotFoundException;
import team9.baseball.exception.OauthException;
import team9.baseball.exception.UnauthorizedException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BadStatusException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult badStatusException(BadStatusException ex) {
        return ApiResult.failed(ex);
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResult notFoundException(NotFoundException ex) {
        return ApiResult.failed(ex);
    }

    @ExceptionHandler(JwtException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResult signatureException(JwtException exception) {
        return ApiResult.failed("유효한 access token 이 아닙니다.");
    }

    @ExceptionHandler({
            UnauthorizedException.class,
            OauthException.class
    })
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResult oauthException(Exception ex) {
        return ApiResult.failed(ex);
    }
}
