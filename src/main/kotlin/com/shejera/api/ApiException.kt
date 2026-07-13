package com.shejera.api

open class ApiException(
    val statusCode: Int,
    message: String,
) : RuntimeException(message)

class NotFoundException(
    message: String,
) : ApiException(404, message)

class BadRequestException(
    message: String,
) : ApiException(400, message)

class ConflictException(
    message: String,
) : ApiException(409, message)
