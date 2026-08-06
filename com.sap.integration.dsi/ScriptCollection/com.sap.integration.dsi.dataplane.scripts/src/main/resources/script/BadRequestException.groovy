package script

/**
 * Used for exceptions that should return a 400 http status code
 */
class BadRequestException extends Exception {

    BadRequestException() {
        super()
    }

    BadRequestException(String errorText) {
        super(errorText)
    }

}
