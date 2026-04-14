package com.blessrom.transform.data;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/productos")
public class ProductoResource {

    @Inject
    SincronizadorService sincronizador;

    @GET
    @Path("/buscar")
    @Produces(MediaType.TEXT_PLAIN) // El bot prefiere texto plano para responder directo
    public String buscar(@QueryParam("q") String consulta) {
        if (consulta == null || consulta.isEmpty()) {
            return "Por favor, dime qué estás buscando.";
        }
        // Llama a tu motor de Weaviate que ya configuramos
        return sincronizador.buscar(consulta);
    }
}