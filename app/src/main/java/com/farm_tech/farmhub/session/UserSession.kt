package com.farm_tech.farmhub.session

/**
 * Singleton to hold session data for the currently logged-in user.
 *
 * Assign values after login:
 *   UserSession.setSessionFromLoginResponse(loginResponse)
 */
object UserSession {
    var token: String? = null
    var userId: String? = null
    var userName: String? = null
    var phone: String? = null
    var role: String? = null
    var county: String? = null
    var subCounty: String? = null
    var paidUser: String? = null
    var issued: Long? = null
    var expires: Long? = null

    /**
     * Set the session values from LoginResponse.
     */
    fun setSessionFromLoginResponse(loginResponse: com.farm_tech.farmhub.models.login.LoginResponse) {
        token = loginResponse.token
        issued = loginResponse.issued
        expires = loginResponse.expires

        val user = loginResponse.userDetails
        userId = user?.id
        userName = user?.names
        phone = user?.phone
        role = user?.role
        county = user?.county
        subCounty = user?.subCounty
        paidUser = user?.paidUser
    }

    /**
     * Clears all session data, e.g. on logout.
     */
    fun clear() {
        token = null
        userId = null
        userName = null
        phone = null
        role = null
        county = null
        subCounty = null
        paidUser = null
        issued = null
        expires = null
    }
}
