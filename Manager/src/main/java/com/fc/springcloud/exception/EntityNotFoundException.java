package com.fc.springcloud.exception;

/**
 * 
 * @author tuhua yang
 *
 */
public class EntityNotFoundException extends RuntimeException {

	public EntityNotFoundException() {
		super();
	}
	
	public EntityNotFoundException(String message) {
		super(message);
	}
	
	public EntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
	
	public EntityNotFoundException(Throwable cause) {
        super(cause);
    }
	
}
