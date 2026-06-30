package com.farm_tech.farmhub.network

sealed class ApiException(message: String) : Exception(message) {
    class BadRequest(message: String = "Bad request") : ApiException(message)
    class Unauthorized(message: String = "Unauthorized") : ApiException(message)
    class Forbidden(message: String = "Forbidden") : ApiException(message)
    class NotFound(message: String = "Not found") : ApiException(message)
    class Conflict(message: String = "Conflict") : ApiException(message)
    class Validation(message: String = "Validation failed") : ApiException(message)
    class RateLimit(message: String = "Too many requests") : ApiException(message)
    class Server(message: String = "Server error") : ApiException(message)
    class BadGateway(message: String = "Bad gateway") : ApiException(message)
    class ServiceUnavailable(message: String = "Service unavailable") : ApiException(message)
    class Network(message: String = "Network unavailable") : ApiException(message)
    class Timeout(message: String = "Request timeout") : ApiException(message)
    class Dns(message: String = "DNS failure") : ApiException(message)
    class SocketTimeout(message: String = "Socket timeout") : ApiException(message)
    class Ssl(message: String = "SSL error") : ApiException(message)
    class Unknown(message: String = "Unexpected error") : ApiException(message)
}

