package script
/**
 * This exception is a multi purpose DSI exception
 **/

class DsiException extends RuntimeException {

    DsiException() {
        super()
    }

    DsiException(String errorText) {
        super(errorText)
    }

}
