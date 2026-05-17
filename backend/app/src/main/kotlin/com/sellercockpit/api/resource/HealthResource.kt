package com.sellercockpit.api.resource

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/health")
class HealthResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    fun health(): String = "UP"

    @GET
    @Path("/ready")
    @Produces(MediaType.TEXT_PLAIN)
    fun ready(): String = "READY"
}
